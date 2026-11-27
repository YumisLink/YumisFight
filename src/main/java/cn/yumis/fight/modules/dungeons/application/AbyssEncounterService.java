package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.combat.api.ActionBlueprint;
import cn.yumis.fight.modules.combat.api.ActionPhase;
import cn.yumis.fight.modules.combat.api.ActionRegistry;
import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.StatusLeases;
import cn.yumis.fight.modules.dungeons.api.AbyssAttack;
import cn.yumis.fight.modules.dungeons.api.AbyssParty;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.dungeons.api.DungeonContentTypes;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Condition;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Cue;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Form;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Kit;
import cn.yumis.fight.modules.dungeons.content.AbyssCareers;
import cn.yumis.fight.modules.dungeons.content.AbyssCareers.Career;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static cn.yumis.fight.modules.dungeons.api.AbyssAttack.Effect.*;
import static cn.yumis.fight.modules.dungeons.api.AbyssAttack.Shape.*;
import static cn.yumis.fight.modules.dungeons.api.AbyssParty.Enemy.*;

/** An optional encounter owned by the dungeon instance, outside its normal clear roster. */
final class AbyssEncounterService {
    private static final Gson JSON = new Gson();
    static final String SPAWN_SLOT = "ydnf_abyss_spawn_slot";
    /** Global gap between champion skill casts; individual skills keep their catalog cooldowns. */
    static final long CAST_GAP = 500;
    private enum Stage { FIRST, TO_SECOND, SECOND, THIRD, CHAMPIONS, FINISH }

    private static final class Run {
        final Random random;
        final List<Vec3> candidates;
        final Vec3 replacement;
        final AbyssParty.Arena arena;
        final List<Actor> wave = new ArrayList<>();
        final List<Queued> queued = new ArrayList<>();
        final Map<UUID, LiveAttack> attacks = new LinkedHashMap<>();
        boolean reached, replaced, invited, finished, active;
        UUID pillar;
        int teamSize, level, firstSpawns;
        double thirdStart;
        Stage stage = Stage.FIRST;
        List<Career> choices = List.of();

        Run(Random random, List<Vec3> candidates, Vec3 replacement, AbyssParty.Arena arena) {
            this.random = random;
            this.candidates = candidates;
            this.replacement = replacement;
            this.arena = arena;
        }
    }

    private static final class Actor {
        final UUID id;
        final AbyssParty.Enemy kind;
        final Vec3 home;
        final Career career;
        final Map<String, Long> ready = new HashMap<>();
        final EnumMap<Form, Long> forms = new EnumMap<>(Form.class);
        final StatusLeases leases = new StatusLeases();
        ActionPhase phase = ActionPhase.IDLE;
        int nextSkill, sunMoonHeart;
        long busy, nextBasic, nextBone, nextColumn, nextLeap, approachUntil;
        boolean awakened;
        Motion motion;

        Actor(DungeonEnemy enemy, AbyssParty.Enemy kind, Career career, long now) {
            this.id = enemy.getUUID();
            this.kind = kind;
            this.home = enemy.position();
            this.career = career;
            nextBone = now;
            nextColumn = now + 5000;
            nextLeap = now + 12000;
        }

        boolean form(Form form, long now) { return forms.getOrDefault(form, 0L) > now; }
    }

    private record LiveAttack(AbyssAttack attack, Target source) {}
    private record Site(Vec3 replacement, AbyssParty.Arena arena) {}
    private record Queued(Actor actor, Kit kit, Cue cue, UUID cast, Vec3 origin, Vec3 aim, long started, long begin,
                          double coefficient, int sequence, boolean finale, long duration) {}
    private record Motion(Vec3 from, Vec3 to, long start, long end, double lift, boolean plunge) {}

    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Map<UUID, Run> runs = new HashMap<>();

    AbyssEncounterService(DungeonService owner, Journal journal, DungeonPorts.Players players, DungeonPorts.Targets targets) {
        this.owner = owner;
        this.journal = journal;
        this.players = players;
        this.targets = targets;
    }

    void plan(Dungeon dungeon, List<Vec3> candidates) {
        if (!AbyssParty.eligible(dungeon)) return;
        if (dungeon.abyssActivation != null) {
            projectActivation(dungeon);
            return;
        }
        runs.computeIfAbsent(dungeon.id, id -> {
            var random = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
            var slots = candidates.stream().distinct().toList();
            if (random.nextDouble() >= AbyssParty.CHANCE) return new Run(random, slots, null, null);
            var safe = safeSites(dungeon, slots);
            if (safe.isEmpty()) return new Run(random, slots, null, null);
            var site = safe.get(random.nextInt(safe.size()));
            return new Run(random, slots, site.replacement(), site.arena());
        });
    }

    boolean hasEncounter(Dungeon dungeon) {
        Run run = runs.get(dungeon.id);
        return dungeon.abyssActivation != null || run != null && run.arena != null;
    }

    boolean pendingEncounter(Dungeon dungeon) {
        return hasEncounter(dungeon) && acceptsChallengers(dungeon);
    }

    boolean acceptsChallengers(Dungeon dungeon) {
        Run run = runs.get(dungeon.id);
        return run == null || !run.finished;
    }

