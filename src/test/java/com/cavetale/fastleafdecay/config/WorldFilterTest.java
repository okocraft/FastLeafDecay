package com.cavetale.fastleafdecay.config;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorldFilterTest {

    @Mock
    private World world;

    @BeforeEach
    void setUp() {
        when(this.world.getName()).thenReturn("world");
        when(this.world.getKey()).thenReturn(NamespacedKey.fromString("minecraft:overworld"));
    }

    @Test
    void emptyFilterMatchesNoWorld() {
        assertTrue(WorldFilter.empty().isEmpty());
        assertFalse(WorldFilter.empty().matches(this.world));
    }

    @Test
    void emptyEntryListCreatesEmptyFilter() {
        var result = WorldFilter.parse(List.of());

        assertTrue(result.filter().isEmpty());
        assertTrue(result.invalidEntries().isEmpty());
    }

    @Test
    void legacyWorldNameMatchesWorldName() {
        var filter = filter("world");

        assertFalse(filter.isEmpty());
        assertTrue(filter.matches(this.world));
    }

    @Test
    void namespacedKeyMatchesWorldKey() {
        assertTrue(filter("minecraft:overworld").matches(this.world));
    }

    @Test
    void customNamespacedKeyMatchesWorldKey() {
        when(this.world.getName()).thenReturn("resource");
        when(this.world.getKey()).thenReturn(NamespacedKey.fromString("custom:resource_world"));

        assertTrue(filter("custom:resource_world").matches(this.world));
    }

    @Test
    void worldNameIsNotReinterpretedAsNamespacedKey() {
        // "overworld" must not match the key "minecraft:overworld"
        assertFalse(filter("overworld").matches(this.world));
    }

    @Test
    void namespacedKeyIsNotReinterpretedAsWorldName() {
        when(this.world.getName()).thenReturn("minecraft:overworld");
        when(this.world.getKey()).thenReturn(NamespacedKey.fromString("custom:other"));

        assertFalse(filter("minecraft:overworld").matches(this.world));
    }

    @Test
    void otherWorldDoesNotMatch() {
        when(this.world.getName()).thenReturn("other");
        when(this.world.getKey()).thenReturn(NamespacedKey.fromString("minecraft:the_nether"));

        assertFalse(filter("world", "minecraft:overworld").matches(this.world));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Custom:World", "custom:re source", "custom:resource:world"})
    void invalidKeyIsDroppedAndReported(String entry) {
        var result = WorldFilter.parse(List.of(entry));

        assertTrue(result.filter().isEmpty());
        assertEquals(List.of(entry), result.invalidEntries());
    }

    @Test
    void invalidKeyDoesNotDiscardValidEntries() {
        var result = WorldFilter.parse(List.of("world", "Invalid:Key"));

        assertTrue(result.filter().matches(this.world));
        assertEquals(List.of("Invalid:Key"), result.invalidEntries());
    }

    @Test
    void blankEntryIsDroppedAndReported() {
        var result = WorldFilter.parse(Arrays.asList(" ", null));

        assertTrue(result.filter().isEmpty());
        assertEquals(2, result.invalidEntries().size());
    }

    private static WorldFilter filter(String... entries) {
        return WorldFilter.parse(List.of(entries)).filter();
    }
}
