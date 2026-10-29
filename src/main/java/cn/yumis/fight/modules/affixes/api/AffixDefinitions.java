package cn.yumis.fight.modules.affixes.api;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Definition lifecycle and retirement are owned by affixes, independently of equipment templates. */
public final class AffixDefinitions {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public long revision;
    public Map<String,AffixDefinition> affixes=new LinkedHashMap<>();
    private static final AffixDefinitions DEFAULT=defaults();
    private static volatile java.util.function.Supplier<AffixDefinitions> source;
    public static AffixDefinitions current(){var read=source;return read==null?DEFAULT:read.get();}
    public static void source(java.util.function.Supplier<AffixDefinitions> value){source=Objects.requireNonNull(value);}
    public static AffixDefinitions defaults(){try(var in=AffixDefinitions.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/equipment.json")){if(in==null)throw new IllegalStateException("Missing equipment");return parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    public static AffixDefinitions parse(String json){
        var result=JSON.fromJson(json,AffixDefinitions.class);
        if(result==null||result.affixes==null)throw new IllegalArgumentException("Missing affix definitions");
        for(var entry:result.affixes.entrySet())if(entry.getValue()==null||!entry.getKey().equals(entry.getValue().id()))throw new IllegalArgumentException("Affix ID mismatch");
        return result;
    }
    public void retainRetiredFrom(AffixDefinitions previous){
        for(var entry:previous.affixes.entrySet()){
            AffixDefinition next=affixes.get(entry.getKey()),old=entry.getValue();
            if(next==null){affixes.put(entry.getKey(),old.retire());continue;}
            if(next.revision()<old.revision()||!next.sameEffect(old)&&next.revision()<=old.revision())throw new IllegalArgumentException("词条效果变更必须提高revision："+entry.getKey());
            boolean bylleFinalAdd=entry.getKey().equals("unique/bylle_physical_flat")&&old.operation().equals("FLAT")&&next.operation().equals("ITEM_FINAL_ADD")&&old.stat()==cn.yumis.fight.modules.attributes.api.Stat.PHYSICAL_ATTACK&&next.stat()==old.stat();
            if(!next.operation().equals(old.operation())&&!bylleFinalAdd)throw new IllegalArgumentException("词条类型不可复用原ID，请登记新ID："+entry.getKey());
        }
        revision=Math.incrementExact(previous.revision);
    }
}
