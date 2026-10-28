package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.combat.api.CombatSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Ports required by encounter lifecycle, without access to the full server runtime. */
public final class DungeonPorts {
    private DungeonPorts(){}
    public interface Players {
        CombatSession session(ServerPlayer player);
        int level(ServerPlayer player);
        void save(ServerPlayer player)throws Exception;
        void profile(ServerPlayer player);
        void message(ServerPlayer player,String message);
    }
    public interface Targets {
        Target target(LivingEntity entity);
        void context(ServerPlayer observer,LivingEntity entity);
    }
    public interface Rewards {
        void move(ServerPlayer player,String instance,ReturnPoint destination);
        void publish();
    }
    public interface Meter {
        void join(Dungeon dungeon,ServerPlayer player)throws Exception;
        void leave(Dungeon dungeon,ServerPlayer player)throws Exception;
        void freeze(Dungeon dungeon)throws Exception;
    }
}
