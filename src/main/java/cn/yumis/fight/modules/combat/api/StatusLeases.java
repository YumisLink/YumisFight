package cn.yumis.fight.modules.combat.api;

import java.util.*;

/**
 * Owner-scoped status lease registry shared by player skills and dungeon enemies.
 * Multiple concurrent actions can hold the same status without overwriting each other on completion or abort.
 */
public final class StatusLeases {
    public static final class Lease implements AutoCloseable {
        private StatusLeases registry;
        private final String owner;
        private final ActionStatus status;
        private final long expiresAt;
        private final int priority;

        private Lease(StatusLeases registry, String owner, ActionStatus status, long expiresAt, int priority) {
            this.registry = registry;
            this.owner = owner;
            this.status = status;
            this.expiresAt = expiresAt;
            this.priority = priority;
        }

        public String owner() { return owner; }
        public ActionStatus status() { return status; }
        public long expiresAt() { return expiresAt; }
        public int priority() { return priority; }
        public boolean active() { return registry != null; }

        public void release() {
            StatusLeases current = registry;
            if (current != null) current.remove(this);
        }

        @Override
        public void close() {
            release();
        }
    }

    private final List<Lease> leases = new ArrayList<>();

    public Lease acquire(String owner, ActionStatus status) {
        return acquire(owner, status, Long.MAX_VALUE, 0);
    }

    public Lease acquire(String owner, ActionStatus status, long expiresAt) {
        return acquire(owner, status, expiresAt, 0);
    }

    public synchronized Lease acquire(String owner, ActionStatus status, long expiresAt, int priority) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(status, "status");
        if (owner.isEmpty()) throw new IllegalArgumentException("Lease owner must not be empty");
        Lease lease = new Lease(this, owner, status, expiresAt, priority);
        leases.add(lease);
        return lease;
    }

    public synchronized boolean has(ActionStatus status, long now) {
        pruneLocked(now);
        for (Lease lease : leases) {
            if (lease.status == status) return true;
        }
        return false;
    }

    public synchronized boolean has(ActionStatus status) {
        for (Lease lease : leases) {
            if (lease.status == status) return true;
        }
        return false;
    }

    public synchronized boolean hasOwner(String owner, ActionStatus status, long now) {
        pruneLocked(now);
        for (Lease lease : leases) {
            if (lease.status == status && lease.owner.equals(owner)) return true;
        }
        return false;
    }

    public synchronized boolean releaseOwner(String owner) {
        if (owner == null || owner.isEmpty()) return false;
        boolean changed = false;
        for (int i = leases.size() - 1; i >= 0; i--) {
            Lease lease = leases.get(i);
            if (lease.owner.equals(owner)) {
                lease.registry = null;
                leases.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    public synchronized boolean release(String owner, ActionStatus status) {
        if (owner == null || owner.isEmpty() || status == null) return false;
        boolean changed = false;
        for (int i = leases.size() - 1; i >= 0; i--) {
            Lease lease = leases.get(i);
            if (lease.status == status && lease.owner.equals(owner)) {
                lease.registry = null;
                leases.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    public synchronized void prune(long now) {
        pruneLocked(now);
    }

    public synchronized void clear() {
        for (Lease lease : leases) lease.registry = null;
        leases.clear();
    }

    public synchronized EnumSet<ActionStatus> activeStatuses(long now) {
        pruneLocked(now);
        EnumSet<ActionStatus> result = EnumSet.noneOf(ActionStatus.class);
        for (Lease lease : leases) result.add(lease.status);
        return result;
    }

    public synchronized int size() {
        return leases.size();
    }

    private synchronized void remove(Lease lease) {
        if (lease.registry != this) return;
        lease.registry = null;
        leases.remove(lease);
    }

    private void pruneLocked(long now) {
        for (int i = leases.size() - 1; i >= 0; i--) {
            Lease lease = leases.get(i);
            if (lease.expiresAt <= now) {
                lease.registry = null;
                leases.remove(i);
            }
        }
    }
}
