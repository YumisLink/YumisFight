package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.AffixResolver;
import cn.yumis.fight.modules.affixes.api.AffixJson;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.affixes.api.*;

import com.google.gson.*;
import java.util.*;
/** ItemStack read paths may resolve a detached instance; persistence remains owned by inventory transactions. */
public final class EquipmentResolution {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().registerTypeAdapterFactory(new AffixJson()).create();
    private EquipmentResolution(){}
    public static Gear read(String json){
        JsonObject data=JsonParser.parseString(json).getAsJsonObject();
        if(!data.has("version")||data.get("version").getAsInt()!=Gear.SCHEMA_VERSION)throw new IllegalArgumentException("不支持的装备存档版本");
        return resolve(JSON.fromJson(data,Gear.class));
    }
    public static Gear resolve(Gear original){
        if(original.version!=Gear.SCHEMA_VERSION)throw new IllegalArgumentException("不支持的装备存档版本");
        Gear g=JSON.fromJson(JSON.toJson(original),Gear.class);
        for(int i=0;i<g.affixes.size();i++)resolve(g.affixes.get(i),g.instance+"/affixes/"+i);
        for(int i=0;i<g.gems.size();i++)for(int j=0;j<g.gems.get(i).affixes.size();j++)resolve(g.gems.get(i).affixes.get(j),g.instance+"/gems/"+i+"/"+j);
        return g;
    }
    private static void resolve(Affix a,String path){AffixResolver.resolve(a,AffixDefinitions.current().affixes.get(AffixResolver.definitionId(a)),path);}
    /** Explicit creation or mutation boundary. Reads never invent missing random origins. */
    public static Gear initialize(Gear g){
        for(int i=0;i<g.affixes.size();i++)initialize(g.affixes.get(i),g.instance+"/affixes/"+i);
        for(int i=0;i<g.gems.size();i++)for(int j=0;j<g.gems.get(i).affixes.size();j++)initialize(g.gems.get(i).affixes.get(j),g.instance+"/gems/"+i+"/"+j);
        return resolve(g);
    }
    private static void initialize(Affix a,String path){if(!a.base&&a.origin==null)AffixResolver.anchor(a,path,null,0);}
}
