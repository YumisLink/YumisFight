package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.function.BiConsumer;

/** Module-local spatial content registry. The same bounds drive construction, reuse and chunk ownership. */
public final class DungeonLayouts {
    public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ){}
    @FunctionalInterface public interface Gate {void set(ServerLevel world,BlockPos origin,int gate,boolean open);}
    public record Layout(String version,Bounds bounds,int gates,int wavesPerGate,BiConsumer<ServerLevel,BlockPos> build,Gate gate){}
    private static final Map<String,Layout> ALL=Map.of(
        "goblin_camp",new Layout("YDNF_LAYOUT_2",new Bounds(-23,-2,-6,23,49,146),3,2,GoblinCamp::build,GoblinCamp::gate),
        "dragon_tower",new Layout("YDNF_LAYOUT_2",new Bounds(-23,-2,-18,23,49,146),3,2,DragonTower::build,DragonTower::gate),
        "gbl_church",new Layout("GBL_LAYOUT_1",new Bounds(-23,-2,-6,23,49,GblChurch.MAX_Z),3,2,GblChurch::build,GblChurch::gate),
        "castle_palace",new Layout("PALACE_LAYOUT_1",new Bounds(-23,-2,-6,23,49,CastlePalace.MAX_Z),6,1,CastlePalace::build,CastlePalace::gate));
    public static Layout require(String id){Layout value=ALL.get(id);if(value==null)throw new IllegalArgumentException("该副本尚未配置场地："+id);return value;}
    public static Bounds reuseBounds(){return new Bounds(ALL.values().stream().mapToInt(l->l.bounds.minX).min().orElseThrow(),-2,ALL.values().stream().mapToInt(l->l.bounds.minZ).min().orElseThrow(),23,49,ALL.values().stream().mapToInt(l->l.bounds.maxZ).max().orElseThrow());}
    private DungeonLayouts(){}
}
