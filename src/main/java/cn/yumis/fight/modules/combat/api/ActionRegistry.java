package cn.yumis.fight.modules.combat.api;

import java.util.*;

/** Cross-module registry of reusable ActionBlueprints populated by skills and dungeons. */
public final class ActionRegistry {
    private static final Map<String, ActionBlueprint> BLUEPRINTS = new LinkedHashMap<>();

    private ActionRegistry() {}

    public static synchronized ActionBlueprint register(ActionBlueprint blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        if (BLUEPRINTS.putIfAbsent(blueprint.id(), blueprint) != null) {
            throw new IllegalArgumentException("Duplicate action blueprint: " + blueprint.id());
        }
        return blueprint;
    }

    public static synchronized ActionBlueprint registerOrReplace(ActionBlueprint blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        BLUEPRINTS.put(blueprint.id(), blueprint);
        return blueprint;
    }

    public static synchronized ActionBlueprint find(String id) {
        return id == null ? null : BLUEPRINTS.get(id);
    }

    public static synchronized ActionBlueprint require(String id) {
        ActionBlueprint value = find(id);
        if (value == null) throw new IllegalArgumentException("Unknown action blueprint: " + id);
        return value;
    }

    public static synchronized boolean contains(String id) {
        return id != null && BLUEPRINTS.containsKey(id);
    }

    public static synchronized Collection<ActionBlueprint> all() {
        return List.copyOf(BLUEPRINTS.values());
    }
}
