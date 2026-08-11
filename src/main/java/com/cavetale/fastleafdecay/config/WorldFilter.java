package com.cavetale.fastleafdecay.config;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A set of world entries that can be matched against a {@link World}.
 * <p>
 * An entry without a colon is treated as a Bukkit world name and is matched
 * against {@link World#getName()}. An entry with a colon is treated as a
 * namespaced key and is matched against {@link World#getKey()}. A world name
 * is never reinterpreted as a namespaced key, or vice versa.
 */
public final class WorldFilter {

    private static final WorldFilter EMPTY = new WorldFilter(Set.of(), Set.of());

    /**
     * Returns a filter without any entry, which matches no world.
     *
     * @return an empty filter
     */
    public static @NotNull WorldFilter empty() {
        return EMPTY;
    }

    /**
     * Creates a filter from the given configuration entries.
     * <p>
     * Entries that cannot be parsed are not included in the resulting filter and
     * are reported through {@link ParseResult#invalidEntries()}.
     *
     * @param entries the configured world names and namespaced keys
     * @return the created filter and the entries that could not be parsed
     */
    public static @NotNull ParseResult parse(@NotNull Collection<String> entries) {
        var names = new HashSet<String>();
        var keys = new HashSet<NamespacedKey>();
        var invalidEntries = new ArrayList<String>();

        for (var entry : entries) {
            if (entry == null || entry.isBlank()) {
                invalidEntries.add(String.valueOf(entry));
                continue;
            }

            if (entry.indexOf(':') < 0) {
                names.add(entry);
                continue;
            }

            var key = NamespacedKey.fromString(entry);

            if (key == null) {
                invalidEntries.add(entry);
                continue;
            }

            keys.add(key);
        }

        var filter = names.isEmpty() && keys.isEmpty() ? EMPTY : new WorldFilter(Set.copyOf(names), Set.copyOf(keys));
        return new ParseResult(filter, List.copyOf(invalidEntries));
    }

    /**
     * The result of {@link #parse(Collection)}.
     *
     * @param filter         the created filter
     * @param invalidEntries the entries that could not be parsed and were dropped
     */
    public record ParseResult(@NotNull WorldFilter filter, @NotNull List<String> invalidEntries) {
    }

    private final Set<String> names;
    private final Set<NamespacedKey> keys;

    private WorldFilter(@NotNull Set<String> names, @NotNull Set<NamespacedKey> keys) {
        this.names = names;
        this.keys = keys;
    }

    /**
     * Checks if this filter has no entry.
     *
     * @return {@code true} if this filter has no entry, otherwise {@code false}
     */
    public boolean isEmpty() {
        return this.names.isEmpty() && this.keys.isEmpty();
    }

    /**
     * Checks if the given world is contained in this filter.
     *
     * @param world the world to check
     * @return {@code true} if the world matches one of the entries, otherwise {@code false}
     */
    public boolean matches(@NotNull World world) {
        if (!this.names.isEmpty() && this.names.contains(world.getName())) {
            return true;
        }

        return !this.keys.isEmpty() && this.keys.contains(world.getKey());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WorldFilter other && this.names.equals(other.names) && this.keys.equals(other.keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.names, this.keys);
    }

    @Override
    public String toString() {
        return "WorldFilter{names=" + this.names + ", keys=" + this.keys + '}';
    }
}
