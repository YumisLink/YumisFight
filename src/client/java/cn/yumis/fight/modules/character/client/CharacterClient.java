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
import net.minecraft.world.item.ItemStack;
import java.util.function.*;

/** Owns eligibility, growth and consumable confirmations on the player's machine. */
public final class CharacterClient {
    private static final Gson JSON=new Gson();
    private static Supplier<Progress> progress;private static Supplier<Stats> stats;private static Supplier<ProfileConditions> conditions;
    private static LongSupplier combatUntil;private static SkillProgression skills;private static Function<String,ItemStack> starter;
    private static CareerProfiles book;
    private CharacterClient(){}
    public static void install(CommandRouter<Void,JsonObject> preparation,Supplier<Progress> p,Supplier<Stats> s,Supplier<ProfileConditions> c,LongSupplier combat,SkillProgression learning,Function<String,ItemStack> equipment){
        progress=p;stats=s;conditions=c;combatUntil=combat;skills=learning;starter=equipment;
        preparation.register("character","progress",(ignored,j)->prepare(j));
        CharacterActions.install((player,ticket)->{if(player.level().isClientSide())use(ticket);});
    }
    public static void receive(JsonObject profile){book=JSON.fromJson(profile.getAsJsonObject("careerProfiles").get("state"),CareerProfiles.class);}
    public static void clear(){book=null;}
    public static void safe(){if(ClientUi.now()<combatUntil.getAsLong())throw new IllegalArgumentException("请脱战后操作");conditions.get().requireReady();}
    public static String blockReason(){try{safe();return "";}catch(IllegalArgumentException failure){return failure.getMessage();}}
    private static void prepare(JsonObject j){
        if(book==null)throw new IllegalArgumentException("角色资料尚未就绪");
        String kind=j.get("kind").getAsString();if(!kind.equals("bind")&&!kind.equals("renameProfile")&&ClientUi.now()<combatUntil.getAsLong())throw new IllegalArgumentException("请脱战后操作");
        var player=Minecraft.getInstance().player;var current=progress.get();if(j.has("confirmedLevel")&&(j.get("confirmedLevel").getAsInt()!=current.level||ClientUi.now()>j.get("confirmUntil").getAsLong()))throw new IllegalArgumentException("用券确认已过期，请重新使用券");var value=CharacterChanges.apply(current,book,stats.get(),j,skills,CharacterClient::safe);
        j.add("beforeProgress",JSON.toJsonTree(current));j.addProperty("beforeProfilesKey",book.stateKey());j.add("progress",JSON.toJsonTree(value.progress()));j.add("profiles",book.delta(value.profiles()));
        var edits=new JsonArray();
        if(!value.ticket().isEmpty()){
            int slot=j.has("ticketSlot")?j.get("ticketSlot").getAsInt():player.getInventory().getSelectedSlot();var source=player.getInventory().getItem(slot);
            if(source.isEmpty()||!cn.yumis.fight.platform.ItemData.id(source).equals(value.ticket()))throw new IllegalArgumentException("券已移动或不存在");
            if(j.has("ticketSource")&&!InventoryEdits.encode(player,source).equals(j.get("ticketSource")))throw new IllegalArgumentException("确认的券已变化，请重新使用");
            var after=source.copy();after.shrink(1);edits.add(InventoryEdits.change(player,"inventory",slot,source,after));
        }
        j.add("inventoryChanges",edits);var outputs=new JsonArray();if(value.starter())outputs.add(InventoryEdits.encode(player,starter.apply(value.progress().jobId())));j.add("outputs",outputs);
    }
    private static void use(CharacterActions.Ticket ticket){try{
        var player=Minecraft.getInstance().player;if(player==null||!ClientUi.current().ready())return;
        String kind=switch(ticket){case CAREER_UNLOCK->"unlockCareer";case AWAKENING->"unlockAwakening";case PROFILE_EXPANSION->"expandProfile";case LEVEL_ONE->"levelOne";case LEVEL_TEN->"levelTen";case LEVEL_MAX->"levelMax";};
        var j=ClientUi.current().operation(kind);int slot=player.getInventory().getSelectedSlot();j.addProperty("ticketSlot",slot);j.add("ticketSource",InventoryEdits.encode(player,player.getInventory().getItem(slot)));
        if(ticket==CharacterActions.Ticket.CAREER_UNLOCK||ticket==CharacterActions.Ticket.AWAKENING){ClientUi.current().send("progress",j);return;}
        safe();var p=progress.get();String text;
        if(ticket==CharacterActions.Ticket.PROFILE_EXPANSION){if(book.capacity==Integer.MAX_VALUE)throw new IllegalArgumentException("职业配置容量已达上限，未消耗券");text="消耗1张职业栏拓展券，将职业配置容量从"+book.capacity+"增加至"+(book.capacity+1)+"；解锁后新栏位可直接使用。";}
        else{if(p.level>=CharacterRules.MAX_LEVEL)throw new IllegalArgumentException("当前配置已经满级，未消耗券");int target=kind.equals("levelMax")?CharacterRules.MAX_LEVEL:Math.min(CharacterRules.MAX_LEVEL,p.level+(kind.equals("levelTen")?10:1));text="当前配置从"+p.level+"级升至"+target+"级，经验清零；消耗整张券。";}
        j.addProperty("confirmedLevel",p.level);j.addProperty("confirmUntil",ClientUi.now()+60000);var mc=Minecraft.getInstance();var parent=mc.gui.screen();mc.gui.setScreen(new ThemedConfirmScreen(yes->{mc.gui.setScreen(parent);if(yes)ClientUi.current().send("progress",j);},Component.literal(ticket==CharacterActions.Ticket.PROFILE_EXPANSION?"拓展职业配置容量？":"使用升级券？"),Component.literal(text),ThemeDomain.MENU));
    }catch(RuntimeException failure){ClientUi.current().message(failure.getMessage());}}
}
