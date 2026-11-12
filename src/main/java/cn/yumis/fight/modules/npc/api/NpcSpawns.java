package cn.yumis.fight.modules.npc.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Map generation passes completed world-space anchors through this public module port. */
public final class NpcSpawns {
    public record Anchor(String id,Vec3 position,float yaw) {}
    @FunctionalInterface public interface Groups {int spawn(ServerLevel level,String group,List<Anchor> anchors,List<String> definitions,long seed);}
    private static Groups groups;
    private NpcSpawns(){}
    public static void install(Groups value){groups=value;}
    public static int group(ServerLevel level,String id,List<Anchor> anchors,List<String> definitions,long seed){if(groups==null)throw new IllegalStateException("NPC投放尚未装配");return groups.spawn(level,id,anchors,definitions,seed);}
}
