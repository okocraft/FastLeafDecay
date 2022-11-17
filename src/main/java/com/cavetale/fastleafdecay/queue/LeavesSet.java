package com.cavetale.fastleafdecay.queue;

import org.bukkit.Location;

public interface LeavesSet {

    static LeavesSet createSet() {
        return new LeavesSetImpl();
    }

    static LeavesSet createQueuingSet() {
        return new LeavesQueuingSet();
    }

    void add(Location location);

    void remove(Location location);

    Location getFirst();

    boolean contains(Location location);

    boolean isEmpty();

    void clear();
}
