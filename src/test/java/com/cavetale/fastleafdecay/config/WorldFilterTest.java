package com.cavetale.fastleafdecay.config;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldFilterTest {

    private static List<String> entries(String entries) {
        return entries == null ? List.of() : List.of(entries.split(","));
    }

    private static World world(String name, String key) {
        var world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getKey()).thenReturn(NamespacedKey.fromString(key));
        return world;
    }

    /**
     * Tests of {@link WorldFilter#parse(java.util.Collection)}.
     */
    @Nested
    class Parse {

        @ParameterizedTest(name = "[{0}] -> invalid: [{1}]")
        @CsvSource(delimiter = '|', value = {
            "world                        |",
            "world,other                  |",
            "minecraft:overworld          |",
            "custom:resource_world        |",
            "world,minecraft:the_nether   |",
            "Custom:World                 | Custom:World",
            "custom:re source             | custom:re source",
            "custom:resource:world        | custom:resource:world",
            "world,Invalid:Key            | Invalid:Key",
            "world,minecraft:the_nether,Invalid:Key,custom:re source | Invalid:Key,custom:re source",
        })
        void reportsOnlyTheInvalidEntries(String entries, String invalidEntries) {
            var result = WorldFilter.parse(entries(entries));

            assertEquals(entries(invalidEntries), result.invalidEntries());
            assertEquals(entries(entries).size() == entries(invalidEntries).size(), result.filter().isEmpty());
        }

        @Test
        void emptyEntryListCreatesEmptyFilter() {
            var result = WorldFilter.parse(List.of());

            assertEquals(WorldFilter.empty(), result.filter());
            assertTrue(result.invalidEntries().isEmpty());
        }

        @Test
        void blankAndNullEntriesAreDroppedAndReported() {
            var result = WorldFilter.parse(Arrays.asList("", " ", null));

            assertTrue(result.filter().isEmpty());
            assertEquals(3, result.invalidEntries().size());
        }
    }

    /**
     * Tests of {@link WorldFilter#matches(World)}.
     */
    @Nested
    class Matches {

        @ParameterizedTest(name = "[{0}] vs {1} ({2}) -> {3}")
        @CsvSource(delimiter = '|', value = {
            // a world name is matched against World#getName()
            "world                      | world              | minecraft:overworld   | true",
            "world                      | other              | minecraft:overworld   | false",
            // a namespaced key is matched against World#getKey()
            "minecraft:overworld        | world              | minecraft:overworld   | true",
            "minecraft:overworld        | other              | minecraft:the_nether  | false",
            "custom:resource_world      | resource          | custom:resource_world | true",
            // neither representation is reinterpreted as the other one
            "overworld                  | world              | minecraft:overworld   | false",
            "minecraft:overworld        | minecraft:overworld | custom:other        | false",
            // any entry of the filter may match
            "world,minecraft:the_nether | nether             | minecraft:the_nether  | true",
            "world,minecraft:the_nether | other              | minecraft:the_end     | false",
        })
        void matchesNamesAndKeys(String entries, String worldName, String worldKey, boolean expected) {
            var filter = WorldFilter.parse(entries(entries)).filter();

            assertEquals(expected, filter.matches(world(worldName, worldKey)));
        }

        @Test
        void emptyFilterMatchesNoWorld() {
            assertTrue(WorldFilter.empty().isEmpty());
            assertFalse(WorldFilter.empty().matches(world("world", "minecraft:overworld")));
        }
    }
}
