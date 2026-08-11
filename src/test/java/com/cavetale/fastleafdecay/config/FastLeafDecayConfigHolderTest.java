package com.cavetale.fastleafdecay.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastLeafDecayConfigHolderTest {

    @Test
    void holdsTheConfigurationItWasCreatedWith() {
        var config = FastLeafDecayConfig.defaults();

        assertSame(config, new FastLeafDecayConfigHolder(config).get());
    }

    @Test
    void returnsTheReplacedConfigurationAfterSet() {
        var holder = new FastLeafDecayConfigHolder(FastLeafDecayConfig.defaults());
        var reloaded = new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(), 20, 10, false, false);

        holder.set(reloaded);

        assertSame(reloaded, holder.get());
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
