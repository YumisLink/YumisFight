package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** The armored defenders and the boss of Victoria Castle. */
public final class CastleEnemy extends DungeonEnemy {
    static final int KARL_HOME_RADIUS = 10;
    static final int KNIGHT_HOME_RADIUS = 12;
    private static final String POST_YAW = "ydnf_victoria_post_yaw";
    private final boolean karl;
    private final @Nullable PhantomSwordGoal swordGoal;

    CastleEnemy(EntityType<? extends Zombie> type, Level level, boolean karl) {
        super(type, level);
        this.karl = karl;
        swordGoal = karl && level instanceof ServerLevel ? new PhantomSwordGoal() : null;
        if (swordGoal != null) goalSelector.addGoal(1, swordGoal);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return DungeonEnemy.createAttributes().add(Attributes.FOLLOW_RANGE, 14);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, .75));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) ->
            isWithinHome(target.blockPosition()) && (!hasHome() || Math.abs(target.getY() - getHomePosition().getY()) < 4)));
    }

    @Override public int dungeonLevel() { return karl ? 15 : 10; }
    @Override public boolean boss() { return karl; }
    @Override public boolean fixedLevel() { return true; }
    @Override protected boolean convertsInWater() { return false; }

    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                             EntitySpawnReason reason, @Nullable SpawnGroupData group) {
        prepareGuard(blockPosition());
        if (!karl && reason == EntitySpawnReason.STRUCTURE) rememberKnightFacing(getYRot());
        return group;
    }

    void prepareGuard(BlockPos home) {
        setPersistenceRequired();
        setHomeTo(home, karl ? KARL_HOME_RADIUS : KNIGHT_HOME_RADIUS);
        equipGuardGear();
    }

    /** Applies the castle loadout for both spawned guards and client previews. */
    public void equipGuardGear() {
        equip(EquipmentSlot.MAINHAND, Items.IRON_SWORD);
        equip(EquipmentSlot.HEAD, karl ? Items.GOLDEN_HELMET : Items.IRON_HELMET);
        equip(EquipmentSlot.CHEST, karl ? Items.NETHERITE_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE);
        equip(EquipmentSlot.LEGS, Items.CHAINMAIL_LEGGINGS);
        equip(EquipmentSlot.FEET, Items.IRON_BOOTS);
    }

    void rememberKnightFacing(float yaw) {
        getPersistentData().putFloat(POST_YAW, yaw);
    }

    @Override protected void handleKillingBlow() {
        super.handleKillingBlow();
        if (karl || !hasHome() || !(level() instanceof ServerLevel server)) return;

        BlockPos post = getHomePosition();
        var data = getPersistentData();
        if (data.contains(POST_YAW) || CastleKnightRespawn.isNaturalCastlePost(server, post)) {
            CastleKnightRespawn.schedule(server, post, data.getFloatOr(POST_YAW, getYRot()));
        }
    }

    private void equip(EquipmentSlot slot, Item item) {
        setItemSlot(slot, new ItemStack(item));
        setDropChance(slot, 0);
    }

    @Override public void tick() {
        if (swordGoal != null && (isNoAi() || CombatContentTypes.controlled(this))) interruptPhantomSword();
        super.tick();
    }

    void interruptPhantomSword() {
        if (swordGoal != null && swordGoal.aim != null) swordGoal.stop();
    }

    private final class PhantomSwordGoal extends Goal {
        private static final int WINDUP = 24;
        private static final int COOLDOWN = 100;
        private static final double CAST_RANGE = 12;
        private static final DustParticleOptions WARNING = new DustParticleOptions(0xAD79FF, .8f);
        private long releaseTick;
        private long readyTick;
        private @Nullable Vec3 aim;

        private PhantomSwordGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() {
            var target = getTarget();
            return level().getGameTime() >= readyTick && isWithinHome() && !CombatContentTypes.controlled(CastleEnemy.this)
                && target != null && target.isAlive() && distanceToSqr(target) <= CAST_RANGE * CAST_RANGE
                && getSensing().hasLineOfSight(target);
        }

        @Override public boolean canContinueToUse() { return aim != null && isWithinHome(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() {
            var target = getTarget();
            if (target == null) return;
            aim = target.getBoundingBox().getCenter();
            releaseTick = level().getGameTime() + WINDUP;
            readyTick = releaseTick + COOLDOWN;
            getNavigation().stop();
            setAggressive(true);
            playSound(SoundEvents.EVOKER_PREPARE_ATTACK, .8f, 1.25f);
        }

        @Override public void tick() {
            if (aim == null || !(level() instanceof ServerLevel level)) return;
            getLookControl().setLookAt(aim.x, aim.y, aim.z, 30, 30);
            Vec3 origin = position().add(0, getBbHeight() * .65, 0);
            Vec3 direction = aim.subtract(origin).normalize();
            long remaining = releaseTick - level.getGameTime();
            if (remaining <= 0) {
                swing(InteractionHand.MAIN_HAND);
                KarlPhantomSword.launch(level, CastleEnemy.this, origin, direction);
                playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1, .75f);
                aim = null;
            } else if (remaining % 6 == 0) {
                Vec3 end = origin.add(direction.scale(KarlPhantomSword.RANGE));
                var wall = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CastleEnemy.this));
                double distance = origin.distanceTo(wall.getLocation());
                for (double step = .3; step < distance; step += 1.25) {
                    Vec3 point = origin.add(direction.scale(step));
                    level.sendParticles(WARNING, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                }
            }
        }

        @Override public void stop() {
            aim = null;
            setAggressive(false);
        }
    }
}
