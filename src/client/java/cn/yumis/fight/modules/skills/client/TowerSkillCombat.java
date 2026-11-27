package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.combat.api.CombatTiming;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.TowerBattleState;
import cn.yumis.fight.modules.dungeons.api.TowerCardEffects;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.skills.api.TowerSkillTraits;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static cn.yumis.fight.modules.dungeons.api.TowerBattleCard.*;

/** The skill module adapts its existing client facts to the cards in the current run. */
public final class TowerSkillCombat {
    private static final List<TowerBattleCard> ELEMENTS = List.of(LIGHT, FIRE, ICE, DARK);

    private TowerSkillCombat() {}

    private static SkillsClient state() { return SkillsClient.current(); }
    private static SkillController combat() { return SkillController.INSTANCE; }

    private static TowerBattleState battle() {
        if (!state().ready() || state().own().dead || state().own().dungeonDefeated) return null;
        var battle = state().towerBattle();
        if (battle != null) battle.life(state().own().generation, state().resources());
        return battle;
    }

    static void installHooks() {
        SkillCastHooks.onRequest(SkillCastHooks.Stage.VALIDATE_REQUEST, req -> {
            if (FUNDAMENTALS.held(req.prepared) && awakening(req.skill.id)) {
                throw new IllegalArgumentException("回归基本功使本局无法使用觉醒技能");
            }
        });
        SkillCastHooks.onCast(SkillCastHooks.Stage.ON_CAST_COMPLETE, event -> finish(event.cast(), true));
        SkillCastHooks.onCast(SkillCastHooks.Stage.ON_CAST_ABANDON, event -> finish(event.cast(), false));
    }

    public static boolean awakening(String skill) {
        return JobCatalog.style(state().progress().jobId()).awakeningSkill().equals(skill);
    }

    public static boolean invulnerable() {
        var battle = battle();
        return battle != null && battle.invulnerable(ClientUi.now());
    }

    public static void tick(Stats stats, long now) {
        var battle = battle();
        if (battle == null || !state().combatAvailable()) return;
        var resources = state().resources();
        battle.combatTick(!CombatTiming.out(resources, now), now);
        long wall = state().serverNow();
        boolean save = false;
        if (battle.use(BRAIN, stats, wall)) {
            TowerBattleState.shield(resources, BRAIN, stats.get(Stat.MP) * BRAIN.ratio("shield"), now + BRAIN.millis("duration"));
            save = true;
        }
        if (battle.use(DIVINE, stats, wall)) {
            battle.buff(DIVINE, now);
            save = true;
        }
        if (MOUNTAIN.held(stats) && battle.mountainReady(now))
            TowerBattleState.shield(resources, MOUNTAIN, stats.get(Stat.HEALTH) * MOUNTAIN.ratio("shield"), 0);
        if (NATURE.held(stats) && battle.regenReady(now)) {
            double maximum = stats.get(Stat.MP);
            if (stats.modifiers.getOrDefault("manaRecoveryBlocked", 0d) <= 0)
                resources.mp = Math.min(maximum, resources.mp + maximum * NATURE.ratio("recovery"));
            heal(stats, stats.get(Stat.HEALTH) * NATURE.ratio("recovery"));
        }
        if (save) state().saveTowerBattle();
    }

    public static void basic(Stats stats) {
        if (battle() != null && PUNCH.held(stats)) reduceCooldowns(PUNCH.millis("reduction"), ClientUi.now());
    }

