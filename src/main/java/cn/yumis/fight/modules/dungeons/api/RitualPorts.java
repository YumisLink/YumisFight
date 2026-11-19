package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonElement;
import java.util.Map;

/** Lifecycle wiring from bootstrap; the ritual service receives altar energy and reward capabilities. */
public interface RitualPorts {
    /** Summon energy stays industry-owned: the altar's own storage first, its covering beacon grid second. */
    public interface AltarPower {
        String identity(ServerLevel level,BlockPos altar);
        boolean usable(ServerPlayer player,BlockPos altar);
        /** Commits the debit and the pending run together. False leaves every stored point untouched. */
        boolean commitSummon(ServerLevel level,BlockPos altar,long cost,String runId,Map<String,JsonElement> writes) throws Exception;
    }
    public record ClearCommit(Map<String,JsonElement> writes,Runnable publish) {
        public static final ClearCommit NONE = new ClearCommit(Map.of(), () -> {});
    }

    /** Produces the durable clear's associated facts; publication follows the same commit. */
    @FunctionalInterface
    public interface Rewards {
        ClearCommit prepare(RitualClear clear,String material);
    }
}
