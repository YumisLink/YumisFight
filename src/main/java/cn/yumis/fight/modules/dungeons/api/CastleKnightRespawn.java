package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A vacant guard post lives in its native entity chunk until it can be filled. */
final class CastleKnightRespawn extends Marker {
    private static final long RESPAWN_DELAY = 5 * 60 * 20L;
    private static final double PLAYER_CLEARANCE = 6;
    private static final ResourceKey<Structure> CASTLE = ResourceKey.create(Registries.STRUCTURE,
        Identifier.fromNamespaceAndPath(ItemData.NS, "wild_buildings/victoria_castle"));

    private long readyAt = Long.MAX_VALUE;
    private UUID reinforcement = UUID.randomUUID();

    CastleKnightRespawn(EntityType<?> type, Level level) {
        super(type, level);
    }

    static boolean isNaturalCastlePost(ServerLevel level, BlockPos post) {
        return level.hasChunkAt(post) && level.structureManager().getStructureWithPieceAt(post, structure -> structure.is(CASTLE)).isValid();
    }

    static void schedule(ServerLevel level, BlockPos post, float yaw) {
        String key = "victoria-knight-post/" + level.dimension().identifier() + "/" + post.asLong();
        UUID vacancy = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        if (level.getEntity(vacancy) != null) return;

        var marker = new CastleKnightRespawn(DungeonContentTypes.CASTLE_KNIGHT_RESPAWN.get(), level);
        marker.setUUID(vacancy);
        marker.snapTo(Vec3.atBottomCenterOf(post), yaw, 0);
        marker.readyAt = level.getGameTime() + RESPAWN_DELAY;
        level.addFreshEntity(marker);
    }

    @Override public void tick() {
        if (!(level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now < readyAt || (now + getId()) % 20 != 0 || level.getDifficulty() == Difficulty.PEACEFUL) return;

        BlockPos post = blockPosition();
        if (!level.areEntitiesLoaded(ChunkPos.pack(post))) return;
        if (level.getEntity(reinforcement) != null || hasGuard(level, post)) {
            discard();
            return;
        }
        Vec3 spawn = position();
        if (level.hasNearbyAlivePlayer(spawn.x, spawn.y, spawn.z, PLAYER_CLEARANCE) || !canSpawn(level, post, spawn)) return;

        var knight = DungeonContentTypes.CASTLE_KNIGHT.get().create(level, EntitySpawnReason.TRIGGERED);
        if (knight == null) return;
        knight.setUUID(reinforcement);
        knight.snapTo(spawn, getYRot(), 0);
        knight.prepareGuard(post);
        knight.rememberKnightFacing(getYRot());
        if (level.addFreshEntity(knight)) discard();
    }

    private static boolean hasGuard(ServerLevel level, BlockPos post) {
        AABB patrol = new AABB(post).inflate(CastleEnemy.KNIGHT_HOME_RADIUS);
        return !level.getEntitiesOfClass(CastleEnemy.class, patrol, knight -> knight.isAlive() && !knight.boss()
            && knight.hasHome() && knight.getHomePosition().equals(post)).isEmpty();
    }

    private static boolean canSpawn(ServerLevel level, BlockPos post, Vec3 spawn) {
        BlockPos floor = post.below();
        if (!level.getWorldBorder().isWithinBounds(post) || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
            || !level.getFluidState(post).isEmpty() || !level.getFluidState(post.above()).isEmpty()) return false;

        AABB body = DungeonContentTypes.CASTLE_KNIGHT.get().getDimensions().makeBoundingBox(spawn);
        return level.noCollision(body) && level.getEntitiesOfClass(LivingEntity.class, body, Entity::isAlive).isEmpty();
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("ready_at", readyAt);
        output.store("reinforcement", UUIDUtil.CODEC, reinforcement);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        readyAt = input.getLongOr("ready_at", Long.MAX_VALUE);
        reinforcement = input.read("reinforcement", UUIDUtil.CODEC).orElse(reinforcement);
    }
}
