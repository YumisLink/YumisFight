package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.dungeons.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Registry of classic dungeon move effects, replacing string switches in ClassicEncounterService. */
final class ClassicMoveEffects {
    @FunctionalInterface
    interface Handler {
        void apply(ClassicEncounterService service, Dungeon dungeon, DungeonEnemy enemy,
                   ClassicEncounterService.Brain brain, ClassicMove move, ServerPlayer locked, long now);
    }

    private static final Map<String, Handler> HANDLERS = new LinkedHashMap<>();

    static {
        register("guard", (s, d, e, b, m, locked, now) -> s.acquireStatus(e, b, "move/guard", ActionStatus.GUARDED, now + 2000));
        register("aura", (s, d, e, b, m, locked, now) -> s.acquireStatus(e, b, "move/guard", ActionStatus.GUARDED, now + 2000));
        register("heal", (s, d, e, b, m, locked, now) -> {
            for (var ally : s.nearby(e, 7)) s.heal(ally, 1 / 30d);
        });
        register("fall_heal", (s, d, e, b, m, locked, now) -> s.heal(e, 1 / 30d));
        register("inhale", (s, d, e, b, m, locked, now) -> s.heal(e, 1 / 30d));
        register("teleport", (s, d, e, b, m, locked, now) -> {
            Vec3 at = ClassicEncounterService.inside(d, b.area, b.destination.add(b.direction.scale(-3)));
            e.setPos(at.x, ClassicEncounterService.floorY(d, b) + (ClassicEnemyKind.of(e).flying ? .35 : 0), at.z);
        });
        register("takeoff", (s, d, e, b, m, locked, now) -> {
            b.airFrom = now;
            b.airUntil = now + 3000;
            s.acquireStatus(e, b, "move/takeoff", ActionStatus.NO_GRAVITY, now + 3000);
        });
    }

    private ClassicMoveEffects() {}

    static void register(String effect, Handler handler) {
        HANDLERS.put(Objects.requireNonNull(effect), Objects.requireNonNull(handler));
    }

    static void dispatch(ClassicEncounterService service, Dungeon dungeon, DungeonEnemy enemy,
                         ClassicEncounterService.Brain brain, ClassicMove move, ServerPlayer locked, long now) {
        Handler handler = HANDLERS.get(move.effect());
        if (handler != null) handler.apply(service, dungeon, enemy, brain, move, locked, now);
        if (move.isSelfDestruct()) service.selfDestruct(enemy);
    }
}
