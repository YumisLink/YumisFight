package cn.yumis.fight.modules.combat.api;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Lightweight, actor-agnostic cast context shared by players, enemies and sourceless world actions.
 */
public record ActionContext(
    String castId,
    SourceKind sourceKind,
    UUID sourceId,
    String dimension,
    String instance,
    Vec3 origin,
    Vec3 direction,
    Vec3 center,
    TargetPolicy targetPolicy,
    ActionPower power,
    Map<String, Double> parameters,
    long startedAt
) {
    public enum SourceKind { PLAYER, ENEMY, SOURCELESS }

    public ActionContext {
        if (castId == null || castId.isEmpty()) castId = UUID.randomUUID().toString();
        Objects.requireNonNull(sourceKind, "sourceKind");
        dimension = dimension == null ? "" : dimension;
        instance = instance == null ? "" : instance;
        origin = origin == null ? Vec3.ZERO : origin;
        direction = normalizeDirection(direction);
        center = center == null ? origin : center;
        Objects.requireNonNull(targetPolicy, "targetPolicy");
        Objects.requireNonNull(power, "power");
        parameters = parameters == null || parameters.isEmpty() ? Map.of() : Map.copyOf(parameters);
    }

    public double param(String key, double fallback) {
        Double value = parameters.get(key);
        return value == null || !Double.isFinite(value) ? fallback : value;
    }

    public ActionContext withCenter(Vec3 nextCenter) {
        return new ActionContext(castId, sourceKind, sourceId, dimension, instance, origin, direction, nextCenter, targetPolicy, power, parameters, startedAt);
    }

    public ActionContext withPower(ActionPower nextPower) {
        return new ActionContext(castId, sourceKind, sourceId, dimension, instance, origin, direction, center, targetPolicy, nextPower, parameters, startedAt);
    }

    public static ActionContext forPlayer(
        String castId, UUID playerId, String dimension, String instance,
        Vec3 origin, Vec3 direction, Vec3 center,
        ActionPower power, Map<String, Double> parameters, long startedAt
    ) {
        return new ActionContext(castId, SourceKind.PLAYER, playerId, dimension, instance, origin, direction, center, TargetPolicy.HOSTILE_TO_ENEMIES, power, parameters, startedAt);
    }

    public static ActionContext forEnemy(
        String castId, UUID enemyId, String dimension, String instance,
        Vec3 origin, Vec3 direction, Vec3 center,
        ActionPower power, Map<String, Double> parameters, long startedAt
    ) {
        return new ActionContext(castId, SourceKind.ENEMY, enemyId, dimension, instance, origin, direction, center, TargetPolicy.HOSTILE_TO_PLAYERS, power, parameters, startedAt);
    }

    public static ActionContext sourceless(
        String castId, String dimension, String instance,
        Vec3 origin, Vec3 direction, Vec3 center,
        TargetPolicy targetPolicy, ActionPower power, Map<String, Double> parameters, long startedAt
    ) {
        return new ActionContext(castId, SourceKind.SOURCELESS, null, dimension, instance, origin, direction, center, targetPolicy, power, parameters, startedAt);
    }

    private static Vec3 normalizeDirection(Vec3 v) {
        if (v == null || v.lengthSqr() < 1e-6) return new Vec3(0, 0, 1);
        return v.normalize();
    }
}
