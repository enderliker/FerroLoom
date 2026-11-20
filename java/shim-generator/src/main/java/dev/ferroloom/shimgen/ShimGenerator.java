package dev.ferroloom.shimgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Turns a 26.3 class's metadata into a shim class: same hierarchy, fields and method descriptors,
 * with bodies that call a hand-written implementation or throw {@code FerroUnimplementedException}.
 */
public final class ShimGenerator {
    public static final String EXCEPTION = "dev/ferroloom/shim/FerroUnimplementedException";

    public record Summary(int classes, int methods, int skipped) {}

    private ShimGenerator() {}

    /** Generates every game class in {@code clientJar} into {@code outJar}. */
    public static Summary generate(Path clientJar, Path impls, Path outJar) throws IOException {
        Map<String, byte[]> classes = new java.util.LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    classes.put(entry.getName().substring(0, entry.getName().length() - 6), in.readAllBytes());
                }
            }
        }

        Map<String, String> supers = new HashMap<>();
        for (var e : classes.entrySet()) supers.put(e.getKey(), new ClassReader(e.getValue()).getSuperName());

        ImplTables base = ImplTables.load(impls);
        Set<String> constructible = new HashSet<>();
        for (String name : classes.keySet()) {
            if (!base.isConstructible(name)) continue;
            String cursor = name;
            while (cursor != null && classes.containsKey(cursor) && constructible.add(cursor)) {
                cursor = supers.get(cursor);
            }
        }
        ImplTables tables = base.withConstructible(constructible);

        int methods = 0;
        int skipped = 0;
        try (OutputStream out = Files.newOutputStream(outJar); JarOutputStream jar = new JarOutputStream(out)) {
            for (var e : classes.entrySet()) {
                byte[] generated = generateClass(e.getValue(), tables);
                ClassNode node = new ClassNode();
                new ClassReader(generated).accept(node, ClassReader.SKIP_CODE);
                methods += node.methods.size();
                skipped += skippedIn(e.getValue());
                jar.putNextEntry(new JarEntry(e.getKey() + ".class"));
                jar.write(generated);
                jar.closeEntry();
            }
        }
        return new Summary(classes.size(), methods, skipped);
    }

    private static int skippedIn(byte[] input) {
        ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
        int n = 0;
        for (MethodNode m : node.methods) {
            if ((m.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) n++;
        }
        return n;
    }

    public static byte[] generateClass(byte[] input, ImplTables tables) {
        ClassNode in = new ClassNode();
        new ClassReader(input).accept(in, 0);

        boolean isInterface = (in.access & Opcodes.ACC_INTERFACE) != 0;
        boolean isEnum = (in.access & Opcodes.ACC_ENUM) != 0;

        ClassNode out = new ClassNode();
        out.version = in.version;
        out.access = sanitizeClass(in.access);
        out.name = in.name;
        out.superName = in.superName;
        out.interfaces = new ArrayList<>(in.interfaces);

        for (FieldNode f : in.fields) {
            if ((f.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
            int acc = Opcodes.ACC_PUBLIC | (f.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ENUM | Opcodes.ACC_TRANSIENT));
            if ((f.access & Opcodes.ACC_STATIC) != 0) acc |= (f.access & Opcodes.ACC_FINAL);
            out.fields.add(new FieldNode(acc, f.name, f.desc, null, null));
        }

        for (MethodNode m : in.methods) {
            if (m.name.equals("<clinit>")) continue;
            if ((m.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;
            if (isEnum && (m.name.equals("values") || m.name.equals("valueOf"))) continue;
            if (isEnum && m.name.equals("<init>")) continue;
            if (tables.isConstructible(in.name) && m.name.equals("<init>") && m.desc.equals("()V")) continue;
            out.methods.add(convert(in, m, tables, isInterface));
        }

        if (isEnum) addEnumSupport(out, in);
        if (tables.isConstructible(in.name) && (in.access & Opcodes.ACC_INTERFACE) == 0) {
            out.methods.add(constructor(in.name, in.superName));
        }
        return write(out);
    }

    private static int sanitizeClass(int access) {
        int kept = access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL);
        return kept | Opcodes.ACC_PUBLIC;
    }

    private static MethodNode convert(ClassNode owner, MethodNode m, ImplTables tables, boolean isInterface) {
        boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
        boolean isAbstract = (m.access & Opcodes.ACC_ABSTRACT) != 0;
        int access = Opcodes.ACC_PUBLIC | (isStatic ? Opcodes.ACC_STATIC : 0) | (isAbstract ? Opcodes.ACC_ABSTRACT : 0);
        MethodNode out = new MethodNode(access, m.name, m.desc, null, null);
        if (isAbstract) return out;

        ImplTables.Impl impl = tables.get(owner.name + "#" + m.name + m.desc);
        if (impl == null) {
            throwUnimplemented(out, owner.name, m.name);
            return out;
        }
        Type[] args = Type.getArgumentTypes(m.desc);
        Type ret = Type.getReturnType(m.desc);
        int slot = 0;
        if (m.name.equals("<init>")) {
            // A constructor must initialise `this` before anything else uses it; the delegate then
            // receives a normal instance. The tables only target classes whose super is Object.
            String superName = owner.superName == null ? "java/lang/Object" : owner.superName;
            out.visitVarInsn(Opcodes.ALOAD, 0);
            out.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        }
        if (!isStatic) {
            out.visitVarInsn(Opcodes.ALOAD, slot);
            slot++;
        }
        for (Type arg : args) {
            out.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
            slot += arg.getSize();
        }
        StringBuilder callDesc = new StringBuilder("(");
        if (!isStatic) callDesc.append('L').append(owner.name).append(';');
        for (Type arg : args) callDesc.append(arg.getDescriptor());
        callDesc.append(')').append(ret.getDescriptor());
        out.visitMethodInsn(Opcodes.INVOKESTATIC, impl.owner(), impl.method(), callDesc.toString(), false);
        out.visitInsn(returnOpcode(ret));
        return out;
    }

    private static void throwUnimplemented(MethodNode out, String ownerName, String methodName) {
        out.visitTypeInsn(Opcodes.NEW, EXCEPTION);
        out.visitInsn(Opcodes.DUP);
        out.visitLdcInsn(ownerName.replace('/', '.') + "#" + methodName);
        out.visitMethodInsn(Opcodes.INVOKESPECIAL, EXCEPTION, "<init>", "(Ljava/lang/String;)V", false);
        out.visitInsn(Opcodes.ATHROW);
    }

    private static MethodNode constructor(String name, String superName) {
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName == null ? "java/lang/Object" : superName, "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        return ctor;
    }

    private static void addEnumSupport(ClassNode out, ClassNode in) {
        List<FieldNode> constants = in.fields.stream()
                .filter(f -> (f.access & Opcodes.ACC_ENUM) != 0)
                .toList();

        MethodNode ctor = new MethodNode(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitVarInsn(Opcodes.ILOAD, 2);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
        ctor.visitInsn(Opcodes.RETURN);
        out.methods.add(ctor);

        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        for (int i = 0; i < constants.size(); i++) {
            FieldNode c = constants.get(i);
            clinit.visitTypeInsn(Opcodes.NEW, out.name);
            clinit.visitInsn(Opcodes.DUP);
            clinit.visitLdcInsn(c.name);
            clinit.visitIntInsn(Opcodes.BIPUSH, i);
            clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, out.name, "<init>", "(Ljava/lang/String;I)V", false);
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, out.name, c.name, "L" + out.name + ";");
        }
        clinit.visitInsn(Opcodes.RETURN);
        out.methods.add(clinit);

        MethodNode values = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[L" + out.name + ";", null, null);
        values.visitIntInsn(Opcodes.BIPUSH, constants.size());
        values.visitTypeInsn(Opcodes.ANEWARRAY, out.name);
        for (int i = 0; i < constants.size(); i++) {
            values.visitInsn(Opcodes.DUP);
            values.visitIntInsn(Opcodes.BIPUSH, i);
            values.visitFieldInsn(Opcodes.GETSTATIC, out.name, constants.get(i).name, "L" + out.name + ";");
            values.visitInsn(Opcodes.AASTORE);
        }
        values.visitInsn(Opcodes.ARETURN);
        out.methods.add(values);

        MethodNode valueOf = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf",
                "(Ljava/lang/String;)L" + out.name + ";", null, null);
        valueOf.visitLdcInsn(Type.getObjectType(out.name));
        valueOf.visitVarInsn(Opcodes.ALOAD, 0);
        valueOf.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        valueOf.visitTypeInsn(Opcodes.CHECKCAST, out.name);
        valueOf.visitInsn(Opcodes.ARETURN);
        out.methods.add(valueOf);
    }

    private static int returnOpcode(Type t) {
        return switch (t.getSort()) {
            case Type.VOID -> Opcodes.RETURN;
            case Type.LONG -> Opcodes.LRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            case Type.OBJECT, Type.ARRAY -> Opcodes.ARETURN;
            default -> Opcodes.IRETURN;
        };
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
