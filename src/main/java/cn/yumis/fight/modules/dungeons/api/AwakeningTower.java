package cn.yumis.fight.modules.dungeons.api;

import java.util.List;
import net.minecraft.util.StringRepresentable;

/** The eight layouts offered by the awakening trial. */
public enum AwakeningTower implements StringRepresentable {
    ROOT("root","根系塔"),
    FORGE("forge","锻炉塔"),
    SLUICE("sluice","水闸塔"),
    SAND_MARKET("sand_market","沙市塔"),
    FROST("frost","霜塔"),
    ORRERY("orrery","星仪塔"),
    SEAL("seal","封印塔"),
    THEATER("theater","剧场塔");

    public static final String GOAL="awakening_tower_first_clear";
    public static final int LEVEL=50;
    private final String template;
    private final DungeonCatalog.DungeonDef dungeon;

    AwakeningTower(String template,String name){
        this.template=template;
        this.dungeon=new DungeonCatalog.DungeonDef("awakening_tower_"+template,name,LEVEL,true,-1,List.of());
    }

    public String template(){return template;}
    @Override public String getSerializedName(){return template;}
    public DungeonCatalog.DungeonDef dungeon(){return dungeon;}

    public static AwakeningTower find(String id){
        for(var tower:values())if(tower.dungeon.id().equals(id))return tower;
        return null;
    }
}