    AbyssParty.Activation prepareChallenge(Dungeon dungeon, Vec3 entrance) {
        Run run = runs.get(dungeon.id);
        if (run == null) throw new IllegalArgumentException("副本还在准备，稍后再开启深渊挑战");
        Site site = run.arena != null ? new Site(run.replacement, run.arena)
            : safeSites(dungeon, run.candidates).stream()
                .min(Comparator.comparingDouble(candidate -> candidate.arena().anchor().distanceToSqr(entrance)))
                .orElseThrow(() -> new IllegalArgumentException("本场副本没有可刷新深渊柱的小怪位"));
        long slot = BlockPos.containing(site.replacement()).asLong();
        UUID replaced = null;
        for (UUID id : dungeon.enemies) {
            if (owner.level().getEntity(id) instanceof DungeonEnemy enemy && enemy.isAlive()
                && enemy.getPersistentData().getLongOr(SPAWN_SLOT, Long.MIN_VALUE) == slot) {
                replaced = id;
                break;
            }
        }
        return new AbyssParty.Activation(site.replacement(), site.arena(), replaced);
    }

    void projectActivation(Dungeon dungeon) {
        var activation = dungeon.abyssActivation;
        if (activation == null) return;
        Run current = runs.get(dungeon.id);
        if (current != null && current.invited) return;
        UUID replaced = activation.replacedEnemy();
        if (replaced != null && owner.level().getEntity(replaced) instanceof DungeonEnemy enemy) {
            owner.cancelBoss(replaced);
            enemy.discard();
        }
        var run = new Run(new Random(dungeon.id.getLeastSignificantBits()), List.of(), activation.replacement(), activation.arena());
        run.invited = true;
        run.reached = true;
        run.replaced = replaced != null;
        runs.put(dungeon.id, run);
    }

    boolean replaces(Dungeon dungeon, Vec3 position) {
        Run run = runs.get(dungeon.id);
        if (run == null || run.replaced || !position.equals(run.replacement)) return false;
        run.replaced = true;
        run.reached = true;
        return true;
    }

    void rollbackPalaceSpawn(Dungeon dungeon) {
        Run run = runs.get(dungeon.id);
        if (run == null || !run.replaced || dungeon.palaceRooms.contains(run.arena.sector())) return;
        run.replaced = false;
        run.reached = run.invited;
    }

    void tick(Dungeon dungeon, List<ServerPlayer> present, long now) throws Exception {
        projectActivation(dungeon);
        Run run = runs.get(dungeon.id);
        if (run == null || !run.reached || run.finished) return;
        if (run.pillar == null) {
            if (!run.invited && dungeon.dungeon.equals("castle_palace") && !dungeon.palaceRooms.contains(run.arena.sector())) return;
            run.teamSize = Math.max(1, dungeon.roster.size());
            run.level = dungeon.waystone == null ? DungeonCatalog.current().dungeon(dungeon.dungeon).level() : dungeon.waystone.monsterLevel;
            run.pillar = spawn(dungeon, run, PILLAR, run.arena.anchor(), null).getUUID();
        }
        if (!(owner.level().getEntity(run.pillar) instanceof DungeonEnemy pillar)) return;
        if (!pillar.position().equals(run.arena.anchor())) pillar.setPos(run.arena.anchor());
        var nearby = present.stream().filter(p -> eligible(p, run.arena)).toList();
        boolean fighting = !nearby.isEmpty();
        if (fighting != run.active) updateDormancy(run, pillar, fighting, now);
        if (!fighting) return;

        advance(dungeon, run, pillar, now);
        run.attacks.values().removeIf(attack -> attack.attack().end() <= now);
        for (Queued cue : List.copyOf(run.queued)) {
            if (cue.begin() > now) continue;
            run.queued.remove(cue);
            if (owner.level().getEntity(cue.actor().id) instanceof DungeonEnemy enemy && enemy.isAlive())
                releaseCue(run, cue, enemy, now);
        }
        for (Actor actor : run.wave) {
            if (owner.level().getEntity(actor.id) instanceof DungeonEnemy enemy && enemy.isAlive()) {
                ServerPlayer focus = nearby.stream().min(Comparator.comparingDouble(enemy::distanceToSqr)).orElseThrow();
                tickActor(run, actor, enemy, focus, now);
            }
        }
    }

