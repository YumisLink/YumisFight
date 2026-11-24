package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Shared server-side execution runtime for dungeon enemies and sourceless world actions.
 * Runs actor-agnostic ActionBlueprints from combat/api's ActionRegistry, manages owner-scoped StatusLeases,
 * and supports humanoid enemies invoking player skill blueprints with minimal Target/ActionPower data.
 */
public final class EnemyActionRuntime {
    private static final Gson JSON = new Gson();
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Targets targets;
    private final Map<UUID, StatusLeases> enemyLeases = new HashMap<>();
    private final Map<UUID, ActionExecution> enemyActions = new LinkedHashMap<>();
    private final SourcelessActions.Runtime sourceless = new SourcelessActions.Runtime();

    EnemyActionRuntime(DungeonService owner, Journal journal, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.journal = journal;
        this.targets = targets;
    }

    public StatusLeases leasesFor(UUID enemyId) {
        return enemyLeases.computeIfAbsent(enemyId, ignored -> new StatusLeases());
    }

    public StatusLeases.Lease acquire(DungeonEnemy enemy, String leaseOwner, ActionStatus status, long expiresAt) {
        StatusLeases.Lease lease = leasesFor(enemy.getUUID()).acquire(leaseOwner, status, expiresAt);
        syncEnemyStatuses(enemy, System.currentTimeMillis());
        return lease;
    }

    public void releaseOwner(DungeonEnemy enemy, String leaseOwner) {
        if (enemy == null) return;
        StatusLeases leases = enemyLeases.get(enemy.getUUID());
        if (leases != null && leases.releaseOwner(leaseOwner)) {
            syncEnemyStatuses(enemy, System.currentTimeMillis());
        }
    }

    public boolean hasStatus(UUID enemyId, ActionStatus status, long now) {
        StatusLeases leases = enemyLeases.get(enemyId);
        return leases != null && leases.has(status, now);
    }

    /**
     * Executes any shared ActionBlueprint (including player skills from SkillBlueprints) on a humanoid DungeonEnemy
     * without requiring full player Stats. Visuals ride ChampionPosePort/Effects; contacts ride classicAttack packets.
     */
    public ActionExecution castBlueprint(Dungeon dungeon, DungeonEnemy enemy, String blueprintId, Vec3 aim, long now) {
        ActionBlueprint blueprint = ActionRegistry.require(blueprintId);
        cancelEnemy(enemy.getUUID());
        Target target = targets.target(enemy);
        int level = Math.max(1, target.originalLevel);
        Vec3 origin = enemy.position();
        Vec3 direction = aim == null ? enemy.getLookAngle().multiply(1, 0, 1) : aim.subtract(origin).multiply(1, 0, 1);
        if (direction.lengthSqr() < 1e-6) direction = new Vec3(0, 0, 1);
        direction = direction.normalize();
        ActionPower power = ActionPower.fromEnemy(target.attack(level), 1.0, false, -1, 1.0);
        ActionContext ctx = ActionContext.forEnemy(
            UUID.randomUUID().toString(), enemy.getUUID(),
            enemy.level().dimension().identifier().toString(), enemy.instance(),
            origin, direction, aim == null ? origin : aim, power, Map.of(), now
        );
        StatusLeases leases = leasesFor(enemy.getUUID());
        ActionExecution execution = new ActionExecution(blueprint, ctx, leases, new ServerBlueprintPort(dungeon, enemy, target, enemy.level() instanceof ServerLevel sl ? sl : owner.level()), now);
        enemyActions.put(enemy.getUUID(), execution);
        execution.start();
        syncEnemyStatuses(enemy, now);
        execution.tick(now);
        if (!execution.active()) {
            enemyActions.remove(enemy.getUUID(), execution);
            syncEnemyStatuses(enemy, now);
        }
        return execution;
    }

    /**
     * First-class sourceless casting entrypoint: runs any ActionBlueprint at a world position/direction
     * with minimal ActionPower and explicit TargetPolicy (HOSTILE_TO_PLAYERS, HOSTILE_TO_ENEMIES, or BOTH).
     */
    public ActionExecution castSourceless(String blueprintId, ActionContext context, ServerLevel level, long now) {
        return castSourceless(ActionRegistry.require(blueprintId), context, level, now);
    }

