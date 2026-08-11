package com.cavetale.fastleafdecay.queue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * A thread-safe set of the leaves positions that are waiting for their scheduled
 * decay check.
 * <p>
 * An instance holds the positions of a single world, so a position is stored as a
 * packed block key that does not encode the world itself.
 */
public final class LeavesSet {

    private final LongSet leavesSet = LongSets.synchronize(new LongOpenHashSet());

    public boolean add(@NotNull Location pos) {
        return this.leavesSet.add(getBlockKey(pos));
    }

    public boolean remove(@NotNull Location pos) {
        return this.leavesSet.remove(getBlockKey(pos));
    }

    public boolean isEmpty() {
        return this.leavesSet.isEmpty();
    }

    public void clear() {
        this.leavesSet.clear();
    }

    private static long getBlockKey(final Location pos) {
        return ((long) pos.x() & 0x7FFFFFF) | (((long) pos.z() & 0x7FFFFFF) << 27) | ((long) pos.y() << 54);
    }
}
