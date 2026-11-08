package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** The seven classic gate defenders, with stable content and resource identities. */
public enum DarkCityEnemyKind {
    HEADLESS_GRAVEROBBER("headless_graverobber","无头盗尸者",.75f,1.55f),
    HEADLESS_SKELETON("headless_skeleton","无头骷髅",.7f,1.65f),
    GUARD("guard","暗精灵护卫",.75f,1.95f),
    GOLIATH("goliath","残缺的歌利亚",1.3f,2.6f),
    TINGO("tingo","谨慎的仃高",.95f,2.05f),
    HUMPHREY("humphrey","沉默的亨普利",1.1f,2.3f),
    HEADLESS_KNIGHT("headless_knight","无头骑士",1.65f,3.35f);

    public final String model,title,id;
    public final float width,height;
    DarkCityEnemyKind(String model,String title,float width,float height){this.model=model;this.title=title;this.id="dark_city_"+model;this.width=width;this.height=height;}
    public static DarkCityEnemyKind find(String id){for(var kind:values())if(kind.id.equals(id))return kind;return null;}
    public static DarkCityEnemyKind of(Entity entity){return find(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());}
}
