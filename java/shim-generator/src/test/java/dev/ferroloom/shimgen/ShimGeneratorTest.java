package dev.ferroloom.shimgen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShimGeneratorTest {
    @Test
    void a_method_without_an_impl_throws_by_name() throws Exception {
        byte[] in = Fixtures.clazz("example/Target", "java/lang/Object", null,
                Fixtures.methods(new Fixtures.Method("getFov", "(Lexample/Camera;FZ)F")), "value:I");

        byte[] out = ShimGenerator.generateClass(in, ImplTables.constructible("example/Target"));
        ClassLoader loader = Fixtures.loader(out, Fixtures.clazz("example/Camera", "java/lang/Object", null, List.of()));
        Class<?> target = loader.loadClass("example.Target");
        Object instance = target.getDeclaredConstructor().newInstance();
        Class<?> camera = loader.loadClass("example.Camera");
        Object cam = camera.getDeclaredConstructor().newInstance();

        Throwable thrown = assertThrows(Throwable.class,
                () -> target.getMethod("getFov", camera, float.class, boolean.class).invoke(instance, cam, 1.0f, false));
        assertTrue(thrown.getCause().getMessage().contains("example.Target#getFov"), thrown.getCause().getMessage());
    }

    @Test
    void an_impl_row_emits_a_static_call_to_shim_impl() throws Exception {
        byte[] in = Fixtures.clazz("example/Target", "java/lang/Object", null,
                Fixtures.methods(new Fixtures.Method("getFov", "(Lexample/Camera;FZ)F")));
        ImplTables tables = ImplTables.of(Map.of(
                "example/Target#getFov(Lexample/Camera;FZ)F",
                new ImplTables.Impl("example/Calls", "fov")));

        byte[] out = ShimGenerator.generateClass(in, tables.withConstructible(java.util.Set.of("example/Target")));
        byte[] camera = Fixtures.clazz("example/Camera", "java/lang/Object", null, List.of());
        byte[] calls = Fixtures.calls("example/Calls", "example/Target", "Lexample/Target;");
        ClassLoader loader = Fixtures.loader(out, camera, calls);
        Object target = loader.loadClass("example.Target").getDeclaredConstructor().newInstance();
        Object cam = loader.loadClass("example.Camera").getDeclaredConstructor().newInstance();

        Object result = loader.loadClass("example.Target")
                .getMethod("getFov", loader.loadClass("example.Camera"), float.class, boolean.class)
                .invoke(target, cam, 1.0f, false);
        assertEquals(2.0f, result);
    }

    @Test
    void a_constructor_with_an_impl_row_calls_super_then_the_delegate() throws Exception {
        byte[] in = Fixtures.clazz("example/Target", "java/lang/Object", null,
                Fixtures.methods(new Fixtures.Method("<init>", "(I)V")));
        ImplTables tables = ImplTables.of(Map.of(
                "example/Target#<init>(I)V", new ImplTables.Impl("example/Calls", "ctor")));

        byte[] out = ShimGenerator.generateClass(in, tables);
        ClassLoader loader = Fixtures.loader(out, Fixtures.ctorCalls("example/Calls", "example/Target"));
        Object target = loader.loadClass("example.Target").getDeclaredConstructor(int.class).newInstance(7);

        assertNotNull(target);
        assertEquals(7, loader.loadClass("example.Calls").getField("last").getInt(null));
    }

    @Test
    void fields_hierarchy_and_constructibility_are_kept() throws Exception {
        byte[] base = Fixtures.clazz("example/Base", "java/lang/Object", null, List.of());
        byte[] face = Fixtures.interfaceClazz("example/I", List.of());
        byte[] in = Fixtures.clazz("example/Target", "example/Base", new String[] {"example/I"},
                Fixtures.methods(new Fixtures.Method("run", "()V")), "x:I");
        ImplTables tables = ImplTables.constructible("example/Target", "example/Base");

        byte[] out = ShimGenerator.generateClass(in, tables);
        ClassLoader loader = Fixtures.loader(out, base, face, new byte[0]);
        Class<?> target = loader.loadClass("example.Target");
        assertEquals("example.Base", target.getSuperclass().getName());
        assertEquals("example.I", target.getInterfaces()[0].getName());
        assertNotNull(target.getField("x"));
        assertNotNull(target.getDeclaredConstructor().newInstance());
    }

    @Test
    void an_enum_keeps_its_constants_in_order() throws Exception {
        byte[] out = ShimGenerator.generateClass(Fixtures.enumClass("example/Kind"), ImplTables.empty());
        Class<?> kind = Fixtures.loader(out).loadClass("example.Kind");
        Object[] values = (Object[]) kind.getMethod("values").invoke(null);
        assertEquals(2, values.length);
        assertEquals("A", ((Enum<?>) values[0]).name());
        assertEquals(1, ((Enum<?>) values[1]).ordinal());
        assertEquals(kind, kind.getMethod("valueOf", String.class).invoke(null, "B").getClass());
    }

    @Test
    void impl_tables_are_read_from_json() throws Exception {
        java.nio.file.Path impls = java.nio.file.Files.createTempDirectory("fl-impl");
        java.nio.file.Files.writeString(impls.resolve("client.json"), """
                { "constructible": ["example/A"],
                  "methods": {
                    "example/A#run()V": {"impl": "example/Calls.run"},
                    "example/A#fov(F)F": {"impl": "example/Calls.fov"}
                  } }
                """);
        ImplTables tables = ImplTables.load(impls);
        assertTrue(tables.isConstructible("example/A"));
        assertEquals("example/Calls", tables.get("example/A#run()V").owner());
        assertEquals("fov", tables.get("example/A#fov(F)F").method());
    }

    @Test
    void generate_writes_a_jar_and_follows_the_superclass_chain_for_constructibility() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("fl-gen");
        java.nio.file.Path jar = dir.resolve("in.jar");
        try (var out = java.nio.file.Files.newOutputStream(jar); var zip = new java.util.jar.JarOutputStream(out)) {
            java.util.Map<String, byte[]> classes = java.util.Map.of(
                    "example/A", Fixtures.clazz("example/A", "example/B", null, List.of()),
                    "example/B", Fixtures.clazz("example/B", "java/lang/Object", null, List.of()));
            for (var e : classes.entrySet()) {
                zip.putNextEntry(new java.util.jar.JarEntry(e.getKey() + ".class"));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        java.nio.file.Path impls = java.nio.file.Files.createDirectories(dir.resolve("impl"));
        java.nio.file.Files.writeString(impls.resolve("t.json"),
                "{\"constructible\":[\"example/A\"],\"methods\":{}}");

        ShimGenerator.Summary summary = ShimGenerator.generate(jar, impls, dir.resolve("out.jar"));

        assertEquals(2, summary.classes());
        ClassLoader loader = Fixtures.jarLoader(dir.resolve("out.jar"));
        Object a = loader.loadClass("example.A").getDeclaredConstructor().newInstance();
        Object b = loader.loadClass("example.B").getDeclaredConstructor().newInstance();
        assertNotNull(a);
        assertNotNull(b);
    }

    @Test
    void an_interface_gets_default_bodies_that_throw() throws Exception {
        byte[] in = Fixtures.interfaceClazz("example/I",
                Fixtures.methods(new Fixtures.Method("run", "()V")));
        byte[] out = ShimGenerator.generateClass(in, ImplTables.empty());
        Class<?> i = Fixtures.loader(out).loadClass("example.I");
        assertTrue(i.isInterface());
        Method run = i.getMethod("run");
        assertTrue(run.isDefault());
        Class<?> impl = Fixtures.loader(out, Fixtures.implementor("example/Impl", "example/I")).loadClass("example.Impl");
        Object instance = impl.getDeclaredConstructor().newInstance();
        Throwable t = assertThrows(Throwable.class, () -> impl.getMethod("run").invoke(instance));
        assertTrue(t.getCause().getMessage().contains("example.I#run"), t.getCause().getMessage());
    }
}
