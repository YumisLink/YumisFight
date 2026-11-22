package cn.yumis.fight.modules.quests.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.npc.api.NpcDefinitions;
import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import cn.yumis.fight.modules.quests.api.Commissions;
import cn.yumis.fight.modules.quests.api.Commissions.Active;
import cn.yumis.fight.modules.quests.api.Commissions.CommissionState;
import cn.yumis.fight.modules.quests.api.Commissions.Kind;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Supplier;

/** The owner observes counting goals, draws replacements and fixes settlements before the transaction. */
final class CommissionClient {
    private static final Gson JSON=new Gson();
    private static Supplier<Progress> growth;
    private static Supplier<JsonObject> profileView;
    private static JsonObject view;
    private static CommissionState mirror=CommissionState.EMPTY;
    private static int observedDelta;
    private static boolean pending;
    private static long retryAt;
    private static Active drawn;
    private static long settlementRevision;

    private CommissionClient(){}

    static void install(CommandRouter<Void,JsonObject> preparations,Supplier<Progress> progress,Supplier<JsonObject> profile){
        growth=progress;profileView=profile;
        preparations.register("quests",Commissions.ACTION,(ignored,request)->prepare(request));
        NpcInteractionComponent.registerClient("commission",CommissionClient::interact);
    }

    static void receive(JsonObject profile){
        var incoming=profile.getAsJsonObject("commission");
        if(Objects.equals(view,incoming))return;
        view=incoming;
        mirror=view==null?CommissionState.EMPTY:JSON.fromJson(view.get("state"),CommissionState.class);
        if(mirror.active()!=null&&observedDelta>mirror.active().count()-mirror.active().progress())observedDelta=0;
        QuestsPage.refresh();
    }

    static void clear(){view=null;mirror=CommissionState.EMPTY;observedDelta=0;pending=false;retryAt=0;drawn=null;}

    private static boolean available(){return view!=null&&ClientUi.current().ready()&&TutorialClient.alive();}

    private static Active active(){return mirror.active();}

    /** Sidebar detail for the task bar tab. */
    static String headline(){
        var value=active();
        return value==null?"可接新委托":"进度 "+Math.min(effective(value),value.count())+"/"+value.count();
    }

    private static int effective(Active active){return Math.min(active.count(),active.progress()+observedDelta);}

    private static boolean counting(Kind kind){var value=active();return value!=null&&Kind.of(value.kind())==kind&&effective(value)<value.count();}

    /** The server's own kill fact relay; only open-world kills feed the hunt goal. */
    static void killObserved(JsonObject fact){
        if(!counting(Kind.HUNT))return;
        // The relayed RewardRules.Context carries "encounter"/"practice" only inside instances; plain Gson omits nulls.
        if(fact.has("encounter")&&!fact.get("encounter").isJsonNull())return;
        if(fact.has("practice")&&!fact.get("practice").isJsonNull())return;
        observedDelta++;
    }

    static void chestOpened(){
        if(counting(Kind.CHEST))observedDelta++;
    }

    static void professionEvent(JsonObject event){
        if(!counting(Kind.PROFESSION))return;
        String kind=event.get("kind").getAsString();
        var goal=active().item();
        if(goal.equals("fishing")?!Commissions.FISHING_KINDS.contains(kind):!goal.equals(kind))return;
        observedDelta++;
    }

    static void tick(){
        if(!available()||pending||ClientUi.now()<retryAt)return;
        var value=active();
        if(value==null)return;
        if(Commissions.ownerCounted(value)&&observedDelta>0){
            send(progressRequest(Math.min(value.count(),value.progress()+observedDelta)));
            return;
        }
        var kind=Kind.of(value.kind());
        boolean claimable=kind==Kind.DUNGEON_CLEAR&&value.progress()>=value.count()||Commissions.ownerCounted(value)&&value.progress()>=value.count();
        if(claimable)sendClaim();
    }

    /** Registered as the commission agent's client action; the screen owns accept, reject, deliver and abandon. */
    private static boolean interact(LivingEntity agent,NpcDefinitions.Action action){
        // A fresh draw per conversation: rejected offers never linger, accepted ones live in the stored state.
        if(active()==null)drawn=Commissions.draw(growth.get().level,new Random());
        Minecraft.getInstance().gui.setScreen(new CommissionDialogueScreen(agent));
        return true;
    }

    private static JsonObject progressRequest(int value){
        var request=ClientUi.current().operation("progress");
        request.addProperty("kind","progress");
        request.addProperty("progress",value);
        return request;
    }

