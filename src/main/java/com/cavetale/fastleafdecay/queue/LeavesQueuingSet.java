package com.cavetale.fastleafdecay.queue;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

class LeavesQueuingSet implements LeavesSet {

    private final Queue<Location> leavesQueue = new ArrayDeque<>();
    private final Set<Location> queuedLeavesSet = new HashSet<>();

    public void add(Location location) {
        leavesQueue.offer(location);
        queuedLeavesSet.add(location);
    }

    @Override
    public void remove(Location location) {
        // This should be removed in getFirst, so it will not normally be true.
        if (queuedLeavesSet.remove(location)) {
            leavesQueue.remove(location);
        }
    }

    public Location getFirst() {
        var location = leavesQueue.poll();

        if (location != null) {
            queuedLeavesSet.remove(location);
        }

        return location;
    }

    public boolean contains(Location location) {
        return queuedLeavesSet.contains(location);
    }

    public boolean isEmpty() {
        return leavesQueue.isEmpty();
    }

    public void clear() {
        leavesQueue.clear();
        queuedLeavesSet.clear();
    }
}
