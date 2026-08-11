package com.cavetale.fastleafdecay.decay;

import com.cavetale.fastleafdecay.config.FastLeafDecayConfig;
import com.cavetale.fastleafdecay.config.FastLeafDecayConfigHolder;
import com.cavetale.fastleafdecay.queue.LeavesSet;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The listener that finds the leaves which should decay and schedules their decay
 * checks.
 * <p>
 * The pending positions are held per loaded world, so a position is deduplicated
 * within its world and the queue of an unloaded world is dropped as a whole.
 */
public final class LeafDecayHandler implements Listener {

    private static final BlockFace[] NEIGHBORS = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};

    private final Map<UUID, LeavesSet> leavesSetMap = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final FastLeafDecayConfigHolder configHolder;
    private final LeafDecayExecutor executor;
    private final Predicate<Material> logPredicate;
    private final Predicate<Material> leavesPredicate;

    public LeafDecayHandler(@NotNull Plugin plugin, @NotNull FastLeafDecayConfigHolder configHolder, @NotNull LeafDecayExecutor executor,
                            @NotNull Predicate<Material> logPredicate, @NotNull Predicate<Material> leavesPredicate) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configHolder = Objects.requireNonNull(configHolder, "configHolder");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.logPredicate = Objects.requireNonNull(logPredicate, "logPredicate");
        this.leavesPredicate = Objects.requireNonNull(leavesPredicate, "leavesPredicate");
    }

    /**
     * Whenever a player breaks a log or leaves block, there is a chance that its
     * surrounding blocks should also decay. We could just wait for the first leaves
     * to decay naturally, but this way, the instant feedback will avoid confusion
     * for players.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        var config = this.configHolder.get();
        onBlockRemove(event.getBlock(), config, config.breakDelay());
    }

    /**
     * Leaves decay has a tendency to cascade. Whenever leaves decay, we want to
     * check its neighbors to find out if they will also decay.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeavesDecay(@NotNull LeavesDecayEvent event) {
        var config = this.configHolder.get();
        onBlockRemove(event.getBlock(), config, config.decayDelay());
    }

    /**
     * A task that was already scheduled for the unloaded world may still hold the
     * removed set, but because it has been cleared, its pending check fails and the
     * task stops without decaying anything.
     */
    @EventHandler
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        var removed = this.leavesSetMap.remove(event.getWorld().getUID());

        if (removed != null) {
            removed.clear();
        }
    }

    public void clear() {
        for (var leavesSet : this.leavesSetMap.values()) {
            leavesSet.clear();
        }

        this.leavesSetMap.clear();
    }

    private void onBlockRemove(@NotNull Block oldBlock, @NotNull FastLeafDecayConfig config, long delay) {
        if (!this.logPredicate.test(oldBlock.getType()) && !this.leavesPredicate.test(oldBlock.getType())) {
            return;
        }

        var world = oldBlock.getWorld();

        if (!config.isEnabledIn(world)) {
            return;
        }

        var leavesSet = this.leavesSetMap.computeIfAbsent(world.getUID(), $ -> new LeavesSet());

        for (var neighborFace : NEIGHBORS) {
            var block = oldBlock.getRelative(neighborFace);

            if (!this.leavesPredicate.test(block.getType())) {
                continue;
            }

            var leaves = (Leaves) block.getBlockData();
            var location = block.getLocation();

            if (!leaves.isPersistent() && leavesSet.add(location)) {
                scheduleDecayTask(leavesSet, location, delay);
            }
        }
    }

    private void scheduleDecayTask(@NotNull LeavesSet leavesSet, @NotNull Location location, long delay) {
        this.plugin.getServer().getRegionScheduler().runDelayed(this.plugin, location, $ -> runDecayTask(leavesSet, location), delay);
    }

    private void runDecayTask(@NotNull LeavesSet leavesSet, @NotNull Location location) {
        if (!leavesSet.remove(location)) { // Has the entry been dropped in the meantime?
            return;
        }

        if (!this.configHolder.get().isEnabledIn(location.getWorld())) {
            return;
        }

        this.executor.decay(location);
    }
}