    private void advance(Dungeon dungeon, Run run, DungeonEnemy pillar, long now) throws Exception {
        Target target = targets.target(pillar);
        double hp = target.life.q;
        switch (run.stage) {
            case FIRST -> {
                while (run.firstSpawns < AbyssParty.SKELETON_THRESHOLDS.size()
                    && reached(hp, AbyssParty.SKELETON_THRESHOLDS.get(run.firstSpawns))) {
                    addActor(dungeon, run, SKELETON, null, now);
                    run.firstSpawns++;
                }
                if (reached(hp, AbyssParty.FIRST_FLOOR) && cleared(run)) {
                    floor(pillar, target, AbyssParty.SECOND_FLOOR);
                    run.stage = Stage.TO_SECOND;
                    run.wave.clear();
                } else pillar.guarded(reached(hp, AbyssParty.FIRST_FLOOR));
            }
            case TO_SECOND -> {
                if (!reached(hp, AbyssParty.SECOND_FLOOR)) return;
                pillar.guarded(true);
                if (run.wave.isEmpty()) addActor(dungeon, run, GIANT, null, now);
                while (run.wave.size() <= AbyssParty.SECOND_WAVE) addActor(dungeon, run, SKELETON, null, now);
                run.stage = Stage.SECOND;
            }
            case SECOND -> {
                if (cleared(run)) {
                    floor(pillar, target, 1 / target.health(target.originalLevel));
                    run.thirdStart = hp;
                    run.stage = Stage.THIRD;
                    run.wave.clear();
                }
            }
            case THIRD -> {
                if (hp >= run.thirdStart) return;
                if (run.choices.isEmpty()) run.choices = AbyssCareers.draw(run.random, AbyssParty.CHAMPIONS);
                while (run.wave.size() < run.choices.size())
                    addActor(dungeon, run, CHAMPION, run.choices.get(run.wave.size()), now);
                run.stage = Stage.CHAMPIONS;
            }
            case CHAMPIONS -> {
                if (cleared(run)) {
                    floor(pillar, target, 0);
                    run.stage = Stage.FINISH;
                    run.wave.clear();
                } else pillar.guarded(reached(hp, target.healthFloor));
            }
            case FINISH -> {}
        }
    }

    private boolean cleared(Run run) {
        return run.wave.stream().allMatch(actor -> owner.defeated(actor.id));
    }

    private static boolean reached(double q, double floor) { return q <= floor + 1e-12; }

    private void floor(DungeonEnemy pillar, Target target, double floor) throws Exception {
        target.healthFloor = floor;
        publishTarget(pillar, target);
        pillar.guarded(false);
    }

    private void publishTarget(DungeonEnemy enemy, Target target) throws Exception {
        target.life.revision++;
        journal.put("target/" + enemy.getUUID(), target);
        projectTarget(enemy, target);
    }

    private void projectTarget(DungeonEnemy enemy, Target target) {
        enemy.setData(CombatContentTypes.TARGET, JSON.toJson(target));
        owner.broadcast(enemy.instance(), "context", target);
    }