    public static void cast(SkillCast cast) {
        var battle = battle();
        if (battle == null) return;
        Stats stats = cast.stats;
        long now = ClientUi.now();
        cast.towerStart = Minecraft.getInstance().player.position();
        if (ARCHMAGE.held(stats) && cast.cooldownMillis > 0)
            reduceCooldowns(Math.round(cast.cooldownMillis * ARCHMAGE.ratio("reduction")), now);
        if (BRAVADO.held(stats)) battle.stack(BRAVADO, now);
        if (bound(HELP, stats, cast.skill)) battle.buff(HELP, now);
        if (bound(ADVANCE, stats, cast.skill)) battle.buff(ADVANCE, now);
        if (UNSTOPPABLE.held(stats)) battle.buff(UNSTOPPABLE, now);
        if (JUICE.held(stats)) heal(stats, stats.get(Stat.HEALTH) * JUICE.ratio("heal"));
        if (!cast.terminal && awakening(cast.skill.id) && battle.use(KILLING_TIME, stats, state().serverNow())) {
            state().resources().cooldowns.remove(cast.skill.id);
            state().saveTowerBattle();
        }
        if (TNT.held(stats) && battle.ready(TNT, state().serverNow())) {
            var near = nearby(cast.towerStart, TNT.value("radius"));
            var closest = near.stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(cast.towerStart))).orElse(null);
            if (closest != null && battle.use(TNT, stats, state().serverNow())) {
                explosion(closest.position().add(0,.6,0), true);
                combat().towerStrike(TNT, closest, stats, TNT.ratio("damage"), false, -1);
                state().saveTowerBattle();
            }
        }
        state().upload();
    }

    public static void moved(SkillCast cast, Vec3 previous) {
        if (cast.towerStart == null || !FLASH.held(cast.stats)) return;
        var player = Minecraft.getInstance().player;
        if (player.position().distanceToSqr(previous) > 1e-8) cast.towerDestination = player.position();
    }

    public static void finish(SkillCast cast, boolean completed) {
        if (!completed || battle() == null || cast.towerStart == null || !FLASH.held(cast.stats)
            || !TowerSkillTraits.displaces(cast.skill.id, cast.stats)) return;
        Vec3 destination = cast.towerDestination;
        cast.towerStart = null;
        if (destination == null) return;
        explosion(destination.add(0,.4,0), false);
        for (var target : nearby(destination, FLASH.value("radius")))
            combat().towerStrike(FLASH, target, cast.stats, FLASH.ratio("damage"), false, -1);
    }

    public static Stats elements(Stats source) {
        Stats result = source;
        for (var card : ELEMENTS) if (card.held(source)) {
            if (source == result) result = source.copy();
            int element = card.count("element");
            var kind = ElementAccumulation.Kind.values()[element];
            for (int from = -1; from < 4; from++) if (from != element)
                ElementAccumulation.addConversion(result, from, kind, card.ratio("buildup"));
        }
        return result;
    }

    public static double hitHealing(Stats stats, SkillCast cast, SkillDefinition skill, double damage, boolean critical) {
        var battle = battle();
        if (battle == null || damage <= 0) return 0;
        double points = critical && SIPHON.held(stats) ? SIPHON.ratio("heal") * damage : 0;
        if (cast != null && bound(LEECH, stats, skill)) {
            double cap = skill.cooldown(cast.parameters.rank()) / 100 * combat().snapshot().get(Stat.HEALTH);
            points += battle.siphon(cast.id, damage * LEECH.ratio("heal") * TowerCardEffects.healingFactor(stats), cap);
        }
        return points;
    }

    public static void hit(LivingEntity entity, Target target, Stats stats, SkillCast cast, SkillDefinition skill,
                           boolean basic, double damage, double before) {
        var battle = battle();
        if (battle == null || damage <= 0) return;
        long now = ClientUi.now();
        if (HASTE.held(stats)) battle.stack(HASTE, now);
        if (SLAP.held(stats)) battle.stack(SLAP, now);
        if (bound(ADAPTIVE, stats, skill)) battle.buff(ADAPTIVE, now);
        if (bound(DOUBLE_GUARD, stats, skill)) shield(DOUBLE_GUARD, damage, now);
        if (ESCAPE.held(stats)) {
            battle.buff(ESCAPE, now);
            shield(ESCAPE, damage, now);
        }
        if (cast != null && bound(MERCY, stats, skill)) {
            var total = SkillPreview.resolve(state().progress(), cast.stats, skill, cast.parameters, cast.equipment).total();
            battle.prepareMercy((total.minimum() + total.maximum()) * .5 * MERCY.ratio("damage"));
        }
        if (skill != null && battle.use(EVIL, stats, state().serverNow())) {
            battle.grow(EVIL);
            state().saveTowerBattle();
        }
        if (basic) {
            double mercy = battle.takeMercy();
            if (mercy > 0) combat().towerFixed(MERCY, entity, stats, mercy);
            if (INFUSE.held(stats)) {
                boolean magic = stats.get(Stat.MAGICAL_ATTACK) > stats.get(Stat.PHYSICAL_ATTACK);
                Stats copy = stats.copy();
                copy.values.put(magic ? Stat.INTELLIGENCE : Stat.STRENGTH, 0d);
                combat().towerStrike(INFUSE, entity, copy, INFUSE.ratio("damage"), magic, -1);
            }
        }
        if (bound(BANG, stats, skill)) for (var other : nearby(entity.position(), BANG.value("radius"))) {
            if (other != entity) combat().towerFixed(BANG, other, stats, damage * BANG.ratio("damage"));
        }
        for (var card : ELEMENTS) if (card.held(stats)) {
            int element = card.count("element");
            var kind = ElementAccumulation.Kind.values()[element];
            if (target.life.elemental.active(kind, target.life, state().serverNow()))
                combat().towerStrike(card, entity, stats, card.ratio("damage"), true, element);
        }
        if (EXECUTE.held(stats) && before < EXECUTE.ratio(target.boss() ? "boss" : "normal")) {
            double life = state().predicted(target.life);
            int level = target.level(state().progress().level, state().encounterSync());
            double needed = target.damageCapacity(life) * target.health(level);
            if (needed > 0) combat().towerFixed(EXECUTE, entity, stats, needed);
        }
    }

    public static double incoming(Stats stats) {
        var battle = battle();
        return battle == null ? 1 : battle.incoming(stats, ClientUi.now());
    }

    public static Stats damageSnapshot(Stats stats) {
        if (!SLAP.held(stats)) return stats;
        var battle = battle();
        double increase = battle == null ? 0 : battle.slapIncrease(ClientUi.now());
        if (increase == stats.modifiers.getOrDefault(TowerCardEffects.SLAP_DAMAGE, 0d)) return stats;
        var copy = stats.copy();
        copy.modifiers.put(TowerCardEffects.SLAP_DAMAGE, increase);
        return copy;
    }

    public static void hurt(Stats stats, UUID attacker, double damage) {
        var battle = battle();
        if (battle == null || attacker == null || damage <= 0) return;
        var mc = Minecraft.getInstance();
        if (!(mc.level.getEntity(attacker) instanceof LivingEntity enemy) || !SkillController.canTarget(enemy)) return;
        battle.hurt(stats, ClientUi.now());
        if (REFLECT.held(stats)) {
            combat().towerFixed(REFLECT, enemy, stats, damage * REFLECT.ratio("damage"));
            heal(stats, damage * REFLECT.ratio("heal"));
        }
        if (PORCUPINE.held(stats)) for (var target : nearby(mc.player.position(), PORCUPINE.value("radius")))
            combat().towerFixed(PORCUPINE, target, stats, damage * PORCUPINE.ratio("damage"));
    }

    public static void killed(Target target, Vec3 location) {
        var battle = state().towerBattle();
        if (battle == null || !target.rewardsEligible()) return;
        Stats stats = combat().snapshot();
        boolean alive = !state().own().dead && !state().own().dungeonDefeated;
        if (!battle.killed(stats, ClientUi.now(), alive)) return;
        state().saveTowerBattle();
        if (alive && CLEANSE.held(stats)) {
            int level = target.level(state().progress().level, state().encounterSync());
            double damage = target.health(level) * CLEANSE.ratio("damage");
            explosion(location.add(0,.5,0), false);
            for (var entity : nearby(location, CLEANSE.value("radius")))
                if (!entity.getUUID().equals(target.id)) combat().towerFixed(CLEANSE, entity, stats, damage);
        }
    }

    private static void shield(TowerBattleCard card, double base, long now) {
        TowerBattleState.shield(state().resources(), card, base * card.ratio("shield"), now + card.millis("duration"));
    }

    private static boolean bound(TowerBattleCard card, Stats stats, SkillDefinition skill) {
        return skill != null && card.held(stats) && state().towerSkill(card).equals(skill.id);
    }

    private static void reduceCooldowns(long amount, long now) {
        if (amount > 0) state().resources().cooldowns.replaceAll((id, until) ->
            until > now && SkillCatalog.contains(id) ? Math.max(now, until - amount) : until);
    }

    private static void heal(Stats stats, double points) {
        if (points <= 0 || state().own().dead || state().predicted(state().own()) >= 1) return;
        double maximum = combat().snapshot().get(Stat.HEALTH);
        state().report(state().own(), points * TowerCardEffects.healingFactor(stats) / maximum, true, maximum, 0x8ef5ad, false, null);
    }

    private static List<LivingEntity> nearby(Vec3 center, double radius) {
        return combat().targets(new AABB(center, center).inflate(radius)).stream()
            .filter(target -> target.distanceToSqr(center) <= radius * radius).toList();
    }

    private static void explosion(Vec3 pos, boolean tnt) {
        var level = Minecraft.getInstance().level;
        level.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 0, 0, 0);
        if (!tnt) return;
        var bits = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TNT.defaultBlockState());
        var random = level.getRandom();
        for (int i = 0; i < 10; i++) level.addParticle(bits, pos.x, pos.y, pos.z,
            random.nextGaussian() * .16, random.nextDouble() * .25, random.nextGaussian() * .16);
    }
}
