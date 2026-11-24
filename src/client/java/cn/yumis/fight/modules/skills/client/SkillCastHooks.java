package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.HookDispatcher;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.skills.api.*;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Re-entrancy-safe typed lifecycle hooks for player skill casting.
 * Careers, tower cards and visual decorators subscribe by stage instead of adding central branches.
 */
public final class SkillCastHooks {
    public enum Stage {
        BEFORE_REQUEST,
        VALIDATE_REQUEST,
        PREPARE_STATS,
        POST_PAY_STATS,
        RESOLVE_PARAMETERS,
        COMMIT_STATE,
        ON_CAST_START,
        ON_CAST_COMPLETE,
        ON_CAST_ABANDON,
        ON_HIT,
        DECORATE_VISUAL
    }

    public static final class Request {
        public final SkillDefinition skill;
        public final int slot;
        public final int rank;
        public final boolean terminal;
        public final long now;
        public final long wall;
        public final Progress progress;
        public final CareerState currentView;
        public final CareerState nextView;
        public final cn.yumis.fight.modules.combat.api.Runtime resources;
        public final Life life;
        public Stats prepared;
        public SkillParameters.Snapshot parameters;
        public Vec3 origin;
        public Vec3 direction;
        public Vec3 center;
        public boolean rejected;

        public Request(
            SkillDefinition skill, int slot, int rank, boolean terminal, long now, long wall,
            Progress progress, CareerState currentView, CareerState nextView,
            cn.yumis.fight.modules.combat.api.Runtime resources, Life life
        ) {
            this.skill = skill;
            this.slot = slot;
            this.rank = rank;
            this.terminal = terminal;
            this.now = now;
            this.wall = wall;
            this.progress = progress;
            this.currentView = currentView;
            this.nextView = nextView;
            this.resources = resources;
            this.life = life;
        }
    }

    public record CastEvent(SkillCast cast, long now, boolean normal) {}
    public record HitEvent(SkillCast source, LivingEntity target, Stats stats, String basicCycle, boolean direct, JsonObject report, double actual) {}
    public record VisualEvent(SkillCast cast, String phase, JsonObject packet) {}

    @FunctionalInterface
    public interface Listener {
        void handle(Object payload);
    }

    private static final HookDispatcher<Stage, Listener> DISPATCHER = new HookDispatcher<>(Stage.class);
    private static boolean defaultsInstalled;

    private SkillCastHooks() {}

    public static HookDispatcher.Registration onRequest(Stage stage, java.util.function.Consumer<Request> handler) {
        return DISPATCHER.register(stage, payload -> handler.accept((Request) payload));
    }

    public static HookDispatcher.Registration onCast(Stage stage, java.util.function.Consumer<CastEvent> handler) {
        return DISPATCHER.register(stage, payload -> handler.accept((CastEvent) payload));
    }

    public static HookDispatcher.Registration onHit(java.util.function.Consumer<HitEvent> handler) {
        return DISPATCHER.register(Stage.ON_HIT, payload -> handler.accept((HitEvent) payload));
    }

    public static HookDispatcher.Registration onVisual(java.util.function.Consumer<VisualEvent> handler) {
        return DISPATCHER.register(Stage.DECORATE_VISUAL, payload -> handler.accept((VisualEvent) payload));
    }

    public static void dispatch(Stage stage, Object payload) {
        ensureDefaults();
        DISPATCHER.dispatch(stage, listener -> listener.handle(payload));
    }

    public static synchronized void ensureDefaults() {
        if (defaultsInstalled) return;
        defaultsInstalled = true;
        CareerClient.installHooks();
        OracleClient.installHooks();
        DemonHunterClient.installHooks();
        SunMoonClient.installHooks();
        ElementalistClient.installHooks();
        BattleMageClient.installHooks();
        TowerSkillCombat.installHooks();
    }
}
