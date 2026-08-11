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
