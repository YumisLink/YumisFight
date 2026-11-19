package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.RitualBossCatalog;
import cn.yumis.fight.modules.dungeons.api.RitualBossEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Shared per-boss behaviour for the six ritual altar fights; the service owns stages, transactions and settlement. */
public abstract class RitualBossContent {
    protected final RitualEncounterService service;
    protected final Set<String> handlers = new LinkedHashSet<>();

    protected RitualBossContent(RitualEncounterService service) { this.service = service; }

    public static RitualBossContent create(String bossId, RitualEncounterService service) {
        return switch (bossId) {
            case "uncrowned_gardener" -> new GardenerContent(service);
            case "off_duty_matron" -> new OffDutyMatronContent(service);
            case "white_reliquary" -> new WhiteReliquaryContent(service);
            case "debt_collector" -> new DebtCollectorContent(service);
            case "fallen_scale_king" -> new FallenScaleKingContent(service);
            case "thousandfold_confession" -> new ThousandfoldConfessionContent(service);
            default -> throw new IllegalArgumentException("未知魔王祭坛内容：" + bossId);
        };
    }

    /** Called once when the boss has been published and the fight clock starts. */
    public abstract void begin(RitualEncounterService.Run run);

    /** Server tick for move selection, phases and boss movement. */
    public abstract void tick(RitualEncounterService.Run run, RitualBossEntity boss, long now);

    /** Every accepted damage receipt against the boss (fraction of maximum health). */
    public void damaged(RitualEncounterService.Run run, ServerPlayer player, double fraction, Target target) {}

    /** The boss life record reached zero without a weakness ending. */
    public void death(RitualEncounterService.Run run, Target target) {}

    /** Right-click on the boss itself (confirmations and listener dialogue). */
    public boolean bossInteract(RitualEncounterService.Run run, ServerPlayer player, RitualBossEntity boss) { return false; }

    public void mechanismBroken(RitualEncounterService.Run run, String kind, UUID id, String breaker) {}

    public boolean mechanismInteract(RitualEncounterService.Run run, ServerPlayer player, String kind, UUID id) { return false; }

    public boolean handled(String player) { return handlers.contains(player); }

    public Collection<String> handlers() { return handlers; }

    protected final List<ServerPlayer> fighters(RitualEncounterService.Run run) { return service.fightersInCircle(run); }

    protected final RitualBossCatalog.Boss catalog(RitualEncounterService.Run run) { return service.catalog(run); }

    protected final RitualBossCatalog.Move move(RitualEncounterService.Run run, String id) { return catalog(run).move(id); }

    protected final void start(RitualEncounterService.Run run, String moveId, Vec3 origin, Vec3 direction, UUID locked) {
        service.startMove(run, move(run, moveId), origin, direction, locked);
    }

    protected final boolean cooling(RitualEncounterService.Run run, String moveId, long now) {
        return service.onCooldown(run, move(run, moveId), now);
    }

    protected final Vec3 direction(Vec3 from, Vec3 to) {
        var delta = to.subtract(from);
        double length = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return length < .01 ? new Vec3(1, 0, 0) : new Vec3(delta.x / length, 0, delta.z / length);
    }

    protected final ServerPlayer nearest(RitualEncounterService.Run run, Vec3 from) {
        return extreme(run, from, true);
    }

    protected final ServerPlayer farthest(RitualEncounterService.Run run, Vec3 from) {
        return extreme(run, from, false);
    }

    private ServerPlayer extreme(RitualEncounterService.Run run, Vec3 from, boolean nearest) {
        ServerPlayer best = null;
        double bestDistance = nearest ? Double.MAX_VALUE : -1;
        for (var player : fighters(run)) {
            double distance = player.position().distanceToSqr(from);
            if (nearest ? distance < bestDistance : distance > bestDistance) { bestDistance = distance; best = player; }
        }
        return best;
    }

    protected final ServerPlayer any(RitualEncounterService.Run run) {
        var list = fighters(run);
        return list.isEmpty() ? null : list.get(service.random().nextInt(list.size()));
    }

    protected final void walk(RitualEncounterService.Run run, RitualBossEntity boss, Vec3 to, double speed) {
        Vec3 delta = to.subtract(run.origin);
        if (delta.horizontalDistanceSqr() > (run.radius - 1d) * (run.radius - 1d)) to = edge(run, delta);
        boss.getNavigation().moveTo(to.x, to.y, to.z, speed);
    }

    /** A point near the stage edge in the given horizontal direction. */
    protected final Vec3 edge(RitualEncounterService.Run run, Vec3 direction) {
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (length < .01) { direction = new Vec3(1, 0, 0); length = 1; }
        return run.origin.add(direction.x / length * (run.radius - 2), 0, direction.z / length * (run.radius - 2));
    }

    protected final UUID spawn(RitualEncounterService.Run run, String kind, double health, boolean immune, Vec3 at) {
        return service.spawnMechanism(run, kind, health, immune, at);
    }

    protected final void say(ServerPlayer player, String text) { service.message(player, text); }

    protected final double hp(RitualEncounterService.Run run) {
        return run.bossTarget == null ? 1 : Math.max(0, run.bossTarget.life.q);
    }

    protected final boolean busy(RitualEncounterService.Run run) { return !run.casts.isEmpty(); }
}
