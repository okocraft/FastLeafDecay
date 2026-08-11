package com.cavetale.fastleafdecay.config;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FastLeafDecayConfigTest {

    private static WorldFilter filter(String entries) {
        return entries == null ? WorldFilter.empty() : WorldFilter.parse(List.of(entries.split(","))).filter();
    }

    private static World world(String name, String key) {
        var world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getKey()).thenReturn(NamespacedKey.fromString(key));
        return world;
    }

    @Test
    void defaultsHoldTheDocumentedValues() {
        var config = FastLeafDecayConfig.defaults();

        assertTrue(config.onlyInWorlds().isEmpty());
        assertTrue(config.excludeWorlds().isEmpty());
        assertEquals(FastLeafDecayConfig.DEFAULT_BREAK_DELAY, config.breakDelay());
        assertEquals(FastLeafDecayConfig.DEFAULT_DECAY_DELAY, config.decayDelay());
        assertTrue(config.spawnParticles());
        assertTrue(config.playSound());
    }

    /**
     * Tests of the invariants enforced on construction.
     */
    @Nested
    class Delays {

        @ParameterizedTest(name = "{0}/{1} -> {2}/{3}")
        @CsvSource({
            "0, 0, 5, 1",
            "-10, -10, 5, 1",
            "4, 1, 5, 1",
            "5, 2, 5, 2",
            "20, 10, 20, 10",
        })
        void areClampedToTheirMinimum(long breakDelay, long decayDelay, long expectedBreakDelay, long expectedDecayDelay) {
            var config = new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(), breakDelay, decayDelay, true, true);

            assertEquals(expectedBreakDelay, config.breakDelay());
            assertEquals(expectedDecayDelay, config.decayDelay());
        }
    }

    /**
     * Tests of {@link FastLeafDecayConfig#isEnabledIn(World)}.
     */
    @Nested
    class IsEnabledIn {

        @ParameterizedTest(name = "only: [{0}], exclude: [{1}], world: {2} ({3}) -> {4}")
        @CsvSource(delimiter = '|', value = {
            // an empty OnlyInWorlds enables every world
            "                     |                     | world | minecraft:overworld | true",
            // OnlyInWorlds limits the enabled worlds
            "world                |                     | world | minecraft:overworld | true",
            "minecraft:overworld  |                     | world | minecraft:overworld | true",
            "other                |                     | world | minecraft:overworld | false",
            "other,world          |                     | world | minecraft:overworld | true",
            // ExcludeWorlds disables the listed worlds
            "                     | world               | world | minecraft:overworld | false",
            "                     | minecraft:overworld | world | minecraft:overworld | false",
            "                     | other               | world | minecraft:overworld | true",
            // ExcludeWorlds takes precedence over OnlyInWorlds
            "world                | world               | world | minecraft:overworld | false",
            "world                | minecraft:overworld | world | minecraft:overworld | false",
        })
        void reflectsBothWorldFilters(String onlyInWorlds, String excludeWorlds, String worldName, String worldKey, boolean expected) {
            var config = new FastLeafDecayConfig(filter(onlyInWorlds), filter(excludeWorlds),
                FastLeafDecayConfig.DEFAULT_BREAK_DELAY, FastLeafDecayConfig.DEFAULT_DECAY_DELAY, true, true);

            assertEquals(expected, config.isEnabledIn(world(worldName, worldKey)));
        }
    }
}
