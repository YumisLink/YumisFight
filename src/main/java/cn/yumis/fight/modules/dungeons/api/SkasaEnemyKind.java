package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Classic Skasa's Lair content, including the one reachable head/chest anchor. */
public enum SkasaEnemyKind {
    MALE_WARRIOR("male_warrior","图卢斯族男战士",.7f,1.95f),
    FEMALE_WARRIOR("female_warrior","图卢斯族女战士",.65f,1.85f),
    NASRA("nasra","北极风纳斯拉",.75f,2f),
    ICE_SPIDER("ice_spider","寒冰蜘蛛",1.35f,.9f),
    HATCHLING("hatchling","斯卡萨幼龙",1.2f,1.25f),
    ICE_DRAGON("ice_dragon","冰龙斯卡萨",3.6f,4f);

    public final String model,title,id;
    public final float width,height;
    SkasaEnemyKind(String model,String title,float width,float height){
        this.model=model;this.title=title;this.id="skasa_"+model;this.width=width;this.height=height;
    }
    public static SkasaEnemyKind find(String id){for(var kind:values())if(kind.id.equals(id))return kind;return null;}
    public static SkasaEnemyKind of(Entity entity){return find(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());}
}