    static void accept(LivingEntity agent){
        if(pending||drawn==null||active()!=null)return;
        var request=ClientUi.current().operation("accept");
        request.addProperty("kind","accept");
        request.addProperty("entity",agent.getStringUUID());
        request.add("commission",JSON.toJsonTree(drawn));
        send(request);
    }

    static void abandon(){
        if(pending||active()==null)return;
        var request=ClientUi.current().operation("abandon");
        request.addProperty("kind","abandon");
        send(request);
    }

    static void deliver(LivingEntity agent){
        if(pending||!Commissions.delivered(active()))return;
        var request=ClientUi.current().operation("deliver");
        request.addProperty("kind","deliver");
        request.addProperty("entity",agent.getStringUUID());
        send(request);
    }

    private static void sendClaim(){
        var value=active();
        if(pending||value==null)return;
        var request=ClientUi.current().operation("claim");
        request.addProperty("kind","claim");
        send(request);
    }

    private static void send(JsonObject request){
        pending=true;retryAt=0;
        ClientUi.current().send(Commissions.ACTION,request);
        QuestsPage.refresh();
    }

    static void committed(JsonObject request,boolean received){
        pending=false;
        retryAt=received?0:ClientUi.now()+1000;
        settlementRevision++;
        if(received){
            if(request.get("kind").getAsString().equals("progress"))observedDelta=0;
            if(request.get("kind").getAsString().equals("accept"))drawn=null;
        }else if(request.get("kind").getAsString().equals("accept")&&active()!=null)drawn=null;
        QuestsPage.refresh();
    }

    private static JsonElement bonus(String item,int count){
        var player=Minecraft.getInstance().player;
        return InventoryEdits.encode(player,ItemRegistry.item(item,count));
    }

    private static void balances(JsonObject request,long gain){
        var profile=profileView.get();
        long balance=profile.has("points")?profile.get("points").getAsLong():0L;
        var before=new JsonObject();before.addProperty("points",balance);
        var after=new JsonObject();after.addProperty("points",Math.addExact(balance,gain));
        request.add("beforeBalances",before);request.add("balances",after);
        request.addProperty("pointsGain",gain);
    }

