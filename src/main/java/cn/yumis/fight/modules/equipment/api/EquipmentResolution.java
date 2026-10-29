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
    private static final int MAX_ENTRIES=2048,MAX_CHARACTERS=8*1024*1024;
    private static final LinkedHashMap<String,Entry> READS=new LinkedHashMap<>(64,.75f,true);
    private static AffixDefinitions definitions;
    private static EquipmentDefinitions equipmentDefinitions;
    private static long equipmentRevision=-1;
    private static long definitionRevision=-1,lookups,hits,decodes;
    private static int characters;
    /** The render path only sees this immutable projection; no mutable Gear escapes the cache. */
    public record View(Rarity rarity,String appearance,int level,Slot slot,Weapon weapon,UUID instance,int revision){
        public boolean valid(cn.yumis.fight.modules.character.api.Progress progress,int allowance){return Gear.valid(level,slot,weapon,progress,allowance);}
    }
    private static final class Entry {
        private final Gear snapshot;
        private final View view;
        private Entry(Gear value){snapshot=value;view=value==null?null:new View(value.rarity,EquipmentTemplates.appearance(value),value.level,value.slot,value.weapon,value.instance,value.revision);}
        private Gear detached(){return snapshot==null?null:EquipmentCopies.gear(snapshot);}
    }
    public record CacheMetrics(long lookups,long hits,long decodes,int entries,int characters){}
    public static synchronized CacheMetrics cacheMetrics(){return new CacheMetrics(lookups,hits,decodes,READS.size(),characters);}
    public static synchronized void clearCache(){READS.clear();characters=0;definitions=null;definitionRevision=-1;equipmentDefinitions=null;equipmentRevision=-1;}
    private EquipmentResolution(){}
    private static synchronized Entry cached(String json){
        var current=AffixDefinitions.current();
        var equipment=EquipmentDefinitions.current();
        if(current!=definitions||current.revision!=definitionRevision||equipment!=equipmentDefinitions||equipment.revision!=equipmentRevision){READS.clear();characters=0;definitions=current;definitionRevision=current.revision;equipmentDefinitions=equipment;equipmentRevision=equipment.revision;}
        lookups++;Entry cached=READS.get(json);if(cached!=null){hits++;return cached;}
        Entry entry;
        try{decodes++;entry=new Entry(decode(json));}catch(RuntimeException invalid){entry=new Entry(null);}
        if(json.length()<=MAX_CHARACTERS){READS.put(json,entry);characters+=json.length();while(READS.size()>MAX_ENTRIES||characters>MAX_CHARACTERS){var oldest=READS.entrySet().iterator();var removed=oldest.next();characters-=removed.getKey().length();oldest.remove();}}
        return entry;
    }
    public static Gear read(String json){Gear value=cached(json).detached();if(value==null)throw new IllegalArgumentException("装备数据无效");return value;}
    public static View view(String json){return json.isEmpty()?null:cached(json).view;}
    private static Gear decode(String json){
        JsonObject data=JsonParser.parseString(json).getAsJsonObject();
        if(!data.has("version")||data.get("version").getAsInt()!=Gear.SCHEMA_VERSION)throw new IllegalArgumentException("不支持的装备存档版本");
        return resolveOwned(JSON.fromJson(data,Gear.class));
    }
    public static Gear resolve(Gear original){
        if(original.version!=Gear.SCHEMA_VERSION)throw new IllegalArgumentException("不支持的装备存档版本");
        return resolveOwned(EquipmentCopies.gear(original));
    }
    private static Gear resolveOwned(Gear g){
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
