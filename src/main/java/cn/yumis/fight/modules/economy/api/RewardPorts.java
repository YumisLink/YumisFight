package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.Dungeon;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Reward calculation and post-commit notification dependencies. */
public final class RewardPorts {
    private RewardPorts(){}
    public interface Players {
        UUID session(ServerPlayer player);
        Progress progress(ServerPlayer player);
        void progress(ServerPlayer player,Progress value);
        Stats stats(ServerPlayer player);
        void profile(ServerPlayer player);
        void message(ServerPlayer player,String text);
    }
    public interface Encounters {
        boolean participant(ServerPlayer player,String instance);
        Dungeon byId(String instance);
    }
    public interface Balance {
        RewardSettings settings();
        long gold(ServerPlayer player);
        long revision(ServerPlayer player);
        void notifyGain(ServerPlayer player,long amount,long balance,long revision);
    }
}
