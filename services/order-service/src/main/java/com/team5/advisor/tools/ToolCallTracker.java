package com.team5.advisor.tools;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Records which tools the model called during one chat request, in first-call order, for toolsUsed. */
public final class ToolCallTracker {

    private final Set<String> names = new LinkedHashSet<>();

    public synchronized void record(String toolName) {
        names.add(toolName);
    }

    public synchronized List<String> names() {
        return List.copyOf(names);
    }
}
