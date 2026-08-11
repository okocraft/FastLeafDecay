package com.cavetale.fastleafdecay.decay;

import com.cavetale.fastleafdecay.config.FastLeafDecayConfigHolder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * The component that decays a single leaves block.
 * <p>
 * It performs the checks that must be evaluated at the time the decay actually
 * happens. It neither knows about the pending queues nor schedules any task.
 */
public final class LeafDecayExecutor {

    /**
     * The minimum distance to the nearest log block at which leaves decay.
     */
    public static final int MIN_DECAY_DISTANCE = 7;

    private static final Sound DECAY_SOUND = Sound.sound(Key.key("block.grass.break"), Sound.Source.BLOCK, 0.05f, 1.2f);

    private final PluginManager pluginManager;
    private final FastLeafDecayConfigHolder configHolder;
    private final Predicate<Material> leavesPredicate;

    public LeafDecayExecutor(@NotNull PluginManager pluginManager, @NotNull FastLeafDecayConfigHolder configHolder,
                             @NotNull Predicate<Material> leavesPredicate) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.configHolder = Objects.requireNonNull(configHolder, "configHolder");
        this.leavesPredicate = Objects.requireNonNull(leavesPredicate, "leavesPredicate");
    }

    public boolean decay(@NotNull Location location) {
        var config = this.configHolder.get();
        var block = location.getBlock();

        if (!this.leavesPredicate.test(block.getType())) { // Is there still leaves?
            return false;
        }

        var leaves = (Leaves) block.getBlockData();

        if (leaves.isPersistent() || leaves.getDistance() < MIN_DECAY_DISTANCE) {
            return false;
        }

        var event = new LeavesDecayEvent(block);
        this.pluginManager.callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        var world = block.getWorld();

        if (config.spawnParticles()) {
            world.spawnParticle(Particle.BLOCK, location.clone().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0, leaves);
        }

        if (config.playSound()) {
            world.playSound(DECAY_SOUND, location.x(), location.y(), location.z());
        }

        block.breakNaturally();
        return true;
    }
}