    /** Owner-scanned removal of exactly the required amount: local inventory plus the server's bag view rows. */
    private static JsonArray removalEdits(String item,int count){
        var player=Minecraft.getInstance().player;var edits=new JsonArray();int remaining=count;
        for(int slot=0;slot<36&&remaining>0;slot++){
            var stack=player.getInventory().getItem(slot);
            if(stack.isEmpty()||!ItemData.own(stack)||!ItemData.id(stack).equals(item))continue;
            int used=Math.min(remaining,stack.getCount());
            var after=used==stack.getCount()?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-used);
            edits.add(InventoryEdits.change(player,"inventory",slot,stack,after));
            remaining-=used;
        }
        if(remaining>0&&view!=null&&view.has("bag")){
            for(var raw:view.getAsJsonArray("bag")){
                if(remaining<=0)break;
                var row=raw.getAsJsonObject();
                var stack=InventoryEdits.decode(player,row.get("source"));
                int used=Math.min(remaining,stack.getCount());
                var after=used==stack.getCount()?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-used);
                edits.add(InventoryEdits.change(player,"bag",row.get("slot").getAsInt(),stack,after));
                remaining-=used;
            }
        }
        if(remaining>0)throw new IllegalArgumentException("还缺少 "+displayName(item)+" ×"+remaining);
        return edits;
    }

    static String displayName(String item){
        var stack=ItemRegistry.item(item,1);
        return stack.getHoverName().getString();
    }

    private static int held(String item){
        var player=Minecraft.getInstance().player;if(player==null)return 0;int count=0;
        for(int slot=0;slot<36;slot++){
            var stack=player.getInventory().getItem(slot);
            if(!stack.isEmpty()&&ItemData.own(stack)&&ItemData.id(stack).equals(item))count+=stack.getCount();
        }
        if(view!=null&&view.has("bag"))for(var raw:view.getAsJsonArray("bag"))count+=InventoryEdits.decode(player,raw.getAsJsonObject().get("source")).getCount();
        return count;
    }

    /** The card mirrors the offer in hand, or the commission already being carried. */
    static boolean deliverable(Active value){return !pending&&held(value.item())>=value.count();}

    private static void prepare(JsonObject request){
        if(!available())throw new IllegalArgumentException("委托尚未就绪");
        String kind=request.get("kind").getAsString();
        switch(kind){
            case "accept" -> {
                var value=JSON.fromJson(request.get("commission"),Active.class);
                Commissions.validate(value,growth.get().level);
                if(active()!=null)throw new IllegalArgumentException("已有进行中的委托");
                if(!request.has("entity")||!(Minecraft.getInstance().level.getEntity(UUID.fromString(request.get("entity").getAsString())) instanceof LivingEntity agent)
                        ||!agent.isAlive()||!NpcInteractionComponent.id(agent).equals(Commissions.NPC))throw new IllegalArgumentException("请在委托NPC附近接取");
                drawn=null;
            }
            case "abandon" -> {if(active()==null)throw new IllegalArgumentException("没有进行中的委托");}
            case "progress" -> {
                var value=active();
                if(value==null||!Commissions.ownerCounted(value))throw new IllegalArgumentException("当前委托不按次数推进");
                int target=request.get("progress").getAsInt();
                if(target<value.progress()||target>value.count())throw new IllegalArgumentException("委托进度无效");
            }
            case "deliver" -> {
                var value=active();
                if(value==null||!Commissions.delivered(value))throw new IllegalArgumentException("当前委托不可交付");
                if(!request.has("entity")||!(Minecraft.getInstance().level.getEntity(UUID.fromString(request.get("entity").getAsString())) instanceof LivingEntity agent)
                        ||!agent.isAlive()||!NpcInteractionComponent.id(agent).equals(Commissions.NPC))throw new IllegalArgumentException("请在委托NPC附近交付");
                request.add("inventoryChanges",removalEdits(value.item(),value.count()));
                balances(request,value.points());
                if(value.bonusCount()>0)request.add("bonus",bonus(value.bonus(),value.bonusCount()));
            }
            case "claim" -> {
                var value=active();
                if(value==null||Kind.of(value.kind())!=Kind.DUNGEON_CLEAR&&!Commissions.ownerCounted(value))throw new IllegalArgumentException("当前委托不可自动结算");
                if(value.progress()<value.count())throw new IllegalArgumentException("委托目标尚未完成");
                balances(request,value.points());
                if(value.bonusCount()>0)request.add("bonus",bonus(value.bonus(),value.bonusCount()));
            }
            default -> throw new IllegalArgumentException("未知委托操作："+kind);
        }
        request.add("beforeCommission",JSON.toJsonTree(mirror));
    }

    static Active pendingDraw(){return drawn;}
    /** The stored commission, for the dialogue card; the offer in hand takes precedence on a fresh draw. */
    static Active activeCommission(){return active();}
    /** Bumped whenever a settlement commits; open dialogue screens close on the change. */
    static long settlementRevision(){return settlementRevision;}

    static void render(PlayerPageSurface page){
        var value=active();
        page.line("累计完成 "+mirror.done()+" 个委托",UiTheme.MUTED);page.gap();
        if(value==null){
            page.line("当前没有进行中的委托。",UiTheme.TEXT);
            page.line("委托NPC随机出现在新手村各处，靠近后右键交谈即可查看并接取。",UiTheme.MUTED);
            page.line("完成或放弃后，可以在任意委托NPC处接取新的委托。",UiTheme.MUTED);
            page.gap();
            return;
        }
        var kind=Kind.of(value.kind());
        page.line(Commissions.title(value),UiTheme.GOLD);page.gap();
        page.line(Commissions.objective(value),UiTheme.TEXT);page.gap();
        switch(kind){
            case DUNGEON_CLEAR -> page.line("通关进度 "+value.progress()+"/"+value.count(),value.progress()>=value.count()?UiTheme.ACCENT:UiTheme.TEXT);
            case DUNGEON_MATERIAL,CURRENCY -> {
                int count=held(value.item());
                page.line(displayName(value.item())+" "+Math.min(count,value.count())+"/"+value.count(),count>=value.count()?UiTheme.ACCENT:UiTheme.MUTED);
                page.line("集齐后在任意委托NPC处交付。",UiTheme.MUTED);
            }
            default -> page.line("完成进度 "+effective(value)+"/"+value.count(),effective(value)>=value.count()?UiTheme.ACCENT:UiTheme.TEXT);
        }
        page.gap();
        page.line("奖励：点券 ×"+value.points(),UiTheme.GOLD);
        if(value.bonusCount()>0)page.line("附加："+displayName(value.bonus())+" ×"+value.bonusCount(),UiTheme.TEXT);
        if(kind==Kind.DUNGEON_CLEAR)page.line("通关后自动结算，无需回来交付。",UiTheme.MUTED);
        if(Commissions.ownerCounted(value))page.line("目标达成后自动结算。",UiTheme.MUTED);
        page.gap();
        var button=page.button(pending?"正在提交…":"放弃委托",0,page.cursor(),page.contentWidth()-24,CommissionClient::abandon);
        button.active=!pending&&available()&&value!=null;
        page.cursor(page.cursor()+25);
    }
}
