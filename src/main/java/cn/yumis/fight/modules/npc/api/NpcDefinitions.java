package cn.yumis.fight.modules.npc.api;

import cn.yumis.fight.platform.ItemData;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

public final class NpcDefinitions {
    public record Action(String id,String label,String type,String value) {}
    public record Definition(String name,List<Action> actions,List<String> dialogue) {}
    public record Skin(String name,String uuid,String texture,boolean slim) {}
    public Map<String,Definition> npcs=new LinkedHashMap<>();
    public Map<String,Skin> skins=new LinkedHashMap<>();
    private static Supplier<NpcDefinitions> source=NpcDefinitions::defaults;
    private static final class Defaults {static final String JSON=read();}
    private static String read(){
        try(var input=NpcDefinitions.class.getResourceAsStream("/data/"+ItemData.NS+"/rules/npcs.json")){
            if(input==null)throw new IllegalStateException("缺少默认NPC定义");return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    public static NpcDefinitions defaults(){return parse(Defaults.JSON);}
    public static NpcDefinitions current(){return source.get();}
    public static void source(Supplier<NpcDefinitions> value){source=Objects.requireNonNull(value);}
    public static NpcDefinitions parse(String json){
        var result=new Gson().fromJson(json,NpcDefinitions.class);
        if(result==null||result.npcs==null||result.skins==null||result.skins.isEmpty())throw new IllegalArgumentException("NPC定义或皮肤池为空");
        for(var row:result.npcs.entrySet()){
            var d=row.getValue();if(!row.getKey().matches("[a-z0-9_./-]+")||d==null||d.name()==null||d.name().isBlank()||d.actions()==null||d.actions().isEmpty()||d.dialogue()==null)throw new IllegalArgumentException("NPC定义无效："+row.getKey());
            var ids=new HashSet<String>();for(var a:d.actions())if(a==null||a.id()==null||!ids.add(a.id())||a.label()==null||a.type()==null||a.value()==null)throw new IllegalArgumentException("NPC选项无效："+row.getKey());
        }
        for(var row:result.skins.entrySet())if(!row.getKey().matches("[a-z0-9_]+")||row.getValue()==null||!row.getValue().texture().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("NPC皮肤无效："+row.getKey());
        return result;
    }
    public Definition require(String id){var d=npcs.get(id);if(d==null)throw new IllegalArgumentException("NPC定义不存在："+id);return d;}
}
