package dev.ferroloom.shimgen;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Builds the class files the generator tests turn into shim classes. */
final class Fixtures {
    private Fixtures() {}

    record Method(String name, String desc, int access) {
        Method(String name, String desc) { this(name, desc, Opcodes.ACC_PUBLIC); }
    }

    static byte[] clazz(String name, String superName, String[] interfaces, List<Method> methods, String... fields) {
        return clazz(Opcodes.ACC_PUBLIC, name, superName, interfaces, methods, fields);
    }

    static byte[] interfaceClazz(String name, List<Method> methods) {
        return clazz(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, name,
                "java/lang/Object", null, methods);
    }

    /** A concrete class that implements {@code iface} by calling its only method. */
    static byte[] implementor(String name, String iface) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", new String[] {iface});
        org.objectweb.asm.MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static byte[] clazz(int access, String name, String superName, String[] interfaces, List<Method> methods, String... fields) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V25, access, name, null, superName, interfaces);
        for (String f : fields) {
            String[] parts = f.split(":", 2);
            cw.visitField(Opcodes.ACC_PRIVATE, parts[0], parts[1], null, null).visitEnd();
        }
        boolean hasCtor = methods.stream().anyMatch(x -> x.name().equals("<init>"));
        boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        List<Method> all = new ArrayList<>(methods);
        if (!hasCtor && !isInterface) all.add(new Method("<init>", "()V"));
        for (Method m : all) {
            int methodAccess = m.access();
            boolean abstractMethod = abstractLike(methodAccess);
            MethodVisitor mv = cw.visitMethod(methodAccess, m.name(), m.desc(), null, null);
            if (!abstractMethod) {
                mv.visitCode();
                Type ret = Type.getReturnType(m.desc());
                if (m.name().equals("<init>")) {
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                } else if (ret.getSort() == Type.VOID) {
                    mv.visitInsn(Opcodes.RETURN);
                } else {
                    mv.visitInsn(defaultReturn(ret));
                }
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static boolean abstractLike(int access) {
        return (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    private static int defaultReturn(Type t) {
        return switch (t.getSort()) {
            case Type.LONG -> Opcodes.LRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            case Type.OBJECT, Type.ARRAY -> Opcodes.ARETURN;
            default -> Opcodes.IRETURN;
        };
    }

    static byte[] enumClass(String name) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, name, null, "java/lang/Enum", null);
        for (String c : List.of("A", "B")) {
            cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, c,
                    "L" + name + ";", null, null).visitEnd();
        }
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitVarInsn(Opcodes.ILOAD, 2);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** A class holding one static method: {@code fov(Target,Camera,FZ)F -> 2.0f}. */
    static byte[] calls(String name, String target, String receiverDesc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fov",
                "(" + receiverDesc + "Lexample/Camera;FZ)F", null, null);
        mv.visitCode();
        mv.visitLdcInsn(2.0f);
        mv.visitInsn(Opcodes.FRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static ClassLoader loader(byte[]... classes) {
        return new ClassLoader(Fixtures.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                for (byte[] b : classes) {
                    if (className(b).equals(name)) {
                        return defineClass(name, b, 0, b.length);
                    }
                }
                throw new ClassNotFoundException(name);
            }
        };
    }

    static String className(byte[] bytes) {
        return new org.objectweb.asm.ClassReader(bytes).getClassName().replace('/', '.');
    }

    static List<Method> methods(Method... ms) {
        return new ArrayList<>(List.of(ms));
    }
}
