package com.cavetale.fastleafdecay.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The configuration is always usable. Values that could not be read are replaced
 * with their default and described in {@link #warnings()}, which the caller is
 * expected to report to the user.
 */
public record ConfigLoadResult(@NotNull FastLeafDecayConfig config, @NotNull List<String> warnings) {
}
