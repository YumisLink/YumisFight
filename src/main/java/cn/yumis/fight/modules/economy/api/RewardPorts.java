package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.Dungeon;

import com.google.gson.JsonElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reward calculation and post-commit notification dependencies. */
public final class RewardPorts {
    private RewardPorts(){}
    public record ExperienceCommit(Map<String,JsonElement> writes,Runnable publish){}
    @FunctionalInterface
    public interface Experience {
        ExperienceCommit prepare(String id,Map<UUID,List<Integer>> percentages);
    }
    public interface Players {
        UUID session(ServerPlayer player);
        Progress progress(ServerPlayer player);
        void progress(ServerPlayer player,Progress value);
        Stats stats(ServerPlayer player);
        void profile(ServerPlayer player);
        void message(ServerPlayer player,String text);
        default void groundPickup(ServerPlayer player,ItemStack stack){}
        default void lootAcquired(ServerPlayer player,ItemStack stack){}
    }
    public interface Encounters {
        boolean participant(ServerPlayer player,String instance);
        Dungeon byId(String instance);
        String currentInstance(ServerPlayer player);
        boolean finalPracticeKill(Dungeon dungeon,UUID enemy);
        /** Presents the already-committed abyss flash fact to everyone still in that instance; presentation only, no gameplay state. */
        default void abyssFlash(String instance,double x,double y,double z,boolean mythic){}
    }
    public interface Balance {
        RewardSettings settings();
        long gold(ServerPlayer player);
        long revision(ServerPlayer player);
        void notifyGain(ServerPlayer player,long amount,long balance,long revision);
        java.util.Map<String,com.google.gson.JsonElement> receive(ServerPlayer player,com.google.gson.JsonObject result);
    }
}
