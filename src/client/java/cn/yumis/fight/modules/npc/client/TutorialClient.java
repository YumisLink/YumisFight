package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.npc.api.NpcTutorial;
import cn.yumis.fight.modules.presentation.api.RichText;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** The player's machine observes lesson goals and fixes rewards before the shared storage transaction. */
public final class TutorialClient {
    private static final Gson JSON=new Gson();
    private static Supplier<Progress> growth;private static Supplier<JsonObject> view;private static BooleanSupplier living;
    private static final Set<String> observed=new LinkedHashSet<>();
    private static String session="",dialogue;private static int page;private static boolean starting,pending,promotionReminder;private static long nextSave;
    private TutorialClient(){}
    public static void install(CommandRouter<Void,JsonObject> router,Supplier<Progress> progress,Supplier<JsonObject> profile,BooleanSupplier alive){growth=progress;view=profile;living=alive;router.register("npc","tutorial",(ignored,j)->prepare(j));}
    public static void receive(JsonObject profile){String next=profile.get("session").getAsString();if(!next.equals(session)){clear();session=next;}}
    public static void clear(){observed.clear();session="";dialogue=null;page=0;starting=pending=promotionReminder=false;nextSave=0;}
    public static boolean alive(){return living!=null&&living.getAsBoolean();}
    public static boolean pending(){return pending;}
    public static boolean starting(){return starting;}
    public static NpcTutorial.State state(){var profile=view.get();return profile.has("npcTutorial")?JSON.fromJson(profile.get("npcTutorial"),NpcTutorial.State.class):new NpcTutorial.State();}
    private static NpcTutorial.State current(){var value=state();value.goals.addAll(observed);return value;}
    public static boolean complete(NpcTutorial.Step step){return NpcTutorial.complete(current(),step);}
    public static String dialogueKey(){var state=state();var step=NpcTutorial.current(state);return !state.started?"introduction":step==null?"completion":step.id();}
    public static List<RichText> dialogue(){var state=state();var step=NpcTutorial.current(state);return !state.started?NpcTutorial.definition().introduction():step==null?NpcTutorial.definition().completion():step.dialogue();}
    public static int savedPage(String key){var state=state();return key.equals(dialogue)?page:key.equals(state.dialogue)?state.page:0;}
    public static void savePage(String key,int value){dialogue=key;page=Math.max(0,value);nextSave=0;}
    public static void start(){starting=true;nextSave=0;tick();}
    private static void observe(){
        var progress=growth.get();if(progress.selected())observed.add("choose_job");
        for(var entry:progress.skills.entrySet())if(entry.getValue()>0&&SkillCatalog.contains(entry.getKey())&&!SkillCatalog.require(entry.getKey()).passive()&&!SkillCatalog.require(entry.getKey()).equipmentGranted){observed.add("learn_skill");break;}
        for(String id:progress.slots)if(id!=null&&!id.isEmpty()&&SkillCatalog.contains(id)&&!SkillCatalog.require(id).passive()&&progress.learned(id)>0){observed.add("bind_skill");break;}
        if(progress.strength>0||progress.intelligence>0||progress.spirit>0)observed.add("allocate");
    }
    public static void skillHit(Target target,SkillDefinition skill,double fraction){
        if(!ClientUi.current().ready()||!alive()||!state().started||target==null||!target.training()||target.timedTraining||skill==null||skill.passive()||growth.get().learned(skill.id)<=0||fraction<=0)return;
        observed.add("practice_hit");nextSave=0;
    }
    /** Called by composition only when the existing personal queue retires its matching storage receipt. */
    public static void committed(String action,JsonObject request,boolean received){
        if(!action.equals("tutorial"))return;
        pending=false;
        if(received){
            var saved=request.getAsJsonObject("tutorial");if(saved.get("started").getAsBoolean())starting=false;
            if(saved.get("promotionHinted").getAsBoolean()){
                promotionReminder=false;
                if(!request.getAsJsonObject("beforeTutorial").get("promotionHinted").getAsBoolean())ClientUi.current().message("伊蕾娜：已经20级了，打开 I → 职业技能查看进阶职业吧。转职还需解锁通用转职资格。");
            }
            if(request.get("kind").getAsString().equals("claim")){
                dialogue=saved.get("dialogue").getAsString();page=saved.get("page").getAsInt();var title=request.get("lessonTitle").getAsString();
                ClientUi.current().message("已提交「"+title+"」并领取奖励；"+(NpcTutorial.current(state())==null?"旅途指引已完成。":"下一课已记入任务追踪器。"));
            }
        }
        nextSave=starting&&received?0:ClientUi.now()+500;
        TutorialPage.refresh();
    }
    /** A published equipment transaction fact, never a button press or an uncommitted request. */
    public static void crafted(){observed.add("craft");nextSave=0;}
    public static void tick(){
        if(view==null||!ClientUi.current().ready()||!alive())return;observe();if(pending||ClientUi.now()<nextSave)return;
        var state=state();if(!state.started&&!starting&&dialogue==null)return;
        promotionReminder|=state.started&&NpcTutorial.current(state)==null&&!state.promotionHinted&&growth.get().level>=20&&JobCatalog.basic(growth.get().jobId());
        boolean changed=starting&&!state.started||promotionReminder&&!state.promotionHinted||!state.goals.containsAll(observed)||dialogue!=null&&(!dialogue.equals(state.dialogue)||page!=state.page);
        if(!changed)return;
        var request=ClientUi.current().operation("update");request.addProperty("retryStorage",true);pending=true;ClientUi.current().send("tutorial",request);
    }
    public static void claim(){
        if(pending||!alive())return;var state=current();var step=NpcTutorial.current(state);if(step==null||!NpcTutorial.complete(state,step))return;
        var request=ClientUi.current().operation("claim");request.addProperty("lesson",step.id());request.addProperty("retryStorage",true);pending=true;ClientUi.current().send("tutorial",request);
    }
    public static ItemStack item(NpcTutorial.ItemGrant grant){
        if(grant.material()!=null&&!grant.material().isBlank())return EquipmentItems.material(grant.material(),grant.count());
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(grant.item())),grant.count());
    }
    private static void prepare(JsonObject request){
        if(!alive())throw new IllegalArgumentException("请在存活时操作旅途指引");observe();
        var before=state();var next=JSON.fromJson(JSON.toJsonTree(before),NpcTutorial.State.class);next.goals.addAll(observed);if(starting)next.started=true;
        if(promotionReminder)next.promotionHinted=true;
        if(dialogue!=null){next.dialogue=dialogue;next.page=page;}var outputs=new JsonArray();
        if(request.get("kind").getAsString().equals("claim")){
            var step=NpcTutorial.current(next);if(step==null||!step.id().equals(request.get("lesson").getAsString())||!NpcTutorial.complete(next,step))throw new IllegalArgumentException("这项引导尚未完成或已经领取");
            next.claimed.add(step.id());next.page=0;var following=NpcTutorial.current(next);next.dialogue=following==null?"completion":following.id();
            request.addProperty("lessonTitle",step.title().plain());
            if(step.reward().experience()>0){var progress=growth.get();var result=CareerProfiles.copy(progress);result.grant(step.reward().experience());request.add("beforeProgress",JSON.toJsonTree(progress));request.add("progress",JSON.toJsonTree(result));}
            if(step.reward().gold()>0){
                var profile=view.get();String account=CurrencyRules.GOLD.id();long gold=profile.get(account).getAsLong(),revision=profile.get("goldRevision").getAsLong();var source=new JsonObject();source.addProperty(account,gold);source.addProperty("goldRevision",revision);var result=new JsonObject();result.addProperty(account,Math.addExact(gold,step.reward().gold()));result.addProperty("goldRevision",Math.incrementExact(revision));request.add("beforeBalances",source);request.add("balances",result);request.addProperty("goldGain",step.reward().gold());
            }
            for(var grant:step.reward().items())outputs.add(InventoryEdits.encode(Minecraft.getInstance().player,item(grant)));
        }
        next.revision=Math.incrementExact(before.revision);request.add("beforeTutorial",JSON.toJsonTree(before));request.add("tutorial",JSON.toJsonTree(next));request.add("outputs",outputs);
    }
}
