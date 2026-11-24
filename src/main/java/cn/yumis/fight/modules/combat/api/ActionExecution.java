package cn.yumis.fight.modules.combat.api;

import java.util.Objects;

/**
 * Stateful runner for one ActionBlueprint execution.
 * Guarantees owner-scoped StatusLeases cleanup and separates Complete vs Abandon semantics.
 */
public final class ActionExecution {
    private final ActionBlueprint blueprint;
    private final ActionContext context;
    private final StatusLeases leases;
    private final ActionPort port;
    private final long startedAt;
    private final double speed;
    private ActionPhase phase = ActionPhase.IDLE;
    private int cursor;
    private boolean canBreak;

    public ActionExecution(ActionBlueprint blueprint, ActionContext context, StatusLeases leases, ActionPort port, long startedAt) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.context = Objects.requireNonNull(context, "context");
        this.leases = leases;
        this.port = Objects.requireNonNull(port, "port");
        this.startedAt = startedAt;
        this.speed = blueprint.fixedActionTime() ? 1.0 : Math.max(0.01, context.power().actionSpeed());
    }

    public ActionBlueprint blueprint() { return blueprint; }
    public ActionContext context() { return context; }
    public ActionPhase phase() { return phase; }
    public boolean active() { return phase.running(); }
    public boolean canBreak() { return canBreak; }
    public int cursor() { return cursor; }
    public long startedAt() { return startedAt; }
    public double speed() { return speed; }

    public long elapsedMillis(long now) {
        return Math.max(0, Math.round((now - startedAt) * speed));
    }

    public boolean canBeCancelledBy(int incomingCancelLevel) {
        if (!active()) return true;
        return canBreak || incomingCancelLevel > blueprint.stiffLevel();
    }

    public void start() {
        if (phase != ActionPhase.IDLE) return;
        phase = ActionPhase.ACTIVE;
        if (leases != null) {
            for (ActionStatus status : blueprint.activeStatuses()) {
                leases.acquire(context.castId(), status);
            }
        }
        port.onStart(context, blueprint);
        if (active() && blueprint.canBreakAtMillis() == 0) {
            markCanBreak();
        }
    }

    public void tick(long now) {
        if (!active()) return;
        long elapsed = elapsedMillis(now);
        if (!canBreak && blueprint.canBreakAtMillis() >= 0 && elapsed >= blueprint.canBreakAtMillis()) {
            markCanBreak();
        }
        var cues = blueprint.cues();
        while (cursor < cues.size() && active()) {
            ActionBlueprint.Cue cue = cues.get(cursor);
            if (elapsed < cue.atMillis()) break;
            int index = cursor++;
            if (cue.releasePose() && !canBreak) markCanBreak();
            port.onCue(context, blueprint, cue, index);
        }
        if (active() && elapsed >= blueprint.durationMillis() && cursor >= cues.size()) {
            complete();
        }
    }

    public void markCanBreak() {
        if (!active() || canBreak) return;
        canBreak = true;
        port.onCanBreak(context, blueprint);
    }

    public void complete() {
        if (!active()) return;
        phase = ActionPhase.COMPLETED;
        canBreak = true;
        try {
            port.onComplete(context, blueprint);
        } finally {
            if (leases != null) leases.releaseOwner(context.castId());
        }
    }

    public void abandon() {
        if (!active()) return;
        phase = ActionPhase.ABANDONED;
        canBreak = true;
        try {
            port.onAbandon(context, blueprint);
        } finally {
            if (leases != null) leases.releaseOwner(context.castId());
        }
    }
}
