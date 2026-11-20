package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod;
import org.junit.jupiter.api.Test;

class HorizonGameProviderTest {
    @Test
    void a_configured_api_jar_becomes_the_fabric_api_builtin() throws Exception {
        Path tmp = Files.createTempDirectory("fl-provider");
        Path api = Files.writeString(tmp.resolve("ferrofabric-api.jar"), "jar");
        System.setProperty("ferro.apiJar", api.toString());
        System.setProperty("ferro.shimDir", tmp.toString());
        try {
            Collection<BuiltinMod> mods = new HorizonGameProvider().getBuiltinMods();
            assertTrue(mods.stream().anyMatch(mod -> mod.metadata.getId().equals("minecraft")));
            BuiltinMod fabricApi = mods.stream()
                    .filter(mod -> mod.metadata.getId().equals("fabric-api"))
                    .findFirst().orElseThrow();
            assertEquals("26.3-ferro.1", fabricApi.metadata.getVersion().getFriendlyString());
            assertEquals(List.of(api), fabricApi.paths);
        } finally {
            System.clearProperty("ferro.apiJar");
        }
    }

    @Test
    void no_fabric_api_builtin_without_a_configured_jar() throws Exception {
        System.clearProperty("ferro.apiJar");
        System.setProperty("ferro.shimDir",
                Files.createTempDirectory("fl-provider-none").toString());

        Collection<BuiltinMod> mods = new HorizonGameProvider().getBuiltinMods();

        assertFalse(mods.stream().anyMatch(mod -> mod.metadata.getId().equals("fabric-api")));
    }
}