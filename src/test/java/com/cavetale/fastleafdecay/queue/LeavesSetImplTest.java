package com.cavetale.fastleafdecay.queue;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class LeavesSetImplTest {

    @Mock
    private World world;

    private LeavesSet leavesSet;

    @BeforeEach
    void setUp() {
        leavesSet = LeavesSet.createSet();
    }

    @Test
    void addAndRemoveReflectInEmptiness() {
        Location location = new Location(world, 1, 2, 3);

        assertTrue(leavesSet.isEmpty());
        assertTrue(leavesSet.add(location));
        assertFalse(leavesSet.isEmpty());
        assertTrue(leavesSet.remove(location));
        assertTrue(leavesSet.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0",
        "-1, 64, 1",
        "100, -32, -100",
    })
    void addingSameCoordinatesTwiceReturnsFalseOnSecondAdd(int x, int y, int z) {
        Location location = new Location(world, x, y, z);

        assertTrue(leavesSet.add(location));
        assertFalse(leavesSet.add(location));
    }

    @Test
    void clearRemovesAllEntries() {
        leavesSet.add(new Location(world, 1, 1, 1));
        leavesSet.add(new Location(world, 2, 2, 2));

        leavesSet.clear();

        assertTrue(leavesSet.isEmpty());
    }
}
