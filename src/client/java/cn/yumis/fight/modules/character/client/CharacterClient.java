package cn.yumis.fight.modules.character.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.character.api.client.*;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.function.*;

/** Owns eligibility, growth and consumable confirmations on the player's machine. */
public final class CharacterClient {
    private static final Gson JSON=new Gson();
    private static Supplier<Progress> progress;private static Supplier<Stats> stats;private static Supplier<ProfileConditions> conditions;
    private static LongSupplier combatUntil;private static SkillProgression skills;private static TalentProgression talents;private static Function<String,ItemStack> starter;
    private static CareerProfiles book;
    private CharacterClient(){}
    public static void install(CommandRouter<Void,JsonObject> preparation,Supplier<Progress> p,Supplier<Stats> s,Supplier<ProfileConditions> c,LongSupplier combat,SkillProgression learning,TalentProgression talentProgress,Function<String,ItemStack> equipment){
        progress=p;stats=s;conditions=c;combatUntil=combat;skills=learning;talents=talentProgress;starter=equipment;
        preparation.register("character","progress",(ignored,j)->prepare(j));
        CharacterActions.install((player,ticket)->{if(player.level().isClientSide())use(ticket);});
    }
    public static void receive(JsonObject profile){book=JSON.fromJson(profile.getAsJsonObject("careerProfiles").get("state"),CareerProfiles.class);}
    public static void clear(){book=null;}
    public static void safe(){if(ClientUi.now()<combatUntil.getAsLong())throw new IllegalArgumentException("请脱战后操作");conditions.get().requireReady();}
    public static String blockReason(){try{safe();return "";}catch(IllegalArgumentException failure){return failure.getMessage();}}
    private static void prepare(JsonObject j){
        if(book==null)throw new IllegalArgumentException("角色资料尚未就绪");
        String kind=j.get("kind").getAsString();boolean crystal=kind.equals("consumeCrystal");
        if(crystal&&!conditions.get().alive())throw new IllegalArgumentException("死亡时不能使用魔能水晶");
        if(!crystal&&!kind.equals("bind")&&!kind.equals("renameProfile")&&ClientUi.now()<combatUntil.getAsLong())throw new IllegalArgumentException("请脱战后操作");
        var player=Minecraft.getInstance().player;var current=progress.get();var value=CharacterChanges.apply(current,book,stats.get(),j,skills,talents,CharacterClient::safe);
        j.add("beforeProgress",JSON.toJsonTree(current));j.addProperty("beforeProfilesKey",book.stateKey());j.add("progress",JSON.toJsonTree(value.progress()));j.add("profiles",book.delta(value.profiles()));
        var edits=new JsonArray();
        if(crystal){
            int slot=j.get("crystalSlot").getAsInt();
            var source=player.getInventory().getItem(slot);
            if(source.isEmpty()||!InventoryEdits.encode(player,source).equals(j.get("crystalSource")))throw new IllegalArgumentException("魔能水晶已变化，请重新使用");
            var after=source.copy();after.shrink(1);edits.add(InventoryEdits.change(player,"inventory",slot,source,after));
        }else if(!value.ticket().isEmpty()){
            int slot=j.has("ticketSlot")?j.get("ticketSlot").getAsInt():player.getInventory().getSelectedSlot();var source=player.getInventory().getItem(slot);
            if(source.isEmpty()||!cn.yumis.fight.platform.ItemData.id(source).equals(value.ticket()))throw new IllegalArgumentException("券已移动或不存在");
            if(j.has("ticketSource")&&!InventoryEdits.encode(player,source).equals(j.get("ticketSource")))throw new IllegalArgumentException("确认的券已变化，请重新使用");
            var after=source.copy();after.shrink(1);edits.add(InventoryEdits.change(player,"inventory",slot,source,after));
        }
        j.add("inventoryChanges",edits);var outputs=new JsonArray();if(value.starter())outputs.add(InventoryEdits.encode(player,starter.apply(value.progress().jobId())));j.add("outputs",outputs);
    }
    public static void useCrystal(InteractionHand hand,long experience){
        var player=Minecraft.getInstance().player;
        if(player==null||!ClientUi.current().ready())return;
        int slot=hand==InteractionHand.MAIN_HAND?player.getInventory().getSelectedSlot():40;
        var j=ClientUi.current().operation("consumeCrystal");
        j.addProperty("crystalSlot",slot);
        j.add("crystalSource",InventoryEdits.encode(player,player.getItemInHand(hand)));
        j.addProperty("crystalExperience",experience);
        ClientUi.current().send("progress",j);
    }
    private static void use(CharacterActions.Ticket ticket){try{
        var player=Minecraft.getInstance().player;if(player==null||!ClientUi.current().ready())return;
        String kind=switch(ticket){case AWAKENING->"unlockAwakening";case PROFILE_EXPANSION->"expandProfile";case LEVEL_ONE->"levelOne";case LEVEL_TEN->"levelTen";case LEVEL_MAX->"levelMax";};
        var j=ClientUi.current().operation(kind);int slot=player.getInventory().getSelectedSlot();j.addProperty("ticketSlot",slot);j.add("ticketSource",InventoryEdits.encode(player,player.getInventory().getItem(slot)));
        if(ticket!=CharacterActions.Ticket.PROFILE_EXPANSION){
            ClientUi.current().send("progress",j);
            return;
        }
        safe();
        if(book.capacity==Integer.MAX_VALUE)throw new IllegalArgumentException("职业配置容量已达上限，未消耗券");
        String text="消耗1张职业栏拓展券，将职业配置容量从"+book.capacity+"增加至"+(book.capacity+1)+"；解锁后新栏位可直接使用。";
        var mc=Minecraft.getInstance();var parent=mc.gui.screen();
        mc.gui.setScreen(new ThemedConfirmScreen(yes->{mc.gui.setScreen(parent);if(yes)ClientUi.current().send("progress",j);},Component.literal("拓展职业配置容量？"),Component.literal(text),ThemeDomain.MENU));
    }catch(RuntimeException failure){ClientUi.current().message(failure.getMessage());}}
}
