package com.cavetale.fastleafdecay.queue;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class LeavesSetTest {

    @Mock
    private World world;

    private LeavesSet leavesSet;

    @BeforeEach
    void setUp() {
        this.leavesSet = new LeavesSet();
    }

    private Location location(int x, int y, int z) {
        return new Location(this.world, x, y, z);
    }

    @Nested
    @DisplayName("a position")
    class Position {

        @Test
        void isHeldUntilItIsRemoved() {
            var location = location(1, 2, 3);

            assertTrue(leavesSet.isEmpty());
            assertTrue(leavesSet.add(location));
            assertFalse(leavesSet.isEmpty());
            assertTrue(leavesSet.remove(location));
            assertTrue(leavesSet.isEmpty());
        }

        @ParameterizedTest(name = "({0}, {1}, {2})")
        @CsvSource({
            "0, 0, 0",
            "-1, 64, 1",
            "100, -32, -100",
        })
        void isAddedOnlyOnce(int x, int y, int z) {
            assertTrue(leavesSet.add(location(x, y, z)));
            assertFalse(leavesSet.add(location(x, y, z)));
        }

        @ParameterizedTest(name = "({0}, {1}, {2})")
        @CsvSource({
            "0, 0, 0",
            "-1, 64, 1",
        })
        void isRemovedOnlyOnce(int x, int y, int z) {
            leavesSet.add(location(x, y, z));

            assertTrue(leavesSet.remove(location(x, y, z)));
            assertFalse(leavesSet.remove(location(x, y, z)));
        }

        @Test
        void isNotRemovedWhenItWasNeverAdded() {
            assertFalse(leavesSet.remove(location(1, 2, 3)));
        }

        @ParameterizedTest(name = "({0}, {1}, {2}) and ({3}, {4}, {5})")
        @CsvSource({
            // a single differing coordinate is enough
            "        0,   0,        0,         1,   0,         0",
            "        0,   0,        0,         0,   1,         0",
            "        0,   0,        0,         0,   0,         1",
            // a negative coordinate is not stored as its positive counterpart
            "        1,   2,        3,        -1,   2,         3",
            "        1,   2,        3,         1,  -2,         3",
            "        1,   2,        3,         1,   2,        -3",
            // the coordinates are not interchangeable
            "        1,   2,        3,         3,   2,         1",
            // the corners of the world are still distinct
            "-30000000, -64, 30000000,  30000000, 320, -30000000",
        })
        void isStoredSeparatelyFromEveryOtherPosition(int x1, int y1, int z1, int x2, int y2, int z2) {
            assertTrue(leavesSet.add(location(x1, y1, z1)));
            assertTrue(leavesSet.add(location(x2, y2, z2)));

            assertTrue(leavesSet.remove(location(x1, y1, z1)));
            assertFalse(leavesSet.isEmpty());
            assertTrue(leavesSet.remove(location(x2, y2, z2)));
            assertTrue(leavesSet.isEmpty());
        }
    }

    /**
     * A position is packed into a single long, which gives x and z 27 bits each and
     * leaves 10 bits for y. Positions that are further apart than the range such a
     * field can represent share their key, so every case below stays inside one
     * range: |x|, |z| below 2^26 and |y| below 512, which covers the whole world.
     */
    @Nested
    @DisplayName("the packed position")
    class PackedPosition {

        private static final int[] OFFSETS = {-1, 0, 1};

        @ParameterizedTest(name = "({0}, {1}, {2}) and ({3}, {4}, {5})")
        @CsvSource({
            // a negative coordinate is not stored as its positive counterpart
            "       -1,   64,         0,          1,   64,         0",
            "        0,   64,        -1,          0,   64,         1",
            "        0,   -1,         0,          0,    1,         0",
            // the position next to the origin of an axis
            "       -1,   64,         0,          0,   64,         0",
            "        0,   64,        -1,          0,   64,         0",
            "        0,   -1,         0,          0,    0,         0",
            // the neighbors of the highest and the lowest x that the field represents
            " 67108862,   64,         0,   67108863,   64,         0",
            " 67108863,   64,         0,   67108864,   64,         0",
            "-67108864,   64,         0,  -67108863,   64,         0",
            // the same for z
            "        0,   64,  67108862,          0,   64,  67108863",
            "        0,   64,  67108863,          0,   64,  67108864",
            "        0,   64, -67108864,          0,   64, -67108863",
            // and for y
            "        0,  510,         0,          0,  511,         0",
            "        0,  511,         0,          0,  512,         0",
            "        0, -512,         0,          0, -511,         0",
            "        0, -512,         0,          0,  511,         0",
            // a field is wide enough to keep the ends of its range apart
            "        0,   64,         0,   67108864,   64,         0",
            "        0,   64,         0,          0,   64,  67108864",
            "        0,    0,         0,          0,  512,         0",
            // an extreme value of one axis does not reach into the field of another one
            " 67108863,   64,         0,          0,   64,  67108863",
            "       -1,   64,         0,          0,   64,        -1",
            "        0,   64,        -1,          0,   -1,         0",
            // the corners of the world border and the height limits
            " 30000000,   64,         0,   29999999,   64,         0",
            "-30000000,   64,         0,  -29999999,   64,         0",
            "        0,  319,         0,          0,  -64,         0",
        })
        void isDistinctFromTheOtherPositionsAtTheBoundaries(int x1, int y1, int z1, int x2, int y2, int z2) {
            assertTrue(leavesSet.add(location(x1, y1, z1)));
            assertTrue(leavesSet.add(location(x2, y2, z2)));

            assertTrue(leavesSet.remove(location(x1, y1, z1)));
            assertFalse(leavesSet.isEmpty());
            assertTrue(leavesSet.remove(location(x2, y2, z2)));
            assertTrue(leavesSet.isEmpty());
        }

        @ParameterizedTest(name = "around ({0}, {1}, {2})")
        @CsvSource({
            "        0,    0,         0",
            "       -1,   -1,        -1",
            " 67108863,  511,  67108863",
            "-67108864, -512, -67108864",
            " 30000000,  319, -30000000",
            "-30000000,  -64,  30000000",
        })
        void isDistinctFromEveryAdjacentPosition(int x, int y, int z) {
            forEachAdjacentPosition(x, y, z, position ->
                assertTrue(leavesSet.add(position), () -> position + " was already stored"));

            forEachAdjacentPosition(x, y, z, position ->
                assertTrue(leavesSet.remove(position), () -> position + " was not stored"));

            assertTrue(leavesSet.isEmpty());
        }

        @ParameterizedTest(name = "({0}, {1}, {2})")
        @CsvSource({
            " 67108863,  511,  67108863",
            "-67108864, -512, -67108864",
            " 30000000,  319, -30000000",
        })
        void isTheSameKeyEveryTimeItIsPacked(int x, int y, int z) {
            assertTrue(leavesSet.add(location(x, y, z)));
            assertFalse(leavesSet.add(location(x, y, z)));
            assertTrue(leavesSet.remove(location(x, y, z)));
        }

        private void forEachAdjacentPosition(int x, int y, int z, Consumer<Location> action) {
            for (var offsetX : OFFSETS) {
                for (var offsetY : OFFSETS) {
                    for (var offsetZ : OFFSETS) {
                        action.accept(location(x + offsetX, y + offsetY, z + offsetZ));
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        void removesEveryPosition() {
            leavesSet.add(location(1, 1, 1));
            leavesSet.add(location(2, 2, 2));

            leavesSet.clear();

            assertTrue(leavesSet.isEmpty());
        }

        @Test
        void makesTheRemovalOfAPendingPositionFail() {
            leavesSet.add(location(1, 1, 1));

            leavesSet.clear();

            assertFalse(leavesSet.remove(location(1, 1, 1)));
        }
    }

    /**
     * The set is shared by the region threads, so its operations must stay exclusive
     * under concurrent access. Every worker waits at a barrier so that the operations
     * really run at the same time, and every wait is bounded so that a missing worker
     * fails the test instead of blocking the build.
     */
    @Nested
    @DisplayName("concurrent access")
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    class Concurrency {

        private static final int WORKERS = 16;
        private static final long TIMEOUT_SECONDS = 10;

        @Test
        void letsASinglePositionBeAddedByExactlyOneCaller() throws Exception {
            var location = location(1, 2, 3);

            assertEquals(1, countTrue(worker -> leavesSet.add(location)));
            assertFalse(leavesSet.isEmpty());
        }

        @Test
        void letsASinglePositionBeRemovedByExactlyOneCaller() throws Exception {
            var location = location(1, 2, 3);
            leavesSet.add(location);

            assertEquals(1, countTrue(worker -> leavesSet.remove(location)));
            assertTrue(leavesSet.isEmpty());
        }

        @Test
        void keepsEveryDistinctPositionThatWasAddedAtTheSameTime() throws Exception {
            assertEquals(WORKERS, countTrue(worker -> leavesSet.add(location(worker, 64, 0))));

            assertEquals(WORKERS, countTrue(worker -> leavesSet.remove(location(worker, 64, 0))));
            assertTrue(leavesSet.isEmpty());
        }

        private long countTrue(IntPredicate operation) throws Exception {
            var barrier = new CyclicBarrier(WORKERS);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var results = IntStream.range(0, WORKERS)
                    .mapToObj(worker -> executor.<Boolean>submit(() -> {
                        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        return operation.test(worker);
                    }))
                    .toList();

                try {
                    long trueResults = 0;

                    for (var result : results) {
                        if (result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            trueResults++;
                        }
                    }

                    return trueResults;
                } finally {
                    // a worker that did not finish in time must not make close() wait for it
                    results.forEach(result -> result.cancel(true));
                }
            }
        }
    }
}
