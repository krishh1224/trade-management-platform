package com.team5.strategies.domain;

// The account's position in one symbol at the moment of a decision: what it holds, and what its
// unexecuted (NEW) orders will add or take away once the executor fills them.
public class PositionSnapshot {

    private long held;
    private long pendingBuy;
    private long pendingSell;

    public PositionSnapshot() {
    }

    public PositionSnapshot(long held, long pendingBuy, long pendingSell) {
        this.held = held;
        this.pendingBuy = pendingBuy;
        this.pendingSell = pendingSell;
    }

    // the position if every pending BUY fills: what a max_position check has to assume
    public long committedLong() {
        return held + pendingBuy;
    }

    // what is left to sell once pending SELLs are taken out
    public long availableToSell() {
        return Math.max(0, held - pendingSell);
    }

    public long getHeld() { return held; }
    public void setHeld(long held) { this.held = held; }

    public long getPendingBuy() { return pendingBuy; }
    public void setPendingBuy(long pendingBuy) { this.pendingBuy = pendingBuy; }

    public long getPendingSell() { return pendingSell; }
    public void setPendingSell(long pendingSell) { this.pendingSell = pendingSell; }
}
