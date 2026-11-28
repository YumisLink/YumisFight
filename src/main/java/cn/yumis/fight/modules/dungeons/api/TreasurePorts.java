package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
/** Reward requests join the caller's WAL transaction; the economy owner creates and publishes items. */
public interface TreasurePorts {
    default void chest(ServerPlayer player,String definition,int tier,int level,Vec3 position,String instance)throws Exception{
        chest(player,definition,tier,level,1,"",position,instance);
    }
    void chest(ServerPlayer player,String definition,int tier,int level,int multiplier,String themeDungeon,Vec3 position,String instance)throws Exception;
    /** Escape Golden Goblin kill reward: joins the caller's WAL transaction and flushes on publish(). */
    default void goblinLoot(ServerPlayer player,int level,Vec3 position,String instance)throws Exception{}
    void publish();
    void abort();
}
