package cn.yumis.fight.modules.combat.api;

import java.util.*;

/**
 * First-class entry point for sourceless/world-spawned skills and shared blueprint execution.
 * Supports HOSTILE_TO_PLAYERS, HOSTILE_TO_ENEMIES and BOTH via injected ActionPorts.
 */
public final class SourcelessActions {
    private SourcelessActions() {}

    public static ActionExecution start(ActionBlueprint blueprint, ActionContext context, StatusLeases leases, ActionPort port, long now) {
        ActionExecution execution = new ActionExecution(blueprint, context, leases, port, now);
        execution.start();
        execution.tick(now);
        return execution;
    }

    public static ActionExecution startById(String blueprintId, ActionContext context, StatusLeases leases, ActionPort port, long now) {
        return start(ActionRegistry.require(blueprintId), context, leases, port, now);
    }

    /** Runtime container for managing concurrent sourceless or world-owned blueprint executions. */
    public static final class Runtime {
        private final Map<String, ActionExecution> active = new LinkedHashMap<>();
        private final StatusLeases leases = new StatusLeases();

        public StatusLeases leases() {
            return leases;
        }

        public ActionExecution launch(ActionBlueprint blueprint, ActionContext context, ActionPort port, long now) {
            Objects.requireNonNull(context, "context");
            ActionExecution execution = start(blueprint, context, leases, port, now);
            if (execution.active()) active.put(context.castId(), execution);
            return execution;
        }

        public ActionExecution launchById(String blueprintId, ActionContext context, ActionPort port, long now) {
            return launch(ActionRegistry.require(blueprintId), context, port, now);
        }

        public void tick(long now) {
            leases.prune(now);
            if (active.isEmpty()) return;
            for (ActionExecution execution : new ArrayList<>(active.values())) {
                if (execution.active()) execution.tick(now);
                if (!execution.active()) active.remove(execution.context().castId(), execution);
            }
        }

        public boolean abandon(String castId) {
            ActionExecution execution = active.remove(castId);
            if (execution == null) return false;
            execution.abandon();
            return true;
        }

        public void abandonByInstance(String instance) {
            if (instance == null) return;
            for (ActionExecution execution : new ArrayList<>(active.values())) {
                if (instance.equals(execution.context().instance())) {
                    active.remove(execution.context().castId(), execution);
                    execution.abandon();
                }
            }
        }

        public void clear() {
            for (ActionExecution execution : new ArrayList<>(active.values())) {
                execution.abandon();
            }
            active.clear();
            leases.clear();
        }

        public int activeCount() {
            return active.size();
        }
    }
}
