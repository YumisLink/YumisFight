package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Content identifiers, model names and physical silhouettes owned by the GBL encounter. */
public enum GblEnemyKind {
    CULTIST("cultist","GBL教信徒",.65f,1.7f),
    FEMALE_CULTIST("female_cultist","GBL教女信徒",.65f,1.7f),
    DRAGON_CANNON("dragon_cannon","龙头炮",1.05f,1.35f),
    YASHA("yasha","夜叉",.95f,1.5f),
    FALLEN_PRIEST("fallen_priest","堕落的祭司",1f,1.75f),
    ASOR("asor","堕落的阿索尔",1.4f,2.3f);

    public final String model,title,id;
    public final float width,height;
    GblEnemyKind(String model,String title,float width,float height){this.model=model;this.title=title;this.id="gbl_"+model;this.width=width;this.height=height;}
    public static GblEnemyKind find(String id){for(var kind:values())if(kind.id.equals(id))return kind;return null;}
    public static GblEnemyKind of(Entity entity){return find(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());}
}
