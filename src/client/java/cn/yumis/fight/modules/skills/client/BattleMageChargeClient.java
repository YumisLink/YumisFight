package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Owning-client hold-to-charge state machine for Battle Mage Palm Blast & Dragon Strike.
 * Supports instant tap release (u~0) or hold-to-full-charge; MP & cooldown are paid on press
 * and never refunded on cancel (per 保留游戏机制).
 */
public final class BattleMageChargeClient {
    private static final Identifier MOVE_MODIFIER = Identifier.parse("yumis_dungeon_and_fight:battle_mage_charge_move");
    private static final Set<String> DAMAGE_KEYS = Set.of("damage", "finisher");
    private static final Set<String> SCALE_KEYS = Set.of("radius", "blastRadius");

    private static SkillCast cast;
    private static Runnable release;
    private static int boundSlot = -1;
    private static String session = "", job = "", instance = "";
    private static long generation, start, chargeMillis, fullAt, autoReleaseAt;

    private BattleMageChargeClient() {}

    public static boolean active() { return cast != null; }
    public static SkillCast cast() { return cast; }
    public static SkillDefinition skill() { return cast == null ? null : cast.skill; }

    public static double chargeProgress(long now) {
        if (cast == null) return 0.0;
        return Math.clamp((now - start) / (double) Math.max(1L, chargeMillis), 0.0, 1.0);
    }

    public static boolean isFull(long now) {
        return cast != null && now >= fullAt;
    }

    public static double holdRemainingRatio(long now) {
        if (cast == null) return 0.0;
        long total = Math.max(1L, autoReleaseAt - fullAt);
        return Math.clamp((autoReleaseAt - now) / (double) total, 0.0, 1.0);
    }

    public static double holdRemainingSeconds(long now) {
        if (cast == null) return 0.0;
        return Math.max(0.0, (autoReleaseAt - now) / 1000.0);
    }

    public static double chargeRemainingSeconds(long now) {
        if (cast == null) return 0.0;
        return Math.max(0.0, (fullAt - now) / 1000.0);
    }

    public static double previewDashDistance(long now) {
        if (cast == null) return 0.0;
        double base = cast.parameters.get("dashDistance", 4.5);
        return base * BattleMageRules.chargeDashMultiplier(cast.parameters, chargeProgress(now));
    }

    public static void begin(SkillCast source, int slot, Runnable completion) {
        clear();
        var state = SkillsClient.current();
        cast = source;
        cast.phase = cn.yumis.fight.modules.combat.api.ActionPhase.CHANTING;
        release = completion;
        boundSlot = slot;
        session = state.session();
        generation = state.own().generation;
        job = state.progress().jobId();
        instance = state.own().instance;

        start = ClientUi.now();
        double baseCharge = source.parameters.get("chargeSeconds", 0.45);
        double effSeconds = BattleMageRules.effectiveChargeSeconds(baseCharge, source.stats);
        chargeMillis = Math.max(50L, Math.round(effSeconds * 1000.0));
        long maxHoldMillis = Math.max(200L, Math.round(source.parameters.get("maxHoldSeconds", 1.5) * 1000.0));
        fullAt = start + chargeMillis;
        autoReleaseAt = fullAt + maxHoldMillis;

        var player = Minecraft.getInstance().player;
        if (player != null) {
            double ratio = source.parameters.get("chargeMoveSpeedRatio", 0.65);
            applyMovementModifier(player, ratio);
            String pose = BattleMageSkills.DRAGON_STRIKE.equals(source.skill.id)
                ? "dragon_strike_charge" : "palm_blast_charge";
            PlayerPoseChannel.loop(source, pose, chargeMillis + maxHoldMillis, source.direction, true);
        }
    }

    /** Pressing same slot absorbs key repeat; pressing another martial/cancel skill cancels charge. */
    public static boolean press(int slot) {
        if (!active()) return false;
        String binding = SkillsClient.current().progress().slots[slot];
        if (cast.skill.id.equals(binding)) return true;
        if (BattleMageSkills.CHASER_LAUNCHER.equals(binding)) return false;
        cancel();
        return false;
    }

    public static void released(int baseSlot) {
        if (!active()) return;
        if (boundSlot >= 0 && boundSlot % 8 == baseSlot) {
            releaseNow();
        }
    }

    public static void cancel() {
        if (!active()) return;
        var source = cast;
        clear();
        PlayerPoseChannel.cancel(source);
        CareerClient.INSTANCE.cancel(source);
    }

    public static void reset() { clear(); }

    public static void clear() {
        var player = Minecraft.getInstance().player;
        if (player != null) removeMovementModifier(player);
        cast = null;
        release = null;
        boundSlot = -1;
    }

    public static void tick() {
        if (!active()) return;
        var mc = Minecraft.getInstance();
        var state = SkillsClient.current();
        var player = mc.player;
        var hand = player == null ? null : EquipmentItems.view(player.getMainHandItem());
        if (player == null || mc.level == null || !state.ready() || state.own().dead || player.isDeadOrDying()
            || !state.session().equals(session) || state.own().generation != generation
            || !state.own().instance.equals(instance) || !state.progress().jobId().equals(job)
            || !mc.level.dimension().identifier().toString().equals(cast.dimension)
            || hand == null || !hand.instance().toString().equals(cast.weapon)
            || CombatContentTypes.controlled(player) || SealBossState.isFrozen()) {
            cancel();
            return;
        }
        if (ClientUi.now() >= autoReleaseAt) {
            releaseNow();
        }
    }

    private static void releaseNow() {
        if (!active()) return;
        var player = Minecraft.getInstance().player;
        var source = cast;
        var completion = release;
        long now = ClientUi.now();
        double u = chargeProgress(now);
        clear();
        if (player == null || completion == null) {
            if (source != null) {
                PlayerPoseChannel.cancel(source);
                CareerClient.INSTANCE.cancel(source);
            }
            return;
        }
        source.chargeProgress = u;
        double dmgMul = BattleMageRules.chargeDamageMultiplier(source.parameters, u);
        double dashMul = BattleMageRules.chargeDashMultiplier(source.parameters, u);
        double scaleMul = BattleMageRules.chargeScaleMultiplier(source.parameters, u);
        var values = new LinkedHashMap<>(source.parameters.values());
        for (String key : DAMAGE_KEYS) {
            if (values.containsKey(key)) values.put(key, values.get(key) * dmgMul);
        }
        if (values.containsKey("dashDistance")) {
            values.put("dashDistance", values.get("dashDistance") * dashMul);
        }
        for (String key : SCALE_KEYS) {
            if (values.containsKey(key) && values.get(key) > 0) values.put(key, values.get(key) * scaleMul);
        }
        source.parameters = new SkillParameters.Snapshot(source.skill, source.parameters.rank(), values);
        source.start = source.stageStart = now;
        source.origin = player.position();
        Vec3 look = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        source.direction = look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : look.normalize();
        source.center = source.origin;
        completion.run();
    }

    private static void applyMovementModifier(LivingEntity actor, double ratio) {
        var attr = actor.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(MOVE_MODIFIER);
        double delta = Math.max(-0.9, ratio - 1.0);
        if (Math.abs(delta) > 1e-4) {
            attr.addTransientModifier(new AttributeModifier(MOVE_MODIFIER, delta, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeMovementModifier(LivingEntity actor) {
        var attr = actor.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(MOVE_MODIFIER);
    }
}
