package com.cavetale.fastleafdecay.config;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.spongepowered.configurate.ConfigurateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FastLeafDecayConfigLoaderTest {

    @TempDir
    private Path directory;

    @Mock
    private World world;

    @BeforeEach
    void setUp() {
        when(this.world.getName()).thenReturn("world");
        when(this.world.getKey()).thenReturn(NamespacedKey.fromString("minecraft:overworld"));
    }

    @Test
    void loadsValidConfiguration() throws IOException {
        var result = load("""
            OnlyInWorlds:
              - world
              - minecraft:the_nether
            ExcludeWorlds:
              - other
            BreakDelay: 10
            DecayDelay: 4
            SpawnParticles: false
            PlaySound: false
            """);
        var config = result.config();

        assertTrue(config.onlyInWorlds().matches(this.world));
        assertFalse(config.excludeWorlds().matches(this.world));
        assertEquals(10, config.breakDelay());
        assertEquals(4, config.decayDelay());
        assertFalse(config.spawnParticles());
        assertFalse(config.playSound());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void loadsDefaultConfigurationFile() throws IOException, ConfigurateException {
        var path = this.directory.resolve("config.yml");

        try (var input = FastLeafDecayConfigLoaderTest.class.getResourceAsStream("/config.yml")) {
            Files.copy(input, path);
        }

        var result = FastLeafDecayConfigLoader.load(path);

        assertEquals(FastLeafDecayConfig.defaults(), result.config());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void missingValuesFallBackToDefaults() throws IOException {
        var result = load("# no options at all\n");

        assertEquals(FastLeafDecayConfig.defaults(), result.config());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void emptyWorldListsDoNotFilterAnyWorld() throws IOException {
        var config = load("""
            OnlyInWorlds: []
            ExcludeWorlds: []
            """).config();

        assertTrue(config.onlyInWorlds().isEmpty());
        assertTrue(config.excludeWorlds().isEmpty());
        assertTrue(config.isEnabledIn(this.world));
    }

    @Test
    void malformedDelayFallsBackToDefaultWithWarning() throws IOException {
        var result = load("""
            BreakDelay: not-a-number
            DecayDelay: 4
            """);

        assertEquals(FastLeafDecayConfig.DEFAULT_BREAK_DELAY, result.config().breakDelay());
        assertEquals(4, result.config().decayDelay());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void tooSmallDelayIsClampedWithWarning() throws IOException {
        var result = load("""
            BreakDelay: 1
            DecayDelay: 0
            """);

        assertEquals(FastLeafDecayConfig.MIN_BREAK_DELAY, result.config().breakDelay());
        assertEquals(FastLeafDecayConfig.MIN_DECAY_DELAY, result.config().decayDelay());
        assertEquals(2, result.warnings().size());
    }

    @Test
    void malformedBooleanFallsBackToDefaultWithWarning() throws IOException {
        var result = load("""
            SpawnParticles: maybe
            PlaySound: false
            """);

        assertTrue(result.config().spawnParticles());
        assertFalse(result.config().playSound());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void malformedWorldListFallsBackToEmptyFilterWithWarning() throws IOException {
        var result = load("""
            OnlyInWorlds:
              nested: value
            ExcludeWorlds:
              - other
            BreakDelay: 10
            """);

        assertTrue(result.config().onlyInWorlds().isEmpty());
        assertFalse(result.config().excludeWorlds().isEmpty());
        assertEquals(10, result.config().breakDelay());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void invalidWorldKeyIsIgnoredWithWarning() throws IOException {
        var result = load("""
            OnlyInWorlds:
              - world
              - Invalid:Key
            """);

        assertTrue(result.config().onlyInWorlds().matches(this.world));
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().getFirst().contains("OnlyInWorlds"));
    }

    @Test
    void brokenYamlThrowsConfigurateException() throws IOException {
        var path = this.directory.resolve("config.yml");
        Files.writeString(path, "OnlyInWorlds: [\n");

        assertThrows(ConfigurateException.class, () -> FastLeafDecayConfigLoader.load(path));
    }

    private ConfigLoadResult load(String yaml) throws IOException {
        var path = this.directory.resolve("config.yml");
        Files.writeString(path, yaml);

        try {
            return FastLeafDecayConfigLoader.load(path);
        } catch (ConfigurateException e) {
            throw new AssertionError("Could not load the configuration.", e);
        }
    }
}
