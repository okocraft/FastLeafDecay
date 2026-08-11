package com.cavetale.fastleafdecay.config;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A loader that reads {@code config.yml} and creates a {@link FastLeafDecayConfig}.
 * <p>
 * A value that cannot be read or parsed is replaced with its default and reported
 * as a warning message, so a malformed option never invalidates the other options.
 */
public final class FastLeafDecayConfigLoader {

    private static final String ONLY_IN_WORLDS = "OnlyInWorlds";
    private static final String EXCLUDE_WORLDS = "ExcludeWorlds";
    private static final String BREAK_DELAY = "BreakDelay";
    private static final String DECAY_DELAY = "DecayDelay";
    private static final String SPAWN_PARTICLES = "SpawnParticles";
    private static final String PLAY_SOUND = "PlaySound";

    /**
     * Loads the configuration from the given file.
     *
     * @param filepath the path to {@code config.yml}
     * @return the loaded configuration and the warnings the caller should report
     * @throws ConfigurateException if the file cannot be read or is not valid YAML
     */
    public static @NotNull ConfigLoadResult load(@NotNull Path filepath) throws ConfigurateException {
        var root = YamlConfigurationLoader.builder().path(filepath).build().load();
        var warnings = new ArrayList<String>();

        var config = new FastLeafDecayConfig(
            worldFilter(root, ONLY_IN_WORLDS, warnings),
            worldFilter(root, EXCLUDE_WORLDS, warnings),
            delay(root, BREAK_DELAY, FastLeafDecayConfig.DEFAULT_BREAK_DELAY, FastLeafDecayConfig.MIN_BREAK_DELAY, warnings),
            delay(root, DECAY_DELAY, FastLeafDecayConfig.DEFAULT_DECAY_DELAY, FastLeafDecayConfig.MIN_DECAY_DELAY, warnings),
            booleanValue(root, SPAWN_PARTICLES, FastLeafDecayConfig.DEFAULT_SPAWN_PARTICLES, warnings),
            booleanValue(root, PLAY_SOUND, FastLeafDecayConfig.DEFAULT_PLAY_SOUND, warnings)
        );

        return new ConfigLoadResult(config, List.copyOf(warnings));
    }

    private static @NotNull WorldFilter worldFilter(@NotNull ConfigurationNode root, @NotNull String key, @NotNull Collection<String> warnings) {
        var node = root.node(key);

        if (node.virtual() || node.raw() == null) {
            return WorldFilter.empty();
        }

        List<String> entries;

        try {
            entries = node.getList(String.class);
        } catch (SerializationException e) {
            warnings.add("Could not read " + key + " as a string list, no world will be filtered: " + e.getMessage());
            return WorldFilter.empty();
        }

        if (entries == null || entries.isEmpty()) {
            return WorldFilter.empty();
        }

        var result = WorldFilter.parse(entries);

        for (var invalidEntry : result.invalidEntries()) {
            warnings.add("Ignoring an invalid world entry in " + key + ": " + invalidEntry);
        }

        return result.filter();
    }

    private static long delay(@NotNull ConfigurationNode root, @NotNull String key, long fallback, long minimum, @NotNull Collection<String> warnings) {
        var node = root.node(key);

        if (node.virtual() || node.raw() == null) {
            return fallback;
        }

        long value;

        try {
            value = node.get(Long.class, fallback);
        } catch (SerializationException e) {
            warnings.add("Could not read " + key + " as a number, using " + fallback + ": " + e.getMessage());
            return fallback;
        }

        if (value < minimum) {
            warnings.add(key + " must be at least " + minimum + ", using " + minimum + " instead of " + value + ".");
            return minimum;
        }

        return value;
    }

    private static boolean booleanValue(@NotNull ConfigurationNode root, @NotNull String key, boolean fallback, @NotNull Collection<String> warnings) {
        var node = root.node(key);

        if (node.virtual() || node.raw() == null) {
            return fallback;
        }

        try {
            return node.get(Boolean.class, fallback);
        } catch (SerializationException e) {
            warnings.add("Could not read " + key + " as a boolean, using " + fallback + ": " + e.getMessage());
            return fallback;
        }
    }

    private FastLeafDecayConfigLoader() {
        throw new UnsupportedOperationException();
    }
}
