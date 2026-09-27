package com.example.springboot.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe log buffer that supports concurrent writes and atomic draining.
 */
public final class Buffer7 {

    private final BlockingQueue<String> entries = new LinkedBlockingQueue<>();

    public int add(String entry) {
        entries.add(Objects.requireNonNull(entry, "entry must not be null"));
        return entries.size();
    }

    public List<String> drainAll() {
        List<String> drainedEntries = new ArrayList<>();
        entries.drainTo(drainedEntries);
        return drainedEntries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
