package dev.ferroloom.loader.remap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Reads a jar's class references to tell whether it is obfuscated intermediary or named. */
public final class JarNamespace {
    private JarNamespace() {}

    public static Namespace detect(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    if (referencesIntermediary(in)) return Namespace.INTERMEDIARY;
                }
            }
        }
        return Namespace.NAMED;
    }

    private static boolean referencesIntermediary(InputStream in) throws IOException {
        boolean[] found = {false};
        new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
            private void check(String internalName) {
                if (internalName != null && internalName.matches("net/minecraft/([a-z]+/)?class_\\d+")) {
                    found[0] = true;
                }
            }

            @Override public void visit(int version, int access, String name, String sig, String superName, String[] ifaces) {
                check(name);
                check(superName);
                if (ifaces != null) for (String i : ifaces) check(i);
            }

            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        check(owner);
                    }

                    @Override public void visitFieldInsn(int op, String owner, String n, String d) {
                        check(owner);
                    }

                    @Override public void visitTypeInsn(int op, String type) {
                        check(type);
                    }

                    @Override public void visitLdcInsn(Object value) {
                        if (value instanceof org.objectweb.asm.Type t && t.getSort() == org.objectweb.asm.Type.OBJECT) {
                            check(t.getInternalName());
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
