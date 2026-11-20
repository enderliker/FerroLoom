package dev.ferroloom.loader.remap;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Jars built in tests: one class whose constant pool references {@code reference}. */
final class JarFixtures {
    private JarFixtures() {}

    static Path jar(Path dir, String name, String reference) throws IOException {
        Path jar = dir.resolve(name + ".jar");
        byte[] cls = clazz("example/" + name, reference);
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zip = new JarOutputStream(out)) {
            zip.putNextEntry(new JarEntry("fabric.mod.json"));
            zip.write(("{\"schemaVersion\":1,\"id\":\"" + name + "\",\"version\":\"1.0.0\"}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new JarEntry("example/" + name + ".class"));
            zip.write(cls);
            zip.closeEntry();
        }
        return jar;
    }

    /** A jar whose only Minecraft reference is inside a method descriptor. */
    static Path descriptorJar(Path dir, String name, String reference) throws IOException {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "example/" + name, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "take", "(L" + reference + ";)V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 2);
        mv.visitEnd();
        cw.visitEnd();
        return write(dir, name, cw.toByteArray());
    }

    /** A jar whose only Minecraft reference is a string in an annotation. */
    static Path annotationJar(Path dir, String name, String reference) throws IOException {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "example/" + name, null, "java/lang/Object", null);
        var annotation = cw.visitAnnotation("Lexample/Target;", true);
        annotation.visit("value", reference);
        annotation.visitEnd();
        cw.visitEnd();
        return write(dir, name, cw.toByteArray());
    }

    private static Path write(Path dir, String name, byte[] cls) throws IOException {
        Path jar = dir.resolve(name + ".jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zip = new JarOutputStream(out)) {
            zip.putNextEntry(new JarEntry("fabric.mod.json"));
            zip.write(("{\"schemaVersion\":1,\"id\":\"" + name + "\",\"version\":\"1.0.0\"}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new JarEntry("example/" + name + ".class"));
            zip.write(cls);
            zip.closeEntry();
        }
        return jar;
    }

    static Path plainJar(Path dir, String name) throws IOException {
        Path jar = dir.resolve(name + ".jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zip = new JarOutputStream(out)) {
            zip.putNextEntry(new JarEntry("example/readme.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    private static byte[] clazz(String name, String reference) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "touch", "()Ljava/lang/Class;", null, null);
        mv.visitCode();
        mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType(reference));
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
