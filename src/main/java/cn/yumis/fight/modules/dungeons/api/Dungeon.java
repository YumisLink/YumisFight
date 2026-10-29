package cn.yumis.fight.modules.dungeons.api;

import java.util.*;

/** dungeons模块拥有的数据合同；仅接收当前存档代际。 */
public class Dungeon {
    public UUID id=UUID.randomUUID();
    public String dungeon="goblin_camp",status="RUNNING";
    public int cell,wave;
    public Difficulty difficulty=Difficulty.NORMAL;
    public boolean summoned;
    public Map<UUID,Boolean> synchronization=new LinkedHashMap<>();
    public boolean syncFor(UUID player){Boolean value=synchronization.get(player);if(value==null)throw new IllegalArgumentException("玩家没有本场同步选择");return value;}
    public Set<UUID> roster=new LinkedHashSet<>(),withdrawn=new HashSet<>(),enemies=new HashSet<>();
    public Map<String,ReturnPoint> returns=new HashMap<>();
}
