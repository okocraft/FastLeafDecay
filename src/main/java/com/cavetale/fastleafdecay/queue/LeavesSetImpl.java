package com.cavetale.fastleafdecay.queue;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;

class LeavesSetImpl implements LeavesSet {

    private final Set<Location> leavesSet = new HashSet<>();

    @Override
    public void add(Location location) {
        leavesSet.add(location);
    }

    @Override
    public void remove(Location location) {
        leavesSet.remove(location);
    }

    @Override
    public Location getFirst() {
        return null; // unsupported
    }

    @Override
    public boolean contains(Location location) {
        return leavesSet.contains(location);
    }

    @Override
    public boolean isEmpty() {
        return leavesSet.isEmpty();
    }

    @Override
    public void clear() {
        leavesSet.clear();
    }
}
