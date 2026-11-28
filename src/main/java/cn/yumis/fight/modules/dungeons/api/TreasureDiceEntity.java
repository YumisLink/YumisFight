package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Interactive floating die entity spawned in pairs after a Treasure Hunt clear (Regions 3-5)
 * for the High-or-Low chest multiplier gamble (1..9 distinct faces, up to 64x or 4096x on cross-region streak).
 */
public final class TreasureDiceEntity extends Entity {
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIRST_VALUE = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MULTIPLIER = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROUNDS = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LAST_RESULT = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PHASE = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> INSTANCE = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> STREAK = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MUST_WIN = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> ROLL_UNTIL = SynchedEntityData.defineId(TreasureDiceEntity.class, EntityDataSerializers.LONG);

    public TreasureDiceEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SLOT, 1);
        b.define(FACE, 0);
        b.define(FIRST_VALUE, 0);
        b.define(MULTIPLIER, 1);
        b.define(ROUNDS, 0);
        b.define(LAST_RESULT, 0);
        b.define(PHASE, "");
        b.define(OWNER, "");
        b.define(INSTANCE, "");
        b.define(STREAK, false);
        b.define(MUST_WIN, false);
        b.define(ROLL_UNTIL, 0L);
    }

    public int slot() { return entityData.get(SLOT); }
    public int face() { return entityData.get(FACE); }
    public int firstValue() { return entityData.get(FIRST_VALUE); }
    public int multiplier() { return entityData.get(MULTIPLIER); }
    public int rounds() { return entityData.get(ROUNDS); }
    public int lastResult() { return entityData.get(LAST_RESULT); }
    public String phase() { return entityData.get(PHASE); }
    public String ownerId() { return entityData.get(OWNER); }
    public String instanceId() { return entityData.get(INSTANCE); }
    public boolean streak() { return entityData.get(STREAK); }
    public boolean mustWinOne() { return entityData.get(MUST_WIN); }
    public long rollUntil() { return entityData.get(ROLL_UNTIL); }

    public void syncState(int slot, int face, int firstValue, int multiplier, int rounds, int lastResult,
                          String phase, String ownerId, String instanceId, boolean streak, boolean mustWinOne, long rollUntil) {
        entityData.set(SLOT, slot);
        entityData.set(FACE, face);
        entityData.set(FIRST_VALUE, firstValue);
        entityData.set(MULTIPLIER, Math.max(1, multiplier));
        entityData.set(ROUNDS, Math.max(0, rounds));
        entityData.set(LAST_RESULT, lastResult);
        entityData.set(PHASE, phase == null ? "" : phase);
        entityData.set(OWNER, ownerId == null ? "" : ownerId);
        entityData.set(INSTANCE, instanceId == null ? "" : instanceId);
        entityData.set(STREAK, streak);
        entityData.set(MUST_WIN, mustWinOne);
        entityData.set(ROLL_UNTIL, rollUntil);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, 0, 0);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
