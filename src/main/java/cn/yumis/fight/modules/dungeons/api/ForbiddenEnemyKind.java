package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Revived GBL silhouettes and the separately attackable Marcellus barrier. */
public enum ForbiddenEnemyKind {
    CULTIST("cultist","复活的GBL信徒",.65f,1.7f),
    FEMALE_CULTIST("female_cultist","复活的GBL女信徒",.65f,1.7f),
    DEACON("deacon","复活的GBL神官",.7f,1.8f),
    PRIEST("priest","复活的GBL祭司",.8f,1.9f),
    BISHOP("bishop","复活的GBL主教",.85f,2f),
    MARCELLUS("marcellus","审判者马塞尔",1f,2.2f),
    BARRIER_CORE("barrier_core","马塞尔的防御罩",2.6f,2.8f);
    public final String model,title,id;
    public final float width,height;
    ForbiddenEnemyKind(String model,String title,float width,float height){this.model=model;this.title=title;this.id="forbidden_"+model;this.width=width;this.height=height;}
    public static ForbiddenEnemyKind find(String id){for(var kind:values())if(kind.id.equals(id))return kind;return null;}
    public static ForbiddenEnemyKind of(Entity entity){return find(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());}
}
