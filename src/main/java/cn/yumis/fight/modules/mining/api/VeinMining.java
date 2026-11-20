package cn.yumis.fight.modules.mining.api;

import cn.yumis.fight.architecture.CommandRouter;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class VeinMining {
    public static final String ACTION = "veinMining";
    public static final int DEFAULT_LIMIT = 16;
    public static final int MAX_LIMIT = 256;
    private static final ThreadLocal<DropTarget> DROP_TARGET = new ThreadLocal<>();

    private record DropTarget(ServerPlayer player, ServerLevel level, BlockPos pos) {}

    private VeinMining() {}

    public interface MiningInput {
        void ydnf$queueMining(Plan plan);
    }

    public record Plan(ServerLevel level, BlockPos origin, int sequence, Block block, Set<BlockPos> extra) {}

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockDropsEvent event) -> {
            DropTarget target = DROP_TARGET.get();
            if (target == null || target.player() != event.getBreaker() || target.level() != event.getLevel()
                    || !target.pos().equals(event.getPos())) return;
            for (var drop : event.getDrops()) {
                drop.setPos(target.player().getX(), target.player().getY(), target.player().getZ());
            }
        });
    }

    public static void register(CommandRouter<ServerPlayer, JsonObject> commands) {
        commands.register("mining", ACTION, (player, payload) -> {
            var positions = payload.getAsJsonArray("positions");
            ServerLevel level = player.level();
            if (positions.size() < 2 || positions.size() > MAX_LIMIT
                    || !level.dimension().identifier().toString().equals(payload.get("dimension").getAsString())) return;

            BlockPos origin = BlockPos.of(positions.get(0).getAsLong());
            if (!level.hasChunkAt(origin) || !player.isWithinBlockInteractionRange(origin, 1.0)) return;
            BlockState state = level.getBlockState(origin);
            if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(payload.get("block").getAsString())) return;

            Set<BlockPos> extra = new HashSet<>();
            for (int i = 1; i < positions.size(); i++) extra.add(BlockPos.of(positions.get(i).getAsLong()));
            extra.remove(origin);
            ((MiningInput) player.gameMode).ydnf$queueMining(new Plan(level, origin,
                    payload.get("sequence").getAsInt(), state.getBlock(), Set.copyOf(extra)));
        });
    }

    public static boolean canMine(Player player, GameType mode) {
        return mode == GameType.SURVIVAL && player.isAlive() && player.getAbilities().mayBuild;
    }

    public static boolean canHarvest(Level level, Player player, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0
                && state.canHarvestBlock(level, pos, player);
    }

    public static List<BlockPos> select(Level level, Player player, BlockPos origin, int limit) {
        BlockState state = level.getBlockState(origin);
        if (!canHarvest(level, player, origin, state)) return List.of();
        return connected(origin, limit, pos -> {
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            BlockState neighbor = level.getBlockState(pos);
            return neighbor.is(state.getBlock()) && canHarvest(level, player, pos, neighbor);
        });
    }

    public static void breakConnected(ServerPlayer player, Plan plan, ItemStack tool) {
        ServerLevel level = plan.level();
        boolean hadTool = !tool.isEmpty();
        connected(plan.origin(), plan.extra().size() + 1, pos -> {
            if (!plan.extra().contains(pos) || player.level() != level
                    || !canMine(player, player.gameMode.getGameModeForPlayer())
                    || player.getMainHandItem() != tool || (hadTool && tool.isEmpty()) || !level.hasChunkAt(pos)
                    || !level.mayInteract(player, pos)) return false;

            BlockState state = level.getBlockState(pos);
            return state.is(plan.block()) && canHarvest(level, player, pos, state)
                    && breakAtFeet(player, pos, () -> player.gameMode.destroyBlock(pos))
                    && !level.getBlockState(pos).is(plan.block());
        });
    }

    public static boolean breakAtFeet(ServerPlayer player, BlockPos pos, BooleanSupplier destroy) {
        DropTarget previous = DROP_TARGET.get();
        DROP_TARGET.set(new DropTarget(player, player.level(), pos));
        try {
            return destroy.getAsBoolean();
        } finally {
            if (previous == null) DROP_TARGET.remove();
            else DROP_TARGET.set(previous);
        }
    }

    private static List<BlockPos> connected(BlockPos origin, int limit, Predicate<BlockPos> visit) {
        int cap = Math.clamp(limit, 1, MAX_LIMIT);
        List<BlockPos> blocks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        blocks.add(origin);
        seen.add(origin);
        for (int index = 0; index < blocks.size(); index++) {
            BlockPos current = blocks.get(index);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (blocks.size() == cap) return blocks;
                        BlockPos next = current.offset(x, y, z);
                        if (seen.add(next) && visit.test(next)) blocks.add(next);
                    }
                }
            }
        }
        return blocks;
    }
}
