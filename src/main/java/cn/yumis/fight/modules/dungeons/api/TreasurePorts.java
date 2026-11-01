package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
/** Reward requests join the caller's WAL transaction; the economy owner creates and publishes items. */
public interface TreasurePorts {
    void chest(ServerPlayer player,String definition,int tier,int level,Vec3 position,String instance)throws Exception;
    void publish();
    void abort();
}
