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
