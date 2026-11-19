package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** Stable identities for the six ritual altar bosses; models live in their own altar_boss folder. */
public enum AltarBossKind {
    UNCROWNED_GARDENER("uncrowned_gardener","无冠园丁",30,1.8f,2.8f),
    OFF_DUTY_MATRON("off_duty_matron","歇工的圣母",35,2.0f,3.7f),
    WHITE_RELIQUARY("white_reliquary","白圣髑",40,1.4f,3.0f),
    DEBT_COLLECTOR("debt_collector","催账人",45,1.0f,2.4f),
    FALLEN_SCALE_KING("fallen_scale_king","坠鳞的君王",50,2.8f,2.8f),
    THOUSANDFOLD_CONFESSION("thousandfold_confession","千室告解",55,3.2f,4.0f);

    public final String id,title;
    public final int level;
    public final float width,height;
    AltarBossKind(String id,String title,int level,float width,float height){
        this.id=id;this.title=title;this.level=level;this.width=width;this.height=height;
    }
    private static final Map<String,AltarBossKind> BY_ID=new HashMap<>();
    static{for(var kind:values())BY_ID.put(kind.id,kind);}
    public static AltarBossKind find(String id){return BY_ID.get(id);}
    public static AltarBossKind of(Entity entity){return find(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());}
    public String resource(){return "altar_boss/"+id;}
}
