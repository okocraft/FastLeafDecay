package com.cavetale.fastleafdecay.config;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FastLeafDecayConfigTest {

    @Mock
    private World world;

    @BeforeEach
    void setUp() {
        when(this.world.getName()).thenReturn("world");
        when(this.world.getKey()).thenReturn(NamespacedKey.fromString("minecraft:overworld"));
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

    @ParameterizedTest
    @CsvSource({
        "0, 0, 5, 1",
        "-10, -10, 5, 1",
        "4, 1, 5, 1",
        "20, 10, 20, 10",
    })
    void delaysAreClampedToTheirMinimum(long breakDelay, long decayDelay, long expectedBreakDelay, long expectedDecayDelay) {
        var config = config(WorldFilter.empty(), WorldFilter.empty(), breakDelay, decayDelay);

        assertEquals(expectedBreakDelay, config.breakDelay());
        assertEquals(expectedDecayDelay, config.decayDelay());
    }

    @Test
    void emptyFiltersEnableEveryWorld() {
        assertTrue(FastLeafDecayConfig.defaults().isEnabledIn(this.world));
    }

    @Test
    void onlyInWorldsLimitsToListedWorlds() {
        assertTrue(config(filter("world"), WorldFilter.empty()).isEnabledIn(this.world));
        assertTrue(config(filter("minecraft:overworld"), WorldFilter.empty()).isEnabledIn(this.world));
        assertFalse(config(filter("other"), WorldFilter.empty()).isEnabledIn(this.world));
    }

    @Test
    void excludeWorldsDisablesListedWorlds() {
        assertFalse(config(WorldFilter.empty(), filter("world")).isEnabledIn(this.world));
        assertFalse(config(WorldFilter.empty(), filter("minecraft:overworld")).isEnabledIn(this.world));
        assertTrue(config(WorldFilter.empty(), filter("other")).isEnabledIn(this.world));
    }

    @Test
    void excludeWorldsTakesPrecedenceOverOnlyInWorlds() {
        assertFalse(config(filter("world"), filter("world")).isEnabledIn(this.world));
    }

    private static WorldFilter filter(String entry) {
        return WorldFilter.parse(List.of(entry)).filter();
    }

    private static FastLeafDecayConfig config(WorldFilter onlyInWorlds, WorldFilter excludeWorlds) {
        return config(onlyInWorlds, excludeWorlds, FastLeafDecayConfig.DEFAULT_BREAK_DELAY, FastLeafDecayConfig.DEFAULT_DECAY_DELAY);
    }

    private static FastLeafDecayConfig config(WorldFilter onlyInWorlds, WorldFilter excludeWorlds, long breakDelay, long decayDelay) {
        return new FastLeafDecayConfig(onlyInWorlds, excludeWorlds, breakDelay, decayDelay, true, true);
    }
}