    public ActionExecution castSourceless(ActionBlueprint blueprint, ActionContext context, ServerLevel level, long now) {
        ServerLevel targetLevel = level == null ? owner.level() : level;
        Dungeon dungeon = context.instance().isEmpty() ? null : owner.byId(context.instance());
        Target synthetic = new Target();
        synthetic.id = context.sourceId() == null ? UUID.nameUUIDFromBytes(("sourceless/" + context.castId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : context.sourceId();
        synthetic.dimension = context.dimension();
        synthetic.kind = "SOURCELESS";
        synthetic.fixedLevel = true;
        synthetic.originalLevel = 1;
        synthetic.speciesAttack = Math.max(1.0 / 15.0, context.power().baseAttack() / 15.0);
        return sourceless.launch(blueprint, context, new ServerBlueprintPort(dungeon, null, synthetic, targetLevel), now);
    }

    public SourcelessActions.Runtime sourcelessRuntime() {
        return sourceless;
    }

    void tick(long now) {
        sourceless.tick(now);
        for (var entry : new ArrayList<>(enemyActions.entrySet())) {
            UUID enemyId = entry.getKey();
            ActionExecution execution = entry.getValue();
            Entity raw = owner.level().getEntity(enemyId);
            if (!(raw instanceof DungeonEnemy enemy) || !enemy.isAlive() || (CombatContentTypes.controlled(enemy) && !hasStatus(enemyId, ActionStatus.STEADFAST, now))) {
                execution.abandon();
                enemyActions.remove(enemyId, execution);
                if (raw instanceof DungeonEnemy enemy) syncEnemyStatuses(enemy, now);
                continue;
            }
            execution.tick(now);
            syncEnemyStatuses(enemy, now);
            if (!execution.active()) enemyActions.remove(enemyId, execution);
        }
        for (var entry : new ArrayList<>(enemyLeases.entrySet())) {
            entry.getValue().prune(now);
            Entity raw = owner.level().getEntity(entry.getKey());
            if (raw instanceof DungeonEnemy enemy && enemy.isAlive()) {
                syncEnemyStatuses(enemy, now);
            } else if (raw == null || !raw.isAlive()) {
                entry.getValue().clear();
                enemyLeases.remove(entry.getKey());
            }
        }
    }

    void cancelEnemy(UUID enemyId) {
        ActionExecution execution = enemyActions.remove(enemyId);
        if (execution != null) execution.abandon();
        StatusLeases leases = enemyLeases.get(enemyId);
        if (leases != null) leases.clear();
        Entity raw = owner.level().getEntity(enemyId);
        if (raw instanceof DungeonEnemy enemy) syncEnemyStatuses(enemy, System.currentTimeMillis());
    }

    void closeInstance(String instance) {
        sourceless.abandonByInstance(instance);
        for (var entry : new ArrayList<>(enemyActions.entrySet())) {
            if (instance.equals(entry.getValue().context().instance())) {
                entry.getValue().abandon();
                enemyActions.remove(entry.getKey());
            }
        }
    }

    private void syncEnemyStatuses(DungeonEnemy enemy, long now) {
        StatusLeases leases = enemyLeases.get(enemy.getUUID());
        if (leases == null) return;
        leases.prune(now);
        boolean steadfast = leases.has(ActionStatus.STEADFAST, now);
        var kb = enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null && steadfast && kb.getBaseValue() < 1) kb.setBaseValue(1);
    }

    private final class ServerBlueprintPort implements ActionPort {
        private final Dungeon dungeon;
        private final DungeonEnemy actor;
        private final Target sourceTarget;
        private final ServerLevel level;
        private int visualSequence;

        ServerBlueprintPort(Dungeon dungeon, DungeonEnemy actor, Target sourceTarget, ServerLevel level) {
            this.dungeon = dungeon;
            this.actor = actor;
            this.sourceTarget = sourceTarget;
            this.level = level;
        }

        @Override
        public void onStart(ActionContext context, ActionBlueprint blueprint) {
            if (actor != null) {
                actor.getNavigation().stop();
                actor.play(blueprint.poseClip(), blueprint.durationMillis());
            }
            broadcastVisual(context, blueprint, "START", context.origin(), context.direction(), blueprint.durationMillis());
        }

        @Override
        public void onCue(ActionContext context, ActionBlueprint blueprint, ActionBlueprint.Cue cue, int cueIndex) {
            Vec3 origin = cue.atCenter() ? context.center() : (actor != null ? actor.position() : context.origin());
            Vec3 direction = context.direction();
            String phase = cue.visualPhase().isEmpty() ? "HIT" : cue.visualPhase();
            broadcastVisual(context, blueprint, phase, origin, direction, Math.max(300, blueprint.durationMillis()));
            if (cue.geometry() == ActionBlueprint.Geometry.NONE || cue.coefficientShare() <= 0) return;

            double coeff = context.power().coefficient() * cue.coefficientShare();
            if (context.targetPolicy().hitsPlayers() && !context.instance().isEmpty()) {
                broadcastPlayerContact(context, blueprint, cue, cueIndex, origin, direction, coeff);
            }
            if (context.targetPolicy().hitsEnemies() && level != null) {
                applyEnemyHits(context, cue, origin, direction, coeff);
            }
        }

        @Override
        public void onComplete(ActionContext context, ActionBlueprint blueprint) {
            broadcastVisual(context, blueprint, "END", actor != null ? actor.position() : context.center(), context.direction(), 300);
        }

        @Override
        public void onAbandon(ActionContext context, ActionBlueprint blueprint) {
            if (actor != null) actor.play("", 0);
            broadcastVisual(context, blueprint, "CANCEL", actor != null ? actor.position() : context.center(), context.direction(), 200);
        }

        private void broadcastVisual(ActionContext context, ActionBlueprint blueprint, String phase, Vec3 point, Vec3 direction, long duration) {
            if (context.instance().isEmpty()) return;
            long now = System.currentTimeMillis();
            JsonObject j = new JsonObject();
            String caster = context.sourceId() == null ? context.castId() : context.sourceId().toString();
            j.addProperty("caster", caster);
            j.addProperty("session", caster);
            j.addProperty("generation", 1);
            j.addProperty("id", context.castId());
            j.addProperty("kind", blueprint.id());
            j.addProperty("phase", phase);
            j.addProperty("sequence", ++visualSequence);
            j.addProperty("seed", context.castId().hashCode());
            j.addProperty("startedAt", context.startedAt());
            j.addProperty("at", now);
            j.addProperty("serverNow", now);
            j.addProperty("duration", Math.max(1, duration));
            j.addProperty("instance", context.instance());
            j.addProperty("dimension", context.dimension());
            j.addProperty("cosmeticOnly", true);
            j.addProperty("x", context.origin().x);
            j.addProperty("y", context.origin().y);
            j.addProperty("z", context.origin().z);
            j.addProperty("px", point.x);
            j.addProperty("py", point.y);
            j.addProperty("pz", point.z);
            j.addProperty("dx", direction.x);
            j.addProperty("dy", direction.y);
            j.addProperty("dz", direction.z);
            owner.broadcast(context.instance(), "visual", j);
        }

        private void broadcastPlayerContact(ActionContext context, ActionBlueprint blueprint, ActionBlueprint.Cue cue, int cueIndex, Vec3 origin, Vec3 direction, double coeff) {
            long now = System.currentTimeMillis();
            PalaceAttack attack = new PalaceAttack();
            attack.instance = context.instance();
            attack.source = sourceTarget.id;
            attack.kind = blueprint.id();
            attack.group = context.castId() + "/cue/" + cueIndex;
            attack.shape = switch (cue.geometry()) {
                case AREA -> "CIRCLE";
                case FAN -> "FAN";
                case WAVE -> "WAVE";
                case DASH -> "DASH";
                case PROJECTILE -> "DART";
                default -> "RECT";
            };
            attack.started = now;
            attack.windup = 0;
            attack.active = cue.geometry() == ActionBlueprint.Geometry.WAVE ? 650 : 200;
            attack.origin(origin);
            attack.destination(origin.add(direction.scale(Math.max(0.5, cue.range()))));
            attack.radius = Math.max(0.5, cue.range());
            attack.width = Math.max(0.5, cue.width());
            attack.halfAngle = cue.geometry() == ActionBlueprint.Geometry.FAN ? Math.toRadians(Math.max(30, cue.width()) / 2.0) : Math.PI / 3.0;
            attack.coefficient = coeff;
            attack.magic = context.power().magic();
            attack.element = context.power().primaryElement();
            JsonObject packet = JSON.toJsonTree(attack).getAsJsonObject();
            packet.add("boss", JSON.toJsonTree(sourceTarget));
            packet.addProperty("cell", dungeon == null ? 0 : dungeon.cell);
            packet.addProperty("area", dungeon == null ? 0 : dungeon.wave);
            packet.addProperty("retired", actor == null);
            packet.addProperty("unstoppable", true);
            owner.broadcast(context.instance(), "classicAttack", packet);
        }

        private void applyEnemyHits(ActionContext context, ActionBlueprint.Cue cue, Vec3 origin, Vec3 direction, double coeff) {
            double range = Math.max(0.5, cue.range());
            double width = Math.max(0.5, cue.width());
            double height = Math.max(2.0, cue.height());
            AABB bounds = new AABB(origin, origin).inflate(range + 1, height, range + 1);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, e -> e.isAlive() && (actor == null || !e.getUUID().equals(actor.getUUID())) && e instanceof DungeonEnemy)) {
                Vec3 offset = entity.getBoundingBox().getCenter().subtract(origin);
                if (Math.abs(offset.y) > height) continue;
                boolean inside = switch (cue.geometry()) {
                    case AREA -> offset.horizontalDistanceSqr() <= range * range;
                    case FAN -> offset.horizontalDistanceSqr() <= range * range && (offset.horizontalDistanceSqr() < 0.01 || offset.multiply(1, 0, 1).normalize().dot(direction) >= Math.cos(Math.toRadians(Math.max(30, width) / 2.0)));
                    default -> {
                        double forward = offset.dot(direction);
                        double side = Math.abs(offset.x * direction.z - offset.z * direction.x);
                        yield forward >= -0.5 && forward <= range + entity.getBbWidth() * 0.5 && side <= width * 0.5 + entity.getBbWidth() * 0.5;
                    }
                };
                if (!inside) continue;
                Target enemyTarget = targets.target(entity);
                if (enemyTarget == null || enemyTarget.damageImmune || enemyTarget.life.dead) continue;
                int levelVal = Math.max(1, enemyTarget.originalLevel);
                double maxHp = Math.max(1, enemyTarget.health(levelVal));
                double defense = enemyTarget.defense(levelVal, context.power().magic());
                double rawDamage = context.power().baseAttack() * coeff * 1000.0 / (1000.0 + Math.max(0, defense));
                double fraction = Math.min(enemyTarget.damageCapacity(enemyTarget.life.q), rawDamage / maxHp);
                if (fraction <= 0) continue;
                enemyTarget.life.q = Math.max(0, enemyTarget.life.q - fraction);
                if (enemyTarget.life.q <= 0) enemyTarget.life.dead = true;
                enemyTarget.life.revision++;
                try {
                    journal.begin();
                    journal.put("target/" + entity.getUUID(), enemyTarget);
                    journal.put("life/" + entity.getUUID(), enemyTarget.life);
                    journal.finish();
                } catch (Exception failure) {
                    journal.abort();
                    continue;
                }
                entity.setData(CombatContentTypes.TARGET, JSON.toJson(enemyTarget));
                entity.setHealth((float) Math.max(0.001, entity.getMaxHealth() * enemyTarget.life.q));
                if (enemyTarget.life.dead) entity.kill(level);
            }
        }
    }
}
