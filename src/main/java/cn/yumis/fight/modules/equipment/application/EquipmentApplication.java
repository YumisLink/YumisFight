package cn.yumis.fight.modules.equipment.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.professions.api.Profession;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import java.util.*;
import java.util.function.*;

/** Receives deterministic owner-authored equipment results; no generation or processing runs here. */
public final class EquipmentApplication {
    private static final Gson JSON=new Gson();
    private final Journal journal;private final InventoryOperations inventory;private final Function<ServerPlayer,UUID> sessionId;private final Consumer<ServerPlayer> profile;private final BiConsumer<ServerPlayer,String> message;
    private final java.util.function.BiFunction<ServerPlayer,JsonObject,Map<String,JsonElement>> balanceReceiver;
    public EquipmentApplication(Journal journal,InventoryOperations inventory,Function<ServerPlayer,UUID> sessionId,Consumer<ServerPlayer> profile,BiConsumer<ServerPlayer,String> message,java.util.function.BiFunction<ServerPlayer,JsonObject,Map<String,JsonElement>> balanceReceiver){this.journal=journal;this.inventory=inventory;this.sessionId=sessionId;this.profile=profile;this.message=message;this.balanceReceiver=balanceReceiver;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("equipment","craft",this::craft);router.register("equipment","cubeCraft",(p,j)->professionCraft(p,j,false));router.register("equipment","currency",this::receive);router.register("equipment","enhance",this::enhance);router.register("equipment","initializeEquipment",this::receive);router.register("equipment","equipmentBagRefresh",(p,j)->sendBagSnapshot(p));}
    public void sendBagSnapshot(ServerPlayer p){
        if(!(p.containerMenu instanceof cn.yumis.fight.modules.equipment.api.EnhanceMenu||p.containerMenu instanceof cn.yumis.fight.modules.equipment.api.FabricatorMenu||p.containerMenu instanceof cn.yumis.fight.modules.equipment.api.HoradricCubeMenu))return;
        var menu=p.containerMenu;
        var extra=MenuHooks.extra(p);
        var rows=new JsonArray();
        int size=0,part=0;
        for(int slot=0;slot<extra.bagLimit();slot++){
            var stack=extra.bag.getItem(slot);
            if(stack.isEmpty())continue;
            var row=new JsonObject();
            row.addProperty("slot",slot);
            row.add("item",inventory.encode(p,stack));
            int length=row.toString().length();
            if(size+length>90000&&!rows.isEmpty()){sendBagPart(p,menu.containerId,extra,part++,false,rows);rows=new JsonArray();size=0;}
            rows.add(row);size+=length;
        }
        for(var entry:extra.categories.stock()){
            if(entry.kind()==CategoryBags.Kind.CURRENCY)continue;
            var row=new JsonObject();
            row.addProperty("area",entry.kind().id);
            row.addProperty("slot",entry.slot());
            row.add("item",inventory.encode(p,entry.item()));
            int length=row.toString().length();
            if(size+length>90000&&!rows.isEmpty()){sendBagPart(p,menu.containerId,extra,part++,false,rows);rows=new JsonArray();size=0;}
            rows.add(row);size+=length;
        }
        sendBagPart(p,menu.containerId,extra,part,true,rows);
    }
    private void sendBagPart(ServerPlayer p,int menuId,ExtraInventory extra,int part,boolean done,JsonArray rows){
        var value=new JsonObject();
        value.addProperty("session",sessionId.apply(p).toString());
        value.addProperty("menu",menuId);
        value.addProperty("tier",extra.bagTier);
        value.addProperty("part",part);
        value.addProperty("done",done);
        value.add("rows",rows);
        if(part==0)value.add("categories",extra.categories.summary());
        Network.send(p,"equipmentBag",value);
    }
    public void creativeArrived(ServerPlayer player,int menuSlot){Network.send(player,"equipmentArrival",Map.of("slot",menuSlot));}
    public void inventoryArrived(ServerPlayer player){Network.send(player,"equipmentArrival",Map.of());}
    public boolean versionlessCurrencyClick(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input){
        if(button==1&&input==ContainerInput.PICKUP&&slot>=0&&slot<menu.slots.size()&&SlotAccess.isCurrency(menu.getCarried())&&EquipmentItems.gear(menu.slots.get(slot).getItem())!=null)throw new IllegalArgumentException("加工操作未完成，请刷新后重试");
        return false;
    }
    private boolean replay(ServerPlayer p,JsonObject j){var previous=journal.get("inventoryReceipt/"+p.getUUID()+"/"+j.get("operation").getAsString());if(previous.isJsonNull())return false;if(!previous.getAsJsonObject().get("intent").equals(j))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);profile.accept(p);return true;}
    private void session(ServerPlayer p,JsonObject j){if(!sessionId.apply(p).toString().equals(j.get("session").getAsString()))throw new IllegalArgumentException("会话已失效");}
    public void craft(ServerPlayer p,JsonObject j){professionCraft(p,j,true);}
    private void professionCraft(ServerPlayer p,JsonObject j,boolean equipment){
        if(replay(p,j)){sendBagSnapshot(p);return;}session(p,j);String key="profession/"+p.getUUID()+"/craftsman";
        if(!JSON.toJsonTree(journal.get(key,Profession.class,new Profession())).equals(j.get("beforeProfession")))throw new IllegalArgumentException("副职业资料已更新，请重试");
        boolean committed=inventory.transact(p,j.get("operation").getAsString(),j,()->InventoryEdits.apply(p,inventory,j),Map.of(key,j.get("profession").deepCopy()));
        if(committed){message.accept(p,equipment?"制作成功，工匠经验 +60":"精华合成成功");if(equipment)Network.send(p,"equipmentCrafted",Map.of("session",sessionId.apply(p).toString(),"operation",j.get("operation").getAsString()));}profile.accept(p);sendBagSnapshot(p);
    }
    public void receive(ServerPlayer p,JsonObject j){if(replay(p,j))return;session(p,j);inventory.transact(p,j.get("operation").getAsString(),j,()->InventoryEdits.apply(p,inventory,j),Map.of());profile.accept(p);}
    /** 强化/增幅：金币快照经经济模块校验后与燃料、装备变更一并原子提交；结果回显由本人客户端自行演出。 */
    public void enhance(ServerPlayer p,JsonObject j){
        if(replay(p,j)){sendBagSnapshot(p);return;}session(p,j);
        var changes=new LinkedHashMap<>(balanceReceiver.apply(p,j));
        inventory.transact(p,j.get("operation").getAsString(),j,()->InventoryEdits.apply(p,inventory,j),changes);
        profile.accept(p);
        sendBagSnapshot(p);
    }
}
