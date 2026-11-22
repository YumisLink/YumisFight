package cn.yumis.fight.modules.quests.api;

import cn.yumis.fight.platform.ItemData;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Loads the quest rule pack: the guide lessons and the awakening stages live here, not on the NPC shell. */
public final class QuestDefinitions {
    public NpcTutorial.Definition tutorial;
    public AwakeningQuest.Definition awakening;
    private static Supplier<QuestDefinitions> source=QuestDefinitions::defaults;
    private static final class Defaults {static final String JSON=read();}
    private static String read(){
        try(var input=QuestDefinitions.class.getResourceAsStream("/data/"+ItemData.NS+"/rules/quests.json")){
            if(input==null)throw new IllegalStateException("缺少默认任务定义");return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    public static QuestDefinitions defaults(){return parse(Defaults.JSON);}
    public static QuestDefinitions current(){return source.get();}
    public static void source(Supplier<QuestDefinitions> value){source=Objects.requireNonNull(value);}
    public static QuestDefinitions parse(String json){
        var result=new Gson().fromJson(json,QuestDefinitions.class);
        if(result==null||result.tutorial==null||result.awakening()==null)throw new IllegalArgumentException("任务定义不完整");
        return result;
    }
    public NpcTutorial.Definition tutorial(){return tutorial;}
    public AwakeningQuest.Definition awakening(){return awakening;}
    private QuestDefinitions(){}
}
