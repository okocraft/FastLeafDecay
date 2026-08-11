package com.cavetale.fastleafdecay.decay;

import com.cavetale.fastleafdecay.config.FastLeafDecayConfig;
import com.cavetale.fastleafdecay.config.FastLeafDecayConfigHolder;
import com.cavetale.fastleafdecay.config.WorldFilter;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeafDecayHandlerTest {

    private static final Material LOG = Material.OAK_LOG;
    private static final Material LEAVES = Material.OAK_LEAVES;
    private static final Material OTHER = Material.STONE;

    private static final BlockFace[] NEIGHBOR_FACES =
        {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};

    private static final UUID WORLD_UID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_WORLD_UID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final String WORLD_NAME = "world";

    private enum Removal {

        BLOCK_BREAK {
            @Override
            void dispatch(LeafDecayHandler handler, Block block) {
                handler.onBlockBreak(new BlockBreakEvent(block, mock(Player.class)));
            }

            @Override
            long delayOf(FastLeafDecayConfig config) {
                return config.breakDelay();
            }
        },
        LEAVES_DECAY {
            @Override
            void dispatch(LeafDecayHandler handler, Block block) {
                handler.onLeavesDecay(new LeavesDecayEvent(block));
            }

            @Override
            long delayOf(FastLeafDecayConfig config) {
                return config.decayDelay();
            }
        };

        abstract void dispatch(LeafDecayHandler handler, Block block);

        abstract long delayOf(FastLeafDecayConfig config);
    }

    private enum Neighbor {

        DECAYABLE_LEAVES,
        PERSISTENT_LEAVES,
        NOT_LEAVES
    }

    @Mock
    private Plugin plugin;

    @Mock
    private Server server;

    @Mock
    private RegionScheduler scheduler;

    @Mock
    private World world;

    @Mock
    private World otherWorld;

    @Mock
    private LeafDecayExecutor executor;

    @Captor
    private ArgumentCaptor<Location> locationCaptor;

    @Captor
    private ArgumentCaptor<Consumer<ScheduledTask>> taskCaptor;

    private FastLeafDecayConfigHolder configHolder;
    private LeafDecayHandler handler;

    @BeforeEach
    void setUp() {
        this.configHolder = new FastLeafDecayConfigHolder(FastLeafDecayConfig.defaults());
        this.handler = new LeafDecayHandler(this.plugin, this.configHolder, this.executor,
            material -> material == LOG, material -> material == LEAVES);
    }

    /**
     * Only the tests that expect a scheduled task need the scheduler, so it is not
     * stubbed for the tests that assert that nothing is scheduled.
     */
    private void givenRegionScheduler() {
        when(this.plugin.getServer()).thenReturn(this.server);
        when(this.server.getRegionScheduler()).thenReturn(this.scheduler);
    }

    private FastLeafDecayConfig setDelays(long breakDelay, long decayDelay) {
        var config = new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.empty(), breakDelay, decayDelay, true, true);
        this.configHolder.set(config);
        return config;
    }

    private void excludeTheWorld() {
        when(this.world.getName()).thenReturn(WORLD_NAME);
        this.configHolder.set(new FastLeafDecayConfig(WorldFilter.empty(), WorldFilter.parse(List.of(WORLD_NAME)).filter(),
            FastLeafDecayConfig.DEFAULT_BREAK_DELAY, FastLeafDecayConfig.DEFAULT_DECAY_DELAY, true, true));
    }

    private Block removedBlock(Material type) {
        var block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        return block;
    }

    /**
     * Creates the removed block and the six blocks around it. The neighbor of the
     * first face is described by {@code first}, the remaining ones by {@code rest}.
     */
    private Block removedBlockSurroundedBy(World world, Material type, Neighbor first, Neighbor rest) {
        var block = removedBlock(type);
        when(block.getWorld()).thenReturn(world);
        when(world.getUID()).thenReturn(world == this.otherWorld ? OTHER_WORLD_UID : WORLD_UID);

        for (int i = 0; i < NEIGHBOR_FACES.length; i++) {
            var neighbor = neighborBlock(world, i == 0 ? first : rest, i);
            when(block.getRelative(NEIGHBOR_FACES[i])).thenReturn(neighbor);
        }

        return block;
    }

    private Block removedBlockSurroundedByLeaves(World world, Material type) {
        return removedBlockSurroundedBy(world, type, Neighbor.DECAYABLE_LEAVES, Neighbor.DECAYABLE_LEAVES);
    }

    private Block neighborBlock(World world, Neighbor neighbor, int index) {
        var block = mock(Block.class);
        when(block.getType()).thenReturn(neighbor == Neighbor.NOT_LEAVES ? OTHER : LEAVES);

        if (neighbor != Neighbor.NOT_LEAVES) {
            var blockData = mock(Leaves.class);
            when(blockData.isPersistent()).thenReturn(neighbor == Neighbor.PERSISTENT_LEAVES);
            when(block.getBlockData()).thenReturn(blockData);

            if (neighbor == Neighbor.DECAYABLE_LEAVES) {
                when(block.getLocation()).thenReturn(neighborLocation(world, index));
            }
        }

        return block;
    }

    /**
     * The neighbors of the removed block only differ in their x coordinate, so the
     * same index describes the same position in every world.
     */
    private Location neighborLocation(World world, int index) {
        return new Location(world, index, 64, 0);
    }

    private List<Location> scheduledLocations(long expectedDelay, int expectedCount) {
        verify(this.scheduler, times(expectedCount))
            .runDelayed(eq(this.plugin), this.locationCaptor.capture(), any(), eq(expectedDelay));
        return this.locationCaptor.getAllValues();
    }

    private List<Location> allScheduledLocations() {
        verify(this.scheduler, atLeastOnce()).runDelayed(eq(this.plugin), this.locationCaptor.capture(), any(), anyLong());
        return this.locationCaptor.getAllValues();
    }

    /**
     * Returns the tasks in the order in which they were scheduled, so the task of the
     * neighbor with the index {@code i} of the first dispatched event is at {@code i}.
     */
    private List<Consumer<ScheduledTask>> scheduledTasks() {
        verify(this.scheduler, atLeastOnce()).runDelayed(eq(this.plugin), any(Location.class), this.taskCaptor.capture(), anyLong());
        return this.taskCaptor.getAllValues();
    }

    private Consumer<ScheduledTask> scheduledTaskOf(int index) {
        return scheduledTasks().get(index);
    }

    @Nested
    @DisplayName("a removed block")
    class RemovedBlock {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Removal.class)
        void schedulesEveryNeighborWithTheConfiguredDelay(Removal removal) {
            givenRegionScheduler();
            var config = setDelays(17, 23);
            var block = removedBlockSurroundedByLeaves(world, LOG);

            removal.dispatch(handler, block);

            assertEquals(List.of(neighborLocation(world, 0), neighborLocation(world, 1), neighborLocation(world, 2),
                    neighborLocation(world, 3), neighborLocation(world, 4), neighborLocation(world, 5)),
                scheduledLocations(removal.delayOf(config), NEIGHBOR_FACES.length));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Removal.class)
        void isIgnoredWhenItIsNeitherLogNorLeaves(Removal removal) {
            removal.dispatch(handler, removedBlock(OTHER));

            verifyNoInteractions(plugin, executor);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Removal.class)
        void isIgnoredInADisabledWorld(Removal removal) {
            excludeTheWorld();
            var block = removedBlock(LEAVES);
            when(block.getWorld()).thenReturn(world);

            removal.dispatch(handler, block);

            verifyNoInteractions(plugin, executor);
        }

        @ParameterizedTest(name = "the neighbors of a removed {0} are scheduled")
        @EnumSource(value = Material.class, names = {"OAK_LOG", "OAK_LEAVES"})
        void isAcceptedWhenItIsALogOrLeaves(Material type) {
            givenRegionScheduler();

            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(world, type));

            verify(scheduler, times(NEIGHBOR_FACES.length))
                .runDelayed(eq(plugin), any(Location.class), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("a neighbor")
    class NeighborBlock {

        @ParameterizedTest(name = "{0} is not scheduled")
        @EnumSource(value = Neighbor.class, names = {"PERSISTENT_LEAVES", "NOT_LEAVES"})
        void isSkippedWhenItCannotDecay(Neighbor skipped) {
            givenRegionScheduler();
            var block = removedBlockSurroundedBy(world, LOG, Neighbor.DECAYABLE_LEAVES, skipped);

            Removal.BLOCK_BREAK.dispatch(handler, block);

            assertEquals(List.of(neighborLocation(world, 0)),
                scheduledLocations(FastLeafDecayConfig.DEFAULT_BREAK_DELAY, 1));
        }

        @Test
        void isScheduledOnlyOnceWhileItIsPending() {
            givenRegionScheduler();
            var block = removedBlockSurroundedByLeaves(world, LOG);

            Removal.BLOCK_BREAK.dispatch(handler, block);
            Removal.BLOCK_BREAK.dispatch(handler, block);

            verify(scheduler, times(NEIGHBOR_FACES.length))
                .runDelayed(eq(plugin), any(Location.class), any(), anyLong());
        }

        @Test
        void isScheduledAgainAfterItsPendingTaskRan() {
            givenRegionScheduler();
            var block = removedBlockSurroundedByLeaves(world, LOG);

            Removal.BLOCK_BREAK.dispatch(handler, block);
            scheduledTaskOf(0).accept(null);
            Removal.BLOCK_BREAK.dispatch(handler, block);

            var locations = allScheduledLocations();
            assertEquals(NEIGHBOR_FACES.length + 1, locations.size());
            assertEquals(2, locations.stream().filter(neighborLocation(world, 0)::equals).count());
        }
    }

    @Nested
    @DisplayName("the scheduled task")
    class ScheduledDecayTask {

        @Test
        void decaysTheLeaves() {
            givenRegionScheduler();

            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(world, LOG));
            scheduledTaskOf(0).accept(null);

            verify(executor).decay(neighborLocation(world, 0));
        }

        @Test
        void decaysTheLeavesOnlyOnce() {
            givenRegionScheduler();

            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(world, LOG));
            var task = scheduledTaskOf(0);
            task.accept(null);
            task.accept(null);

            verify(executor, times(1)).decay(any(Location.class));
        }

        @Test
        void doesNothingWhenTheWorldWasDisabledInTheMeantime() {
            givenRegionScheduler();

            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(world, LOG));
            var task = scheduledTaskOf(0);
            excludeTheWorld();
            task.accept(null);

            verifyNoInteractions(executor);
        }

        @Test
        void doesNothingAfterTheWorldWasUnloaded() {
            givenRegionScheduler();

            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(world, LOG));
            var task = scheduledTaskOf(0);
            handler.onWorldUnload(new WorldUnloadEvent(world));
            task.accept(null);

            verifyNoInteractions(executor);
        }

        @Test
        void doesNothingAfterTheHandlerWasCleared() {
            givenRegionScheduler();

            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(world, LOG));
            var task = scheduledTaskOf(0);
            handler.clear();
            task.accept(null);

            verifyNoInteractions(executor);
        }
    }

    /**
     * The pending positions are held per world while {@code LeavesSet} stores a
     * position without its world, so the worlds must not share their pending state.
     */
    @Nested
    @DisplayName("another world")
    class MultipleWorlds {

        @BeforeEach
        void dispatchInBothWorlds() {
            givenRegionScheduler();

            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(world, LOG));
            Removal.BLOCK_BREAK.dispatch(handler, removedBlockSurroundedByLeaves(otherWorld, LOG));
        }

        @Test
        void holdsTheSamePositionIndependently() {
            var locations = scheduledLocations(FastLeafDecayConfig.DEFAULT_BREAK_DELAY, 2 * NEIGHBOR_FACES.length);

            assertTrue(locations.contains(neighborLocation(world, 0)));
            assertTrue(locations.contains(neighborLocation(otherWorld, 0)));
        }

        @Test
        void keepsItsPendingPositionsWhenAnotherWorldIsUnloaded() {
            handler.onWorldUnload(new WorldUnloadEvent(world));

            scheduledTaskOf(0).accept(null);
            scheduledTaskOf(NEIGHBOR_FACES.length).accept(null);

            verify(executor, never()).decay(neighborLocation(world, 0));
            verify(executor).decay(neighborLocation(otherWorld, 0));
        }

        @Test
        void losesItsPendingPositionsWhenTheHandlerIsCleared() {
            handler.clear();

            scheduledTaskOf(0).accept(null);
            scheduledTaskOf(NEIGHBOR_FACES.length).accept(null);

            verifyNoInteractions(executor);
        }
    }
}
