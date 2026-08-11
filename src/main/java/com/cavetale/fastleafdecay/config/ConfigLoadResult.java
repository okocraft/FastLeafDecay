package com.cavetale.fastleafdecay.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The result of {@link FastLeafDecayConfigLoader#load(java.nio.file.Path)}.
 * <p>
 * The configuration is always usable. Values that could not be read are replaced
 * with their default and described in {@link #warnings()}, which the caller is
 * expected to report to the user.
 *
 * @param config   the loaded configuration
 * @param warnings the messages describing the values that had to be replaced
 */
public record ConfigLoadResult(@NotNull FastLeafDecayConfig config, @NotNull List<String> warnings) {
}
