package com.cavetale.fastleafdecay;

import com.cavetale.fastleafdecay.queue.LeavesSet;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;

public final class FastLeafDecayPlugin extends JavaPlugin implements Listener {

    private static final BlockFace[] NEIGHBORS = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};

    private Set<String> onlyInWorlds = Collections.emptySet();
    private Set<String> excludeWorlds = Collections.emptySet();
    private long breakDelay;
    private long decayDelay;
    private boolean spawnParticles;
    private boolean playSound;
    private boolean oneByOne;

    private LeavesSet leavesSet;

    @Override
    public void onEnable() {
        // Load config
        saveDefaultConfig();
        reloadConfig();

        var config = getConfig();

        var onlyInWorldsList = config.getStringList("OnlyInWorlds");

        if (!onlyInWorldsList.isEmpty()) {
            onlyInWorlds = Set.copyOf(onlyInWorldsList);
        }

        var excludeWorldsList = config.getStringList("ExcludeWorlds");

        if (!excludeWorldsList.isEmpty()) {
            excludeWorlds = Set.copyOf(excludeWorldsList);
        }

        breakDelay = Math.max(config.getLong("BreakDelay"), 1);
        decayDelay = Math.max(config.getLong("DecayDelay"), 1);
        oneByOne = config.getBoolean("OneByOne");
        spawnParticles = config.getBoolean("SpawnParticles");
        playSound = config.getBoolean("PlaySound");

        leavesSet = oneByOne ? LeavesSet.createQueuingSet() : LeavesSet.createSet();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Clean up
        leavesSet.clear();
    }

    /**
     * Whenever a player breaks a log or leaves block, there is a chance
     * that its surrounding blocks should also decay.  We could just
     * wait for the first leaves to decay naturally, but this way, the
     * instant feedback will avoid confusion for players.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        onBlockRemove(event.getBlock(), breakDelay);
    }

    /**
     * Leaves decay has a tendency to cascade.  Whenever leaves decay,
     * we want to check its neighbors to find out if they will also
     * decay.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent event) {
        onBlockRemove(event.getBlock(), decayDelay);
    }

    /**
     * Check if block is either leaves or a log and whether any of the
     * blocks surrounding it are non-persistent leaves blocks.  If so,
     * schedule their respective removal via
     * {@link #decay(Location) block()}.  The latter will perform all
     * necessary checks, including distance.
     *
     * @param oldBlock the block
     * @param delay    the delay of the scheduled check, in ticks
     */
    private void onBlockRemove(final Block oldBlock, long delay) {
        if (!Tag.LOGS.isTagged(oldBlock.getType()) && !Tag.LEAVES.isTagged(oldBlock.getType())) {
            return;
        }

        var worldName = oldBlock.getWorld().getName();

        if (!onlyInWorlds.isEmpty() && !onlyInWorlds.contains(worldName)) {
            return;
        }

        if (excludeWorlds.contains(worldName)) {
            return;
        }

        for (var neighborFace : NEIGHBORS) {
            var block = oldBlock.getRelative(neighborFace);

            if (!Tag.LEAVES.isTagged(block.getType())) {
                continue;
            }

            var leaves = (Leaves) block.getBlockData();
            var location = block.getLocation();

            if (leaves.isPersistent() || leavesSet.contains(location)) {
                continue;
            }

            boolean notScheduled = leavesSet.isEmpty();
            leavesSet.add(location);

            if (oneByOne) {
                if (notScheduled) {
                    getServer().getScheduler().runTaskLater(this, this::decayOne, delay);
                }
            } else {
                getServer().getScheduler().runTaskLater(this, () -> decay(location), delay);
            }
        }
    }

    /**
     * Decay if it is a leaves block and its distance the nearest log
     * block is 7 or greater.
     * <p>
     * This method may only be called by a scheduler if the given
     * block has previously been added to the scheduledBlocks set,
     * from which it will be removed.
     * <p>
     * This method calls {@link LeavesDecayEvent} and will not act if
     * the event is cancelled.
     *
     * @param location The block location
     * @return true if the block was decayed, false otherwise.
     */
    private boolean decay(Location location) {
        leavesSet.remove(location);

        if (!isLoaded(location)) {
            return false;
        }

        var block = location.getBlock();

        if (!Tag.LEAVES.isTagged(block.getType())) { // Is there still leaves?
            return false;
        }

        var leaves = (Leaves) block.getBlockData();

        if (leaves.isPersistent() || leaves.getDistance() < 7) {
            return false;
        }

        var event = new LeavesDecayEvent(block);
        getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        if (spawnParticles) {
            block.getWorld().spawnParticle(Particle.BLOCK_DUST, location.add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0, leaves);
        }

        if (playSound) {
            block.getWorld().playSound(location, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.05f, 1.2f);
        }

        block.breakNaturally();
        return true;
    }

    /**
     * Decay one block from the list of scheduled blocks. Schedule the
     * same function again if the list is not empty.
     * This gets called if OneByOne is activated in the
     * config. Therefore, we wait at least one tick.
     * <p>
     * This could undermine the BlockDelay if the DecayDelay
     * significantly smaller and the list devoid of valid leaf blocks.
     */
    private void decayOne() {
        boolean decayed = false;

        while (!decayed && !leavesSet.isEmpty()) {
            var blockLocation = leavesSet.getFirst();

            if (blockLocation == null) {
                break;
            }

            decayed = decay(blockLocation);
        }

        if (!leavesSet.isEmpty()) {
            getServer().getScheduler().runTaskLater(this, this::decayOne, Math.max(decayDelay, 1));
        }
    }

    private boolean isLoaded(Location location) {
        var world = location.getWorld();
        return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
