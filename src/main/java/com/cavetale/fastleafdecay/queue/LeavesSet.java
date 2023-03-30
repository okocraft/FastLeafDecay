package com.cavetale.fastleafdecay.queue;

import org.bukkit.Location;

public interface LeavesSet {

    static LeavesSet createSet() {
        return new LeavesSetImpl();
    }

    boolean add(Location pos);

    boolean remove(Location pos);

    boolean isEmpty();

    void clear();
}
