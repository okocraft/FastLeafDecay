package com.cavetale.fastleafdecay.config;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The immutable runtime configuration of the plugin.
 * <p>
 * The delays are clamped to their minimum on construction, so an instance
 * always holds values that are safe to use.
 *
 * @param onlyInWorlds   the worlds to limit the fast leaf decay to, or an empty filter for all worlds
 * @param excludeWorlds  the worlds to exclude from the fast leaf decay
 * @param breakDelay     the delay in ticks to check around a broken block
 * @param decayDelay     the delay in ticks to check around decaying leaves
 * @param spawnParticles whether to spawn particles on decay
 * @param playSound      whether to play a sound on decay
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

    /**
     * Returns the configuration that consists of the default values only.
     *
     * @return the default configuration
     */
    public static @NotNull FastLeafDecayConfig defaults() {
        return DEFAULT;
    }

    public FastLeafDecayConfig {
        Objects.requireNonNull(onlyInWorlds, "onlyInWorlds");
        Objects.requireNonNull(excludeWorlds, "excludeWorlds");
        breakDelay = Math.max(breakDelay, MIN_BREAK_DELAY);
        decayDelay = Math.max(decayDelay, MIN_DECAY_DELAY);
    }

    /**
     * Checks if the fast leaf decay is enabled in the given world.
     *
     * @param world the world to check
     * @return {@code true} if the world is enabled, otherwise {@code false}
     */
    public boolean isEnabledIn(@NotNull World world) {
        if (!this.onlyInWorlds.isEmpty() && !this.onlyInWorlds.matches(world)) {
            return false;
        }

        return !this.excludeWorlds.matches(world);
    }
}
