package cn.yumis.fight.modules.npc.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Map generation passes completed world-space anchors through this public module port. */
public final class NpcSpawns {
    public record Anchor(String id,Vec3 position,float yaw) {}
    @FunctionalInterface public interface Groups {int spawn(ServerLevel level,String group,List<Anchor> anchors,List<String> definitions,long seed);}
    @FunctionalInterface public interface Relocator {void relocate(ServerLevel overworld,String definitionId,Vec3 targetPos,float yaw);}
    private static Groups groups;
    private static Relocator relocator;
    private static java.util.function.Consumer<ServerLevel> starter=level->{};
    private NpcSpawns(){}
    public static void install(Groups value){groups=value;}
    public static void relocator(Relocator value){relocator=value;}
    public static void starter(java.util.function.Consumer<ServerLevel> value){starter=Objects.requireNonNull(value);}
    /** Called only by the map module's consumed new-world lifecycle. */
    public static void starterWorld(ServerLevel level){starter.accept(level);}
    public static int group(ServerLevel level,String id,List<Anchor> anchors,List<String> definitions,long seed){if(groups==null)throw new IllegalStateException("NPC投放尚未装配");return groups.spawn(level,id,anchors,definitions,seed);}
    public static void relocateUnique(ServerLevel overworld,String definitionId,Vec3 targetPos,float yaw){if(relocator==null)throw new IllegalStateException("NPC迁移尚未装配");relocator.relocate(overworld,definitionId,targetPos,yaw);}
}
