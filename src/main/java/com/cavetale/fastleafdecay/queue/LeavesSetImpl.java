package com.cavetale.fastleafdecay.queue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.bukkit.Location;

class LeavesSetImpl implements LeavesSet {

    private final LongSet leavesSet = LongSets.synchronize(new LongOpenHashSet());

    LeavesSetImpl() {
    }

    @Override
    public boolean add(Location pos) {
        return leavesSet.add(getBlockKey(pos));
    }

    @Override
    public boolean remove(Location pos) {
        return leavesSet.remove(getBlockKey(pos));
    }

    @Override
    public boolean isEmpty() {
        return leavesSet.isEmpty();
    }

    @Override
    public void clear() {
        leavesSet.clear();
    }

    private static long getBlockKey(final Location pos) {
        return ((long) pos.x() & 0x7FFFFFF) | (((long) pos.z() & 0x7FFFFFF) << 27) | ((long) pos.y() << 54);
    }
}
