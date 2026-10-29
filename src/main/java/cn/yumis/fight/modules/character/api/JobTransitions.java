package cn.yumis.fight.modules.character.api;

import com.google.gson.JsonElement;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;

/** A career change's skill/resource writes join the character WAL transaction. */
public interface JobTransitions {
    Change prepare(ServerPlayer player,Progress next,String destination);
    Change reduceSkill(ServerPlayer player,Progress next,String skillId);
    default Change clear(ServerPlayer player,Progress next){return new Change(Map.of(),()->{});}
    record Change(Map<String,JsonElement> writes,Runnable committed){}
}
