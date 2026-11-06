package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Classic Sky Tower silhouettes; these IDs belong to the palace content. */
public enum PalaceEnemyKind {
    EXPELLER("expeller","驱逐者",.85f,2.05f),
    AXE_EXPELLER("axe_expeller","斧之驱逐者",1f,2.25f),
    COMMANDER("golem_commander","石巨人操控师",.85f,1.65f),
    GOLEM("scholar_golem","浮游石巨人",1.6f,2.45f),
    AXION("axion","骑士队长阿克雷",1.05f,2.5f),
    IAE("general_iae","大将伊埃",.85f,1.4f),
    SIEGHART("sieghart","光之城主赛格哈特",1.1f,2.8f);

    public final String model,id,title;
    public final float width,height;
    PalaceEnemyKind(String model,String title,float width,float height){this.model=model;this.id="palace_"+model;this.title=title;this.width=width;this.height=height;}
    public static PalaceEnemyKind find(String id){for(var kind:values())if(kind.id.equals(id))return kind;return null;}
    public static PalaceEnemyKind of(Entity entity){return find(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());}
}
