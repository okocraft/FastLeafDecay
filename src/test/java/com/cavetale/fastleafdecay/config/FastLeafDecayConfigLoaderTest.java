package com.cavetale.fastleafdecay.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastLeafDecayConfigLoaderTest {

    private static WorldFilter filter(String... entries) {
        return WorldFilter.parse(List.of(entries)).filter();
    }

    private static ConfigurationNode node(String yaml) {
        try {
            return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .build()
                .load();
        } catch (ConfigurateException e) {
            throw new AssertionError("Could not parse the test YAML.", e);
        }
    }

    /**
     * Tests of the conversion from a node to a configuration, without touching the file system.
     */
    @Nested
    class FromNode {

        static Stream<Arguments> validConfigurations() {
            return Stream.of(
                Arguments.of("every option", """
                        OnlyInWorlds:
                          - world
                          - minecraft:the_nether
                        ExcludeWorlds:
                          - other
                        BreakDelay: 10
                        DecayDelay: 4
                        SpawnParticles: false
                        PlaySound: false
                        """,
                    new FastLeafDecayConfig(filter("world", "minecraft:the_nether"), filter("other"), 10, 4, false, false)),
                Arguments.of("no option", "# no options at all\n", FastLeafDecayConfig.defaults()),
                Arguments.of("empty world lists", """
                        OnlyInWorlds: []
                        ExcludeWorlds: []
                        """,
                    FastLeafDecayConfig.defaults()),
                Arguments.of("a single option", "BreakDelay: 8\n",
                    new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(), 8,
                        FastLeafDecayConfig.DEFAULT_DECAY_DELAY, FastLeafDecayConfig.DEFAULT_SPAWN_PARTICLES, FastLeafDecayConfig.DEFAULT_PLAY_SOUND)),
                Arguments.of("legacy world names", """
                        OnlyInWorlds:
                          - world
                        ExcludeWorlds:
                          - world_nether
                        """,
                    new FastLeafDecayConfig(filter("world"), filter("world_nether"),
                        FastLeafDecayConfig.DEFAULT_BREAK_DELAY, FastLeafDecayConfig.DEFAULT_DECAY_DELAY, true, true)),
                Arguments.of("namespaced world keys", """
                        OnlyInWorlds:
                          - minecraft:overworld
                          - custom:resource_world
                        """,
                    new FastLeafDecayConfig(filter("minecraft:overworld", "custom:resource_world"), WorldFilter.empty(),
                        FastLeafDecayConfig.DEFAULT_BREAK_DELAY, FastLeafDecayConfig.DEFAULT_DECAY_DELAY, true, true)),
                Arguments.of("delays at their minimum", """
                        BreakDelay: 5
                        DecayDelay: 1
                        """,
                    new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(),
                        FastLeafDecayConfig.MIN_BREAK_DELAY, FastLeafDecayConfig.MIN_DECAY_DELAY, true, true)),
                Arguments.of("scalars written as strings", """
                        BreakDelay: '10'
                        SpawnParticles: 'false'
                        """,
                    new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(), 10,
                        FastLeafDecayConfig.DEFAULT_DECAY_DELAY, false, FastLeafDecayConfig.DEFAULT_PLAY_SOUND))
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validConfigurations")
        void readsValidConfiguration(String name, String yaml, FastLeafDecayConfig expected) {
            var result = load(yaml);

            assertEquals(expected, result.config());
            assertTrue(result.warnings().isEmpty());
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

            assertEquals(WorldFilter.empty(), result.config().onlyInWorlds());
            assertEquals(filter("other"), result.config().excludeWorlds());
            assertEquals(10, result.config().breakDelay());
            assertEquals(1, result.warnings().size());
        }

        @Test
        void invalidWorldEntriesAreIgnoredWithWarning() {
            var result = load("""
                OnlyInWorlds:
                  - world
                  - minecraft:the_nether
                  - Invalid:Key
                  - custom:re source
                ExcludeWorlds:
                  - other
                """);

            assertEquals(filter("world", "minecraft:the_nether"), result.config().onlyInWorlds());
            assertEquals(filter("other"), result.config().excludeWorlds());
            assertEquals(2, result.warnings().size());
            assertTrue(result.warnings().stream().allMatch(warning -> warning.contains("OnlyInWorlds")));
        }

        private ConfigLoadResult load(String yaml) {
            return FastLeafDecayConfigLoader.load(node(yaml));
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

            assertEquals(filter("world"), result.config().onlyInWorlds());
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
