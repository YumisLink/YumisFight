package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.professions.api.Profession;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Equipment generation, costs and eligibility run only on the owner's client. */
public final class EquipmentActions {
    private static final Random RANDOM=new Random();private static final Gson JSON=new Gson();
    private static Supplier<Progress> progress;private static Supplier<JsonObject> profile;private static Function<String,Profession> profession;
    private record Initialization(String operation,String source) {}
    private static final Map<String,Initialization> initializing=new HashMap<>();
    private record CreativeCurrency(String operation, InventoryMenu menu, ItemStack before, ItemStack after) {}
    private static CreativeCurrency creativeCurrency;
    private static String pendingCurrencyOp="";
    private record PendingEnhance(String operation,EquipmentChanges.EnhanceOutcome outcome) {}
    private static PendingEnhance pendingEnhance;
    private EquipmentActions(){}
    public static void clear(){initializing.clear();creativeCurrency=null;pendingCurrencyOp="";pendingEnhance=null;EnhanceClient.clear();BagSnapshotClient.clear();}
    public static void install(CommandRouter<Void,JsonObject> router,Supplier<Progress> p,Supplier<JsonObject> view,Function<String,Profession> professions){progress=p;profile=view;profession=professions;router.register("equipment","craft",(ignored,j)->craft(j));router.register("equipment","cubeCraft",(ignored,j)->cube(j));router.register("equipment","currency",(ignored,j)->currency(j));router.register("equipment","enhance",(ignored,j)->enhance(j));router.register("equipment","initializeEquipment",(ignored,j)->initialize(j));}
    private static void cube(JsonObject request){
        var player=Minecraft.getInstance().player;
        EquipmentChanges.prepareCube(player,profession.apply("craftsman"),request,BagSnapshotClient.items(player.containerMenu));
    }
    /** 当前账户快照（金币等），供强化界面做预算展示。 */
    public static JsonObject accountView(){return profile.get();}
    private static void enhance(JsonObject j){
        var player=Minecraft.getInstance().player;
        String operation=j.get("operation").getAsString();
        try{
            if(player==null)throw new IllegalArgumentException("强化界面已失效");
            var account=profile.get();
            long gold=account.has("gold")?account.get("gold").getAsLong():0L;
            long revision=account.has("goldRevision")?account.get("goldRevision").getAsLong():0L;
            var outcome=EquipmentChanges.prepareEnhance(player,j,gold,revision,RANDOM,BagSnapshotClient.items(player.containerMenu));
            pendingEnhance=new PendingEnhance(operation,outcome);
        }catch(RuntimeException failure){
            EnhanceClient.failed(operation);
            throw failure;
        }
    }
    public static void enhanceFinished(JsonObject request,boolean committed){
        String operation=request.get("operation").getAsString();
        var pending=pendingEnhance;
        if(pending!=null&&operation.equals(pending.operation())){
            pendingEnhance=null;
            if(committed)EnhanceClient.dispatched(operation,pending.outcome());
        }
        if(!committed)EnhanceClient.failed(operation);
    }
    private static void craft(JsonObject j){var player=Minecraft.getInstance().player;EquipmentChanges.prepareCraft(player,progress.get(),profile.get().get("craftAllowed").getAsBoolean(),profession.apply("craftsman"),j,RANDOM,BagSnapshotClient.items(player==null?null:player.containerMenu));}
    private static void currency(JsonObject request) {
        String operation = request.get("operation").getAsString();
        pendingCurrencyOp = operation;
        try {
            var player = Minecraft.getInstance().player;
            boolean creative = request.has("creativeCursor");
            if (player == null || creative != (player.containerMenu instanceof CreativeModeInventoryScreen.ItemPickerMenu)) {
                throw new IllegalArgumentException("加工界面已变化");
            }
            ItemStack remainder = EquipmentChanges.prepareCurrency(player, request, RANDOM);
            if (creative) {
                var menu = player.inventoryMenu;
                creativeCurrency = new CreativeCurrency(operation, menu, menu.getCarried().copy(), remainder);
            }
        } catch (RuntimeException failure) {
            if (operation.equals(pendingCurrencyOp)) pendingCurrencyOp = "";
            throw failure;
        }
    }
    public static void beginCurrency(String operation) {
        pendingCurrencyOp = operation == null ? "" : operation;
    }
    public static boolean currencyPending() {
        return creativeCurrency != null || !pendingCurrencyOp.isEmpty();
    }
    public static boolean creativeCurrencyPending() {
        return currencyPending();
    }
    public static void currencyFinished(JsonObject request, boolean committed) {
        String operation = request.get("operation").getAsString();
        if (operation.equals(pendingCurrencyOp)) pendingCurrencyOp = "";
        var pending = creativeCurrency;
        if (pending == null || !pending.operation().equals(operation)) return;
        creativeCurrency = null;
        if (committed && ItemStack.matches(pending.menu().getCarried(), pending.before())) {
            pending.menu().setCarried(pending.after());
        }
    }
    private static void initialize(JsonObject j){
        var player=Minecraft.getInstance().player;String area=j.get("area").getAsString();int index=j.get("slot").getAsInt();var source=InventoryEdits.source(player,area,index);ItemStack generated;
        var gear=EquipmentItems.gear(source);
        if(gear!=null&&gear.creativeTemplate)generated=CreativeEquipment.materialize(source,RANDOM);
        else if(!source.isEmpty()&&ItemData.own(source)&&EquipmentItems.GEAR_IDS.contains(ItemData.id(source))&&EquipmentItems.serialized(source).isEmpty()){
            String id=ItemData.id(source);Slot slot=rawSlot(id);Weapon weapon=Arrays.stream(Weapon.values()).filter(w->w.name().equalsIgnoreCase(id)).findFirst().orElse(Weapon.KATANA);
            if(slot==Slot.WEAPON&&!Weapon.standard().contains(weapon)||progress.get().level<slot.minimum())throw new IllegalArgumentException("该装备尚不能初始化");generated=source.copy();EquipmentItems.write(generated,EquipmentTemplates.createStandard(slot,weapon,Rarity.COMMON,progress.get().level,RANDOM));
        }else{j.add("inventoryChanges",new JsonArray());return;}
        var edits=new JsonArray();edits.add(InventoryEdits.change(player,area,index,source,generated));j.add("inventoryChanges",edits);
    }
    private static Slot rawSlot(String id){
        return Arrays.stream(Slot.values()).filter(slot->slot.item().equals(id)).findFirst().orElse(Slot.WEAPON);
    }
    public static void initializeFinished(JsonObject request,boolean committed){
        if(committed)return;
        String key=request.get("area").getAsString()+"/"+request.get("slot").getAsInt();
        var pending=initializing.get(key);
        if(pending!=null&&pending.operation().equals(request.get("operation").getAsString()))initializing.remove(key);
    }
    public static void tick(){
        var player=Minecraft.getInstance().player;if(player==null||!ClientUi.current().ready()){initializing.clear();return;}
        int level=progress.get().level;
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);String key="inventory/"+i;var view=EquipmentItems.view(stack);
            boolean needed=view!=null&&view.creativeTemplate()||!stack.isEmpty()&&ItemData.own(stack)&&EquipmentItems.GEAR_IDS.contains(ItemData.id(stack))&&EquipmentItems.serialized(stack).isEmpty()&&level>=rawSlot(ItemData.id(stack)).minimum();
            if(!needed){initializing.remove(key);continue;}
            String source=InventoryEdits.encode(player,stack)+"/"+level+"/"+EquipmentDefinitions.current().revision;
            var pending=initializing.get(key);
            if(pending!=null&&source.equals(pending.source()))continue;
            var j=ClientUi.current().operation("initializeEquipment");
            j.addProperty("area","inventory");j.addProperty("slot",i);
            initializing.put(key,new Initialization(j.get("operation").getAsString(),source));
            ClientUi.current().send("initializeEquipment",j);
        }
    }
}
