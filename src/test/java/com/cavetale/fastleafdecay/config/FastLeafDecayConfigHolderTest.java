package com.cavetale.fastleafdecay.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastLeafDecayConfigHolderTest {

    private static FastLeafDecayConfig config(long breakDelay) {
        return new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(), breakDelay,
            FastLeafDecayConfig.DEFAULT_DECAY_DELAY, true, true);
    }

    @Test
    void holdsTheConfigurationItWasCreatedWith() {
        var config = config(10);

        assertSame(config, new FastLeafDecayConfigHolder(config).get());
    }

    @Test
    void returnsTheReplacedConfigurationAfterSet() {
        var holder = new FastLeafDecayConfigHolder(config(10));
        var reloaded = config(20);

        holder.set(reloaded);

        assertSame(reloaded, holder.get());
        assertEquals(20, holder.get().breakDelay());
    }

    @Test
    void rejectsNullAtConstruction() {
        assertThrows(NullPointerException.class, () -> new FastLeafDecayConfigHolder(null));
    }

    @Test
    void rejectsNullAtSet() {
        var holder = new FastLeafDecayConfigHolder(FastLeafDecayConfig.defaults());

        assertThrows(NullPointerException.class, () -> holder.set(null));
        assertSame(FastLeafDecayConfig.defaults(), holder.get());
    }
}
