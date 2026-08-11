package com.cavetale.fastleafdecay.config;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The immutable runtime configuration of the plugin.
 * <p>
 * The delays are in ticks and are clamped to their minimum on construction, so an
 * instance always holds values that are safe to use. An empty {@code onlyInWorlds}
 * means every world.
 */
public record FastLeafDecayConfig(@NotNull WorldFilter onlyInWorlds, @NotNull WorldFilter excludeWorlds,
                                  long breakDelay, long decayDelay,
                                  boolean spawnParticles, boolean playSound) {

    /**
     * The minimum {@code BreakDelay} that guarantees proper function.
     */
    public static final long MIN_BREAK_DELAY = 5;

    /**
     * The minimum {@code DecayDelay} that guarantees proper function.
     */
    public static final long MIN_DECAY_DELAY = 1;

    public static final long DEFAULT_BREAK_DELAY = 5;
    public static final long DEFAULT_DECAY_DELAY = 2;
    public static final boolean DEFAULT_SPAWN_PARTICLES = true;
    public static final boolean DEFAULT_PLAY_SOUND = true;

    private static final FastLeafDecayConfig DEFAULT =
        new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(),
            DEFAULT_BREAK_DELAY, DEFAULT_DECAY_DELAY, DEFAULT_SPAWN_PARTICLES, DEFAULT_PLAY_SOUND);

    public static @NotNull FastLeafDecayConfig defaults() {
        return DEFAULT;
    }

    public FastLeafDecayConfig {
        Objects.requireNonNull(onlyInWorlds, "onlyInWorlds");
        Objects.requireNonNull(excludeWorlds, "excludeWorlds");
        breakDelay = Math.max(breakDelay, MIN_BREAK_DELAY);
        decayDelay = Math.max(decayDelay, MIN_DECAY_DELAY);
    }

    public boolean isEnabledIn(@NotNull World world) {
        if (!this.onlyInWorlds.isEmpty() && !this.onlyInWorlds.matches(world)) {
            return false;
        }

        return !this.excludeWorlds.matches(world);
    }
}