    private DungeonEnemy spawn(Dungeon dungeon, Run run, AbyssParty.Enemy kind, Vec3 position, Career career) throws Exception {
        var enemy = new DungeonEnemy(DungeonContentTypes.ENEMIES.get(kind.id).get(), owner.level());
        var tags = enemy.getPersistentData();
        tags.putString("ydnf_instance", dungeon.id.toString());
        tags.putInt("ydnf_level", run.level);
        tags.putInt("ydnf_abyss_party_size", run.teamSize);
        enemy.scripted();
        enemy.setPos(position);
        if (kind == PILLAR) {
            enemy.setNoGravity(true);
            enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        } else if (kind == GIANT) {
            var speed = enemy.getAttribute(Attributes.MOVEMENT_SPEED);
            speed.setBaseValue(speed.getBaseValue() * .6);
            enemy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_BARS));
        } else if (kind == SKELETON) enemy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BONE));
        if (career != null) {
            enemy.setItemSlot(EquipmentSlot.MAINHAND, weaponFor(career.job()));
            enemy.setCustomName(Component.literal(JobCatalog.require(career.job()).title()).withColor(career.color()));
        } else {
            enemy.setCustomName(Component.translatable("entity.yumis_dungeon_and_fight." + kind.id));
        }
        enemy.setDropChance(EquipmentSlot.MAINHAND, 0);
        if (!owner.level().addFreshEntity(enemy)) throw new IllegalStateException("深渊派对敌人生成失败：" + kind.id);
        try {
            publishTarget(enemy, targets.target(enemy));
        } catch (Exception failure) {
            enemy.discard();
            throw failure;
        }
        return enemy;
    }

    private static ItemStack weaponFor(String job) {
        return EquipmentVisualItems.weaponModel("common_" + AbyssChampionKits.weapon(job).toLowerCase(Locale.ROOT));
    }

    private void addActor(Dungeon dungeon, Run run, AbyssParty.Enemy kind, Career career, long now) throws Exception {
        double pillarClearance = (PILLAR.width + kind.width) / Math.sqrt(2);
        Vec3 position = around(run, run.arena.anchor(), Math.max(2.5, pillarClearance), 7.5, kind.width, kind.height, true);
        var actor = new Actor(spawn(dungeon, run, kind, position, career), kind, career, now);
        actor.busy = now + (career == null ? 0 : 1000 + 1650L * run.wave.size());
        run.wave.add(actor);
    }

    private void updateDormancy(Run run, DungeonEnemy pillar, boolean awake, long now) {
        run.active = awake;
        if (awake) return;
        clearAttacks(run, pillar.instance());
        for (Actor actor : run.wave) {
            if (!(owner.level().getEntity(actor.id) instanceof DungeonEnemy enemy) || !enemy.isAlive()) continue;
            if (actor.motion != null) enemy.setPos(actor.home);
            actor.motion = null;
            enemy.setNoGravity(false);
            stop(enemy);
            enemy.play("", 0);
            actor.busy = now;
        }
    }

    private void tickActor(Run run, Actor actor, DungeonEnemy enemy, ServerPlayer focus, long now) throws Exception {
        if (actor.kind == SKELETON && now >= actor.nextBone && !CombatContentTypes.controlled(enemy)) {
            Vec3 point = around(run, focus.position(), 0, 3, .45, 1.8, false);
            if (point != null) {
                emit(run, enemy, point, Vec3.ZERO, CIRCLE, BONE, 3, 0, 0, now, 2000, 30000, .35, true, 3, true);
                actor.nextBone = now + 15000;
            }
        }
        if (CombatContentTypes.controlled(enemy)) {
            cancel(actor.id);
            stop(enemy);
            return;
        }
        if (actor.motion != null) {
            if (stepMotion(actor, enemy, now)) return;
        }
        if (!run.arena.contains(enemy.position())) {
            enemy.setPos(actor.home);
            stop(enemy);
        }
        if (now < actor.busy) return;

        var delta = focus.position().subtract(enemy.position());
        double distance = delta.horizontalDistance();
        Vec3 direction = heading(delta);
        enemy.getLookControl().setLookAt(focus);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        enemy.setYRot(yaw);
        enemy.yBodyRot = yaw;
        if (actor.kind == GIANT && distance <= 20 && giant(run, actor, enemy, focus, now)) return;
        if (actor.career != null && champion(run, actor, enemy, focus, distance, now)) return;

        boolean ranged = actor.career != null && (actor.career.ranged() || actor.form(Form.KALLA, now));
        double melee = actor.kind == GIANT ? 5 : actor.kind == SKELETON ? 2.5 : 3.2;
        double reach = ranged ? 10 : melee;
        if (distance > reach || !enemy.hasLineOfSight(focus)) {
            if (enemy.tickCount % 8 == 0 || enemy.getNavigation().isDone()) {
                enemy.getNavigation().moveTo(focus, actor.form(Form.FRENZY, now) ? 1.1 : 1);
            }
            return;
        }
        stop(enemy);
        if (now < actor.nextBasic) return;
        boolean giant = actor.kind == GIANT, skeleton = actor.kind == SKELETON;
        boolean magic = actor.career != null && (ranged || JobCatalog.style(actor.career.job()).magic());
        int element = actor.career != null && actor.career.job().equals(JobCatalog.SOUL_BENDER) ? 3 : -1;
        var abyssRules = cn.yumis.fight.modules.dungeons.api.EnemyBalanceRules.current().abyss;
        double coefficient = giant ? abyssRules.giantBasicCoefficient() : skeleton ? abyssRules.skeletonBasicCoefficient() : abyssRules.championBasicCoefficient();
        long warning = giant ? 1250 : skeleton ? 700 : 750;
        var timing = targets.target(enemy).actionTiming(now);
        long castWarning = timing.windup(warning), castRecovery = timing.recovery(450);
        actor.nextBasic = now + (giant ? 3600 : 2450);
        actor.busy = now + castWarning + castRecovery;
        enemy.play("abyss_basic", castWarning + castRecovery);
        boolean doubleHit = actor.form(Form.FRENZY, now);
        boolean champion = actor.career != null;
        emit(run, enemy, enemy.position(), direction, giant ? CIRCLE : ranged ? LINE : FAN,
            ranged ? ORB : SLASH, reach, ranged ? 1.35 : 1.05, 0, now, warning, 200,
            doubleHit ? coefficient * .55 : coefficient, magic, element, !champion);
        if (doubleHit) emit(run, enemy, enemy.position(), direction, FAN, SLASH, reach, 1.05, 0, now + 300, warning,
            200, coefficient * .45, magic, element, !champion);
        if (champion && !ranged) {
            owner.broadcast(enemy.instance(), "visual", swing(enemy, direction, actor.career.color(), now));
            if (doubleHit) owner.broadcast(enemy.instance(), "visual", swing(enemy, direction, actor.career.color(), now + 300));
        }
    }

    private boolean giant(Run run, Actor actor, DungeonEnemy enemy, ServerPlayer focus, long now) {
        if (now < actor.nextLeap && now < actor.nextColumn) return false;
        Vec3 ground = ground(run.arena, focus.position(), GIANT.width, GIANT.height);
        boolean leap = now >= actor.nextLeap;
        if (leap && ground == null) ground = around(run, focus.position(), .1, 3, GIANT.width, GIANT.height, false);
        if (ground == null && now >= actor.nextColumn) {
            leap = false;
            ground = ground(run.arena, focus.position(), .6, 2);
        }
        if (ground == null) return false;
        stop(enemy);
        var timing = targets.target(enemy).actionTiming(now);
        actor.busy = now + timing.duration(6000);
        if (leap) {
            actor.nextLeap = now + 35000;
            enemy.play("abyss_leap", timing.duration(5500));
            double lift = Math.min(clearance(enemy.position(), GIANT.width, GIANT.height, 50),
                clearance(ground, GIANT.width, GIANT.height, 50));
            actor.motion = new Motion(enemy.position(), ground, now, now + timing.duration(5000), lift, true);
            emit(run, enemy, ground, Vec3.ZERO, CIRCLE, LANDING, 8, 0, 0, now, 5000, 375, 2.5, false, -1, true);
            for (int i = 0; i < 8; i++) {
                Vec3 point = around(run, ground, 1.5, 9, .65, 1.8, false);
                if (point == null) point = ground;
                emit(run, enemy, point, Vec3.ZERO, CIRCLE, COLUMN, 1, 0, clearance(point, .65, 1.8, 8),
                    now + i * timing.duration(475), 900, 220, .25, false, -1, true);
            }
        } else {
            actor.nextColumn = now + 25000;
            enemy.play("abyss_column", timing.duration(5500));
            emit(run, enemy, ground, Vec3.ZERO, CIRCLE, COLUMN, 8, 0, clearance(ground, 2.2, 3.5, 20),
                now, 5000, 400, 2.5, false, -1, true);
        }
        return true;
    }

    private boolean champion(Run run, Actor actor, DungeonEnemy enemy, ServerPlayer focus, double distance, long now) throws Exception {
        if (!enemy.hasLineOfSight(focus)) return false;
        List<Kit> kits = AbyssChampionKits.kits(actor.career.job());
        Target stats = targets.target(enemy);
        if (actor.career.job().equals(JobCatalog.BERSERKER)) {
            double defense = actor.form(Form.FRENZY, now) ? -.15 : 0;
            if (stats.encounterDefenseIncrease != defense) {
                stats.encounterDefenseIncrease = defense;
                publishTarget(enemy, stats);
            }
        }
        for (int i = 0; i < kits.size(); i++) {
            int index = (actor.nextSkill + i) % kits.size();
            Kit kit = kits.get(index);
            if (now < actor.ready.getOrDefault(kit.id(), 0L)) continue;
            switch (kit.condition()) {
                case AWAKENING -> { if (!actor.awakened && stats.life.q > .70) continue; }
                case SUMMON_ACTIVE -> { if (!actor.form(Form.SUMMON, now)) continue; }
                case SUN_HEART -> { if (actor.sunMoonHeart != 1) continue; }
                case MOON_HEART -> { if (actor.sunMoonHeart != 2) continue; }
                case ALWAYS -> {}
            }
            if (distance > kit.reach()) {
                if (actor.approachUntil == 0) actor.approachUntil = now + 2200;
                if (now < actor.approachUntil) {
                    if (enemy.tickCount % 8 == 0 || enemy.getNavigation().isDone()) enemy.getNavigation().moveTo(focus, 1);
                    return true;
                }
                actor.nextSkill = (index + 1) % kits.size();
                actor.approachUntil = 0;
                return false;
            }
            actor.awakened |= kit.condition() == Condition.AWAKENING;
            actor.approachUntil = 0;
            actor.nextSkill = (index + 1) % kits.size();
            cast(run, actor, enemy, focus, kit, now);
            return true;
        }
        return false;
    }

    private void cast(Run run, Actor actor, DungeonEnemy enemy, ServerPlayer focus, Kit kit, long now) {
        long cooldown = actor.form(Form.MOON_REFLUX, now) && kit.magic() ? kit.cooldown() * 4 / 5 : kit.cooldown();
        actor.ready.put(kit.id(), now + cooldown);
        if (kit.form() != Form.NONE) actor.forms.put(kit.form(), now + kit.formTime());
        if (kit.form() == Form.SUN) actor.sunMoonHeart = 1;
        else if (kit.form() == Form.MOON || kit.id().equals("paradox")) actor.sunMoonHeart = 2;
        if (actor.career.job().equals(JobCatalog.ORACLE)) oracleWeapon(enemy, kit.id());
        var timing = targets.target(enemy).actionTiming(now);
        long maxDelay = kit.cues().stream().mapToLong(Cue::delay).max().orElse(0);
        long castTime = timing.duration(maxDelay + 400);
        actor.busy = now + CAST_GAP;
        actor.phase = ActionPhase.ACTIVE;
        stop(enemy);
        enemy.play("abyss_skill/" + kit.id(), castTime);
        Vec3 origin = enemy.position(), aim = focus.position();
        double budget = kit.coefficient() * kit.enhance() * cn.yumis.fight.modules.dungeons.api.EnemyBalanceRules.current().abyss.championSkillEnhanceMultiplier();
        UUID cast = UUID.randomUUID();
        long duration = timing.duration(maxDelay + 1500);
        ActionBlueprint shared = ActionRegistry.find(kit.kind());
        if (shared != null) {
            for (ActionStatus status : shared.activeStatuses()) {
                actor.leases.acquire(cast.toString(), status, now + castTime);
            }
        }
        owner.broadcast(enemy.instance(), "visual", visual(enemy, cast, kit, "START", 0, now, now, origin,
            heading(aim.subtract(origin)), duration, motionFor(kit, now)));
        for (int i = 0; i < kit.cues().size(); i++) {
            Cue cue = kit.cues().get(i);
            var pending = new Queued(actor, kit, cue, cast, origin, aim, now, now + timing.duration(cue.delay()),
                budget * cue.share(), i + 1, i == kit.cues().size() - 1, duration);
            if (cue.delay() == 0) releaseCue(run, pending, enemy, now);
            else run.queued.add(pending);
        }
    }

    /** Champion casts ride the same client visual pipeline as player casts: identical message shape, catalog kind and color. */
    private static JsonObject visual(DungeonEnemy enemy, UUID cast, Kit kit, String phase, int sequence,
                                     long started, long at, Vec3 point, Vec3 direction, long duration, JsonObject motion) {
        var j = new JsonObject();
        j.addProperty("caster", enemy.getUUID().toString());
        j.addProperty("session", enemy.getUUID().toString());
        j.addProperty("generation", 1);
        j.addProperty("id", cast.toString());
        j.addProperty("kind", kit.kind());
        j.addProperty("phase", phase);
        j.addProperty("sequence", sequence);
        j.addProperty("seed", cast.hashCode());
        j.addProperty("startedAt", started);
        j.addProperty("at", at);
        j.addProperty("duration", duration);
        j.addProperty("instance", enemy.instance());
        j.addProperty("x", point.x);
        j.addProperty("y", point.y);
        j.addProperty("z", point.z);
        j.addProperty("px", point.x);
        j.addProperty("py", point.y);
        j.addProperty("pz", point.z);
        j.addProperty("dx", direction.x);
        j.addProperty("dy", direction.y);
        j.addProperty("dz", direction.z);
        j.addProperty("color", kit.color());
        if (motion != null) j.add("motion", motion);
        return j;
    }

    /** The visual anchor follows the same dash or leap the encounter applies to the champion body. */
    private static JsonObject motionFor(Kit kit, long now) {
        JsonObject motion = null;
        if (kit.motion() == AbyssChampionKits.Motion.DASH) motion = visualMotion(8.5, 450, 0);
        else if (kit.motion() == AbyssChampionKits.Motion.LEAP) motion = visualMotion(8, 600, 2);
        if (motion != null) motion.addProperty("at", now);
        return motion;
    }

    private static JsonObject visualMotion(double range, long millis, double arcHeight) {
        var motion = new JsonObject();
        motion.addProperty("velocity", millis <= 0 ? 0 : range * 1000 / millis);
        motion.addProperty("range", range);
        motion.addProperty("millis", millis);
        motion.addProperty("ground", false);
        motion.addProperty("released", true);
        if (arcHeight > 0) motion.addProperty("arcHeight", arcHeight);
        return motion;
    }

    /** Champion melee basics flash the same ATTACK crescent and swing sound the player client draws for its own swings. */
    private static JsonObject swing(DungeonEnemy enemy, Vec3 direction, int color, long at) {
        var j = new JsonObject();
        j.addProperty("caster", enemy.getUUID().toString());
        j.addProperty("session", enemy.getUUID().toString());
        j.addProperty("generation", 1);
        j.addProperty("id", UUID.randomUUID().toString());
        j.addProperty("kind", "ATTACK");
        j.addProperty("phase", "START");
        j.addProperty("sequence", 1);
        j.addProperty("seed", at);
        j.addProperty("startedAt", at);
        j.addProperty("at", at);
        j.addProperty("duration", 650);
        j.addProperty("instance", enemy.instance());
        Vec3 point = enemy.position();
        j.addProperty("x", point.x);
        j.addProperty("y", point.y);
        j.addProperty("z", point.z);
        j.addProperty("px", point.x);
        j.addProperty("py", point.y);
        j.addProperty("pz", point.z);
        j.addProperty("dx", direction.x);
        j.addProperty("dy", direction.y);
        j.addProperty("dz", direction.z);
        j.addProperty("color", color);
        j.addProperty("sweep", true);
        return j;
    }

    /** The champion oracle switches weapons per skill exactly like the career's weapon commands. */
    private static void oracleWeapon(DungeonEnemy enemy, String skill) {
        String weapon = switch (skill) {
            case "oracle_down" -> "common_blunt";
            case "oracle_thrust" -> "common_katana";
            case "oracle_sweep" -> "common_greatsword";
            case "oracle_smile" -> "common_shortsword";
            case "oracle_rift", "furioso_replica" -> "common_lightsaber";
            default -> "common_katana";
        };
        enemy.setItemSlot(EquipmentSlot.MAINHAND, EquipmentVisualItems.weaponModel(weapon));
    }

    private void releaseCue(Run run, Queued pending, DungeonEnemy enemy, long now) {
        Cue cue = pending.cue();
        Kit kit = pending.kit();
        Vec3 base = pending.origin();
        Vec3 aim = pending.aim();
        Vec3 direction = heading(aim.subtract(base));
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        Vec3 position = (cue.atTarget() ? aim : base)
            .add(direction.scale(cue.forward())).add(side.scale(cue.side()));
        Vec3 ground = ground(run.arena, position, .25, 1);
        if (ground == null) ground = ground(run.arena, cue.atTarget() ? aim : base, .25, 1);
        if (ground == null) return;
        direction = direction.yRot((float) cue.turn());
        double elevation = cue.effect() == COLUMN ? clearance(ground, .65, 1.6, 6) : 0;
        boolean travels = cue.shape() == LINE && (cue.effect() == WAVE || cue.effect() == ORB || cue.effect() == ICE);
        long active = travels ? Math.max(400, Math.round(cue.range() * 55)) : 300;
        emit(run, enemy, ground, direction, cue.shape(), cue.effect(), cue.range(), cue.width(), elevation, now,
            0, active, pending.coefficient(), kit.magic(), kit.element(), false);
        owner.broadcast(enemy.instance(), "visual", visual(enemy, pending.cast(), kit,
            pending.finale() ? "FINISH" : pending.sequence() == 1 ? "RELEASE" : "HIT",
            pending.sequence(), pending.started(), now, ground, direction, pending.duration(), null));
        boolean jump = kit.motion() == AbyssChampionKits.Motion.LEAP;
        boolean dash = kit.motion() == AbyssChampionKits.Motion.DASH && cue.shape() == LINE && cue.effect() != WAVE;
        boolean hunt = kit.id().equals("the_hunt") && cue.shape() == LINE;
        if (cue.delay() == 0 && (jump || dash || hunt)) {
            Vec3 desired = dash || hunt ? ground.add(direction.scale(Math.min(cue.range(), 8.5))) : aim;
            Vec3 destination = motionEnd(run.arena, enemy, desired);
            double height = jump ? clearance(enemy.position(), enemy.getBbWidth(), enemy.getBbHeight(), 2.8) : 0;
            long travel = Math.max(200, Math.round(enemy.position().distanceTo(destination) * 45));
            pending.actor().motion = new Motion(enemy.position(), destination, now, now + travel, height, false);
        }
    }

    private Vec3 motionEnd(AbyssParty.Arena arena, DungeonEnemy enemy, Vec3 desired) {
        Vec3 start = enemy.position();
        for (double fraction : new double[]{1, .75, .5, .25}) {
            Vec3 end = ground(arena, start.lerp(desired, fraction), enemy.getBbWidth(), enemy.getBbHeight());
            if (end == null) continue;
            boolean clear = true;
            int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end)));
            for (int i = 1; i <= steps && clear; i++)
                clear = open(start.lerp(end, (double) i / steps), enemy.getBbWidth(), enemy.getBbHeight());
            if (clear) return end;
        }
        return start;
    }

    private boolean stepMotion(Actor actor, DungeonEnemy enemy, long now) {
        Motion motion = actor.motion;
        if (now < motion.start()) return true;
        stop(enemy);
        double progress = Math.clamp((double) (now - motion.start()) / (motion.end() - motion.start()), 0, 1);
        if (progress >= 1) {
            enemy.setPos(motion.to());
            actor.leases.releaseOwner("abyss/motion");
            enemy.setNoGravity(actor.leases.has(ActionStatus.NO_GRAVITY, now));
            actor.motion = null;
            return false;
        }
        actor.leases.acquire("abyss/motion", ActionStatus.NO_GRAVITY, motion.end());
        enemy.setNoGravity(true);
        Vec3 position;
        if (motion.plunge()) {
            if (progress <= .19) position = motion.from().add(0, motion.lift() * progress / .19, 0);
            else {
                double descent = (progress - .19) / .81;
                position = motion.to().add(0, motion.lift() * (1 - descent * descent), 0);
            }
        } else {
            position = motion.from().lerp(motion.to(), progress).add(0, motion.lift() * Math.sin(progress * Math.PI), 0);
        }
        if (open(position, enemy.getBbWidth(), enemy.getBbHeight())) enemy.setPos(position);
        enemy.fallDistance = 0;
        return true;
    }

    private void emit(Run run, DungeonEnemy enemy, Vec3 point, Vec3 direction, AbyssAttack.Shape shape, AbyssAttack.Effect effect,
                      double range, double width, double height, long warningAt, long warning, long active,
                      double damage, boolean magic, int element, boolean telegraph) {
        var timing = targets.target(enemy).actionTiming(warningAt);
        long release = warningAt + timing.windup(warning);
        var attack = new AbyssAttack(UUID.randomUUID(), enemy.getUUID(), run.arena, point, direction, shape, effect, range,
            width, height, warningAt, release, release + timing.active(active), damage, magic, element, telegraph);
        var live = new LiveAttack(attack, targets.target(enemy));
        run.attacks.put(attack.id(), live);
        owner.broadcast(enemy.instance(), "abyssAttack", packet(live));
    }

    private static JsonObject packet(LiveAttack live) {
        var packet = new JsonObject();
        packet.add("attack", JSON.toJsonTree(live.attack()));
        packet.add("source", JSON.toJsonTree(live.source()));
        return packet;
    }

    void track(Dungeon dungeon, ServerPlayer player) {
        Run run = runs.get(dungeon.id);
        if (run != null) for (LiveAttack live : run.attacks.values()) Network.send(player, "abyssAttack", packet(live));
    }

    void died(Target target) {
        cancel(target.id);
        if (target.abyss != PILLAR) return;
        Run run = runs.get(UUID.fromString(target.instance));
        if (run != null) {
            run.finished = true;
            clearAttacks(run, target.instance);
        }
    }

    void cancel(UUID actor) {
        for (var entry : runs.entrySet()) {
            var run = entry.getValue();
            run.queued.removeIf(cue -> cue.actor().id.equals(actor));
            run.attacks.values().removeIf(live -> {
                if (!live.attack().source().equals(actor) || live.attack().effect() == BONE) return false;
                broadcastCancel(entry.getKey().toString(), live.attack().id());
                return true;
            });
            for (Actor state : run.wave) {
                if (!state.id.equals(actor)) continue;
                state.phase = ActionPhase.ABANDONED;
                state.leases.clear();
                if (state.motion == null) continue;
                if (owner.level().getEntity(actor) instanceof DungeonEnemy enemy) {
                    enemy.setNoGravity(false);
                    enemy.setPos(groundOrHome(run, enemy, state.home));
                    stop(enemy);
                }
                state.motion = null;
            }
        }
    }

    private Vec3 groundOrHome(Run run, DungeonEnemy enemy, Vec3 home) {
        Vec3 point = ground(run.arena, enemy.position(), enemy.getBbWidth(), enemy.getBbHeight());
        return point == null ? home : point;
    }

    void close(Dungeon dungeon) {
        Run run = runs.remove(dungeon.id);
        if (run != null) clearAttacks(run, dungeon.id.toString());
    }

    private void clearAttacks(Run run, String instance) {
        for (UUID id : run.attacks.keySet()) broadcastCancel(instance, id);
        run.attacks.clear();
        run.queued.clear();
    }

    private void broadcastCancel(String instance, UUID id) {
        owner.broadcast(instance, "abyssCancel", Map.of("id", id.toString()));
    }

    private boolean eligible(ServerPlayer player, AbyssParty.Arena arena) {
        var life = players.session(player).life;
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().invulnerable
            && player.level() == owner.level() && !life.dead && !life.dungeonDefeated && arena.contains(player.position());
    }

    private Site site(Dungeon dungeon, Vec3 replacement) {
        var provisional = AbyssParty.Arena.at(dungeon, replacement);
        if (provisional.sector() < 0) return null;
        Vec3 center = ground(provisional, replacement, PILLAR.width, PILLAR.height);
        if (center == null) return null;
        var arena = AbyssParty.Arena.at(dungeon, center);
        int paths = 0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            var point = arena.anchor().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
            if (ground(arena, point, GIANT.width, GIANT.height) != null) paths++;
        }
        return paths >= 3 ? new Site(replacement, arena) : null;
    }

    private List<Site> safeSites(Dungeon dungeon, List<Vec3> candidates) {
        return candidates.stream().map(p -> site(dungeon, p)).filter(Objects::nonNull).toList();
    }

    private Vec3 around(Run run, Vec3 center, double minimum, double maximum, double width, double height, boolean forEnemy) {
        Vec3 fallback = null;
        for (int i = 0; i < 36; i++) {
            double angle = run.random.nextDouble() * Math.TAU;
            double distance = Math.sqrt(run.random.nextDouble(minimum * minimum, maximum * maximum));
            Vec3 point = ground(run.arena, center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), width, height);
            if (point == null) continue;
            fallback = point;
            if (!forEnemy || run.wave.stream().noneMatch(actor -> actor.home.distanceToSqr(point) < 1.4)) return point;
        }
        if (fallback != null) return fallback;
        Vec3 exact = ground(run.arena, center, width, height);
        return forEnemy && exact == null ? run.arena.anchor() : exact;
    }

    private Vec3 ground(AbyssParty.Arena arena, Vec3 point, double width, double height) {
        ServerLevel world = owner.level();
        BlockPos column = BlockPos.containing(point);
        for (int rise : new int[]{0, -1, 1, -2, 2, -3, 3, -4, -5}) {
            BlockPos pos = column.above(rise);
            Vec3 feet = new Vec3(point.x, pos.getY(), point.z);
            if (!arena.contains(feet) || !world.getFluidState(pos).isEmpty()) continue;
            var below = pos.below();
            if (world.getBlockState(below).isFaceSturdy(world, below, Direction.UP) && open(feet, width, height))
                return feet;
        }
        return null;
    }

    private double clearance(Vec3 feet, double width, double height, double maximum) {
        double lift = 0;
        for (double next = .5; next <= maximum; next += .5) {
            if (!open(feet.add(0, next, 0), width, height)) break;
            lift = next;
        }
        return lift;
    }

    private boolean open(Vec3 feet, double width, double height) {
        double radius = width * .49;
        AABB box = new AABB(feet.x - radius, feet.y + .015, feet.z - radius,
            feet.x + radius, feet.y + height, feet.z + radius);
        return !owner.level().getBlockCollisions(null, box).iterator().hasNext();
    }

    private static Vec3 heading(Vec3 offset) {
        Vec3 direction = offset.multiply(1, 0, 1).normalize();
        return direction.lengthSqr() < .0001 ? new Vec3(0, 0, 1) : direction;
    }

    private static void stop(DungeonEnemy enemy) {
        enemy.getNavigation().stop();
        enemy.setDeltaMovement(Vec3.ZERO);
    }
}
