package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.combat.api.CombatSession;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Ports required by encounter lifecycle, without access to the full server runtime. */
public final class DungeonPorts {
    private DungeonPorts(){}
    @FunctionalInterface
    public interface CampDecorations {
        net.minecraft.world.level.block.Block block(String id);
    }
    public interface Players {
        CombatSession session(ServerPlayer player);
        int level(ServerPlayer player);
        cn.yumis.fight.modules.character.api.Progress progress(ServerPlayer player);
        void progress(ServerPlayer player,cn.yumis.fight.modules.character.api.Progress next);
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
        void closePractice(String instance)throws Exception;
    }
    @FunctionalInterface
    public interface TowerClear {
        CombatPorts.Commit prepare(UUID instance,Set<UUID> participants);
    }
    @FunctionalInterface
    public interface StoryClear {
        CombatPorts.Commit prepare(UUID instance,String dungeon,Difficulty difficulty,Set<UUID> participants);
    }
    public interface Meter {
        void join(Dungeon dungeon,ServerPlayer player)throws Exception;
        void leave(Dungeon dungeon,ServerPlayer player)throws Exception;
        void freeze(Dungeon dungeon)throws Exception;
    }
}
