package com.cavetale.fastleafdecay.config;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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

    @Mock
    private World world;

    @BeforeEach
    void setUp() {
        when(this.world.getName()).thenReturn("world");
        when(this.world.getKey()).thenReturn(NamespacedKey.fromString("minecraft:overworld"));
    }

    /**
     * Tests of the conversion from a node to a configuration, without touching the file system.
     */
    @Nested
    class FromNode {

        @Test
        void readsEveryOption() {
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

            assertTrue(config.onlyInWorlds().matches(world));
            assertFalse(config.excludeWorlds().matches(world));
            assertEquals(10, config.breakDelay());
            assertEquals(4, config.decayDelay());
            assertFalse(config.spawnParticles());
            assertFalse(config.playSound());
            assertTrue(result.warnings().isEmpty());
        }

        @Test
        void missingValuesFallBackToDefaults() {
            var result = load("# no options at all\n");

            assertEquals(FastLeafDecayConfig.defaults(), result.config());
            assertTrue(result.warnings().isEmpty());
        }

        @Test
        void emptyWorldListsDoNotFilterAnyWorld() {
            var config = load("""
                OnlyInWorlds: []
                ExcludeWorlds: []
                """).config();

            assertTrue(config.onlyInWorlds().isEmpty());
            assertTrue(config.excludeWorlds().isEmpty());
            assertTrue(config.isEnabledIn(world));
        }

        @Test
        void malformedDelayFallsBackToDefaultWithWarning() {
            var result = load("""
                BreakDelay: not-a-number
                DecayDelay: 4
                """);

            assertEquals(FastLeafDecayConfig.DEFAULT_BREAK_DELAY, result.config().breakDelay());
            assertEquals(4, result.config().decayDelay());
            assertEquals(1, result.warnings().size());
        }

        @Test
        void tooSmallDelayIsClampedWithWarning() {
            var result = load("""
                BreakDelay: 1
                DecayDelay: 0
                """);

            assertEquals(FastLeafDecayConfig.MIN_BREAK_DELAY, result.config().breakDelay());
            assertEquals(FastLeafDecayConfig.MIN_DECAY_DELAY, result.config().decayDelay());
            assertEquals(2, result.warnings().size());
        }

        @Test
        void malformedBooleanFallsBackToDefaultWithWarning() {
            var result = load("""
                SpawnParticles: maybe
                PlaySound: false
                """);

            assertTrue(result.config().spawnParticles());
            assertFalse(result.config().playSound());
            assertEquals(1, result.warnings().size());
        }

        @Test
        void malformedWorldListFallsBackToEmptyFilterWithWarning() {
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
        void invalidWorldKeyIsIgnoredWithWarning() {
            var result = load("""
                OnlyInWorlds:
                  - world
                  - Invalid:Key
                """);

            assertTrue(result.config().onlyInWorlds().matches(world));
            assertEquals(1, result.warnings().size());
            assertTrue(result.warnings().getFirst().contains("OnlyInWorlds"));
        }

        private ConfigLoadResult load(String yaml) {
            return FastLeafDecayConfigLoader.load(node(yaml));
        }

        private ConfigurationNode node(String yaml) {
            try {
                return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build()
                    .load();
            } catch (ConfigurateException e) {
                throw new AssertionError("Could not parse the test YAML.", e);
            }
        }
    }

    /**
     * Tests of reading the configuration from a file.
     */
    @Nested
    class FromFile {

        @TempDir
        private Path directory;

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
        void loadsEditedConfigurationFile() throws IOException, ConfigurateException {
            var path = this.directory.resolve("config.yml");
            Files.writeString(path, """
                OnlyInWorlds:
                  - world
                BreakDelay: 10
                """);

            var result = FastLeafDecayConfigLoader.load(path);

            assertTrue(result.config().onlyInWorlds().matches(world));
            assertEquals(10, result.config().breakDelay());
            assertTrue(result.warnings().isEmpty());
        }

        @Test
        void brokenYamlThrowsConfigurateException() throws IOException {
            var path = this.directory.resolve("config.yml");
            Files.writeString(path, "OnlyInWorlds: [\n");

            assertThrows(ConfigurateException.class, () -> FastLeafDecayConfigLoader.load(path));
        }

        @Test
        void missingFileFallsBackToDefaults() throws ConfigurateException {
            var result = FastLeafDecayConfigLoader.load(this.directory.resolve("missing.yml"));

            assertEquals(FastLeafDecayConfig.defaults(), result.config());
            assertTrue(result.warnings().isEmpty());
        }
    }
}
