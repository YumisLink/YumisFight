package cn.yumis.fight.modules.presentation.api.client;

import com.google.common.reflect.TypeToken;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/** Client-side lighting for held items and moving entities. */
public final class LocalLights {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static ToIntFunction<LivingEntity> entityLight = entity -> 0;
    private static volatile Map<Long, Integer> blockLight = Map.of();
    private static ClientLevel world;
    private static Map<BlockPos, Integer> previousSources = Map.of();
    private static long lastRefresh;

    private LocalLights() {}

    public static void register(IEventBus bus, IntSupplier heldLight, ToIntFunction<LivingEntity> entityLight) {
        LocalLights.entityLight = entityLight;
        boolean externalEntityLights = ModList.get().isLoaded("lambdynlights");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(heldLight.getAsInt(), externalEntityLights));
        bus.addListener((RegisterRenderStateModifiersEvent event) ->
            event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, (entity, state) -> {
                int emission = entityEmission(entity);
                if (emission > LightCoordsUtil.block(state.lightCoords)) {
                    state.lightCoords = LightCoordsUtil.pack(emission, LightCoordsUtil.sky(state.lightCoords));
                }
            }));
    }

    public static int entityEmission(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && !living.isInvisible()
            ? Math.clamp(entityLight.applyAsInt(living), 0, 15) : 0;
    }

    private static void tick(int heldLight, boolean externalEntityLights) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;
        var sources = new HashMap<BlockPos, Integer>();
        if (level != null && player != null) {
            if (player.isAlive() && heldLight > 0) {
                var position = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(.4)));
                sources.put(position, Math.min(heldLight, 15));
            }
            if (!externalEntityLights) {
                for (var entity : level.entitiesForRendering()) {
                    int emission = entityEmission(entity);
                    if (emission > 0) {
                        sources.merge(BlockPos.containing(entity.getLightProbePosition(1)), emission, Math::max);
                    }
                }
            }
        }
        update(level, sources);
    }

    private static void update(@Nullable ClientLevel level, Map<BlockPos, Integer> sources) {
        boolean sameWorld = world == level;
        long time = level == null ? 0 : level.getGameTime();
        if (sameWorld && previousSources.equals(sources)
            && (sources.isEmpty() || time == lastRefresh || time % 5 != 0)) return;

        var previous = sameWorld ? blockLight : Map.<Long, Integer>of();
        var updated = level == null ? Map.<Long, Integer>of() : propagate(level, sources);
        world = level;
        previousSources = Map.copyOf(sources);
        lastRefresh = time;
        if (!sameWorld || !previous.equals(updated)) {
            blockLight = Map.copyOf(updated);
            if (level != null) dirtySections(level, previous, updated);
        }
    }

    private static Map<Long, Integer> propagate(ClientLevel level, Map<BlockPos, Integer> sources) {
        var light = new HashMap<Long, Integer>();
        var pending = new ArrayDeque<BlockPos>();
        sources.forEach((position, strength) -> {
            if (level.hasChunkAt(position)) {
                light.put(position.asLong(), strength);
                pending.add(position);
            }
        });
        while (!pending.isEmpty()) {
            var position = pending.removeFirst();
            int current = light.get(position.asLong());
            if (current <= 1) continue;
            for (var direction : DIRECTIONS) {
                var neighbor = position.relative(direction);
                if (level.isOutsideBuildHeight(neighbor) || !level.hasChunkAt(neighbor)) continue;
                int next = current - Math.max(1, level.getBlockState(neighbor).getLightDampening());
                long key = neighbor.asLong();
                if (next > light.getOrDefault(key, 0)) {
                    light.put(key, next);
                    pending.add(neighbor);
                }
            }
        }
        return light;
    }

    private static void dirtySections(ClientLevel level, Map<Long, Integer> previous, Map<Long, Integer> updated) {
        var sections = new HashSet<Long>();
        LongConsumer mark = sections::add;
        previous.forEach((position, light) -> {
            if (!light.equals(updated.get(position))) SectionPos.aroundAndAtBlockPos(position, mark);
        });
        updated.forEach((position, light) -> {
            if (!previous.containsKey(position)) SectionPos.aroundAndAtBlockPos(position, mark);
        });
        for (long section : sections) {
            int x = SectionPos.x(section), y = SectionPos.y(section), z = SectionPos.z(section);
            level.setSectionRangeDirty(x, y, z, x, y, z);
        }
    }

    public static int block(int original, BlockPos position) {
        return Math.max(original, blockLight.getOrDefault(position.asLong(), 0));
    }

    public static int packed(int original, BlockPos position) {
        return LightCoordsUtil.pack(block(LightCoordsUtil.block(original), position), LightCoordsUtil.sky(original));
    }
}
