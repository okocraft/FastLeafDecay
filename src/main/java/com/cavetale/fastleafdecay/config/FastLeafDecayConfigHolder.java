package com.cavetale.fastleafdecay.config;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A holder of the current configuration that is shared by the runtime components.
 * <p>
 * The held configuration is replaced as a whole, so a caller that reads it once at
 * the beginning of an operation always works on a consistent snapshot, even if the
 * configuration is reloaded while the operation is running.
 */
public final class FastLeafDecayConfigHolder {

    private volatile FastLeafDecayConfig config;

    public FastLeafDecayConfigHolder(@NotNull FastLeafDecayConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public @NotNull FastLeafDecayConfig get() {
        return this.config;
    }

    public void set(@NotNull FastLeafDecayConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }
}
