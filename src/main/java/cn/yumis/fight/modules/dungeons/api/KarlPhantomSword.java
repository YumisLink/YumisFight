package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The server owns the blade's flight and walls. Players check their own swept contact. */
public final class KarlPhantomSword extends ThrowableItemProjectile {
    private static final int FLIGHT_TICKS = 18;
    private static final float SPEED = .8f;
    private static final double CONTACT_MARGIN = .18;
    static final double RANGE = SPEED * FLIGHT_TICKS;

    KarlPhantomSword(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    static void launch(ServerLevel level, CastleEnemy owner, Vec3 origin, Vec3 direction) {
        var sword = new KarlPhantomSword(DungeonContentTypes.KARL_PHANTOM_SWORD.get(), level);
        var blade = new ItemStack(sword.getDefaultItem());
        blade.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        sword.setItem(blade);
        sword.setOwner(owner);
        sword.setPos(origin);
        var c = EnemyBalanceRules.current().castle;
        sword.shoot(direction.x, direction.y, direction.z, (float) c.phantomSwordSpeed(), 0);
        level.addFreshEntity(sword);
    }

    @Override protected Item getDefaultItem() { return Items.IRON_SWORD; }
    @Override protected double getDefaultGravity() { return 0; }
    @Override protected float getAirDrag() { return 1; }
    @Override protected boolean canHitEntity(Entity entity) { return false; }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }

    @Override public void tick() {
        int flightTicks = EnemyBalanceRules.current().castle.phantomSwordFlightTicks();
        if (level() instanceof ServerLevel && (tickCount > flightTicks || getOwner() == null || !getOwner().isAlive())) {
            discard();
            return;
        }
        super.tick();
        if (level().isClientSide() && !isRemoved()) {
            level().addParticle(ParticleTypes.WITCH, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        if (level() instanceof ServerLevel level) {
            Vec3 point = hit.getLocation();
            level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 7, .12, .12, .12, 0);
        }
        discard();
    }

    public boolean crosses(AABB playerBox) {
        Vec3 from = new Vec3(xo, yo, zo);
        AABB reach = playerBox.inflate(CONTACT_MARGIN);
        Vec3 touch = reach.contains(from) ? from : reach.clip(from, position()).orElse(null);
        return touch != null && level().clip(new ClipContext(touch, playerBox.getCenter(), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
}
