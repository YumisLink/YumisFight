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
    public EquipmentApplication(Journal journal,InventoryOperations inventory,Function<ServerPlayer,UUID> sessionId,Consumer<ServerPlayer> profile,BiConsumer<ServerPlayer,String> message){this.journal=journal;this.inventory=inventory;this.sessionId=sessionId;this.profile=profile;this.message=message;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("equipment","craft",this::craft);router.register("equipment","currency",this::receive);router.register("equipment","initializeEquipment",this::receive);}
    public void creativeArrived(ServerPlayer player,int menuSlot){Network.send(player,"equipmentArrival",Map.of("slot",menuSlot));}
    public void inventoryArrived(ServerPlayer player){Network.send(player,"equipmentArrival",Map.of());}
    public boolean versionlessCurrencyClick(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input){
        if(button==1&&input==ContainerInput.PICKUP&&slot>=0&&slot<menu.slots.size()&&SlotAccess.isCurrency(menu.getCarried())&&EquipmentItems.gear(menu.slots.get(slot).getItem())!=null)throw new IllegalArgumentException("加工操作未完成，请刷新后重试");
        return false;
    }
    private boolean replay(ServerPlayer p,JsonObject j){var previous=journal.get("inventoryReceipt/"+p.getUUID()+"/"+j.get("operation").getAsString());if(previous.isJsonNull())return false;if(!previous.getAsJsonObject().get("intent").equals(j))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);profile.accept(p);return true;}
    private void session(ServerPlayer p,JsonObject j){if(!sessionId.apply(p).toString().equals(j.get("session").getAsString()))throw new IllegalArgumentException("会话已失效");}
    public void craft(ServerPlayer p,JsonObject j){
        if(replay(p,j))return;session(p,j);String key="profession/"+p.getUUID()+"/craftsman";
        if(!JSON.toJsonTree(journal.get(key,Profession.class,new Profession())).equals(j.get("beforeProfession")))throw new IllegalArgumentException("副职业资料已更新，请重试");
        boolean committed=inventory.transact(p,j.get("operation").getAsString(),j,()->InventoryEdits.apply(p,inventory,j),Map.of(key,j.get("profession").deepCopy()));
        if(committed)message.accept(p,"制作成功，工匠经验 +60");profile.accept(p);
    }
    public void receive(ServerPlayer p,JsonObject j){if(replay(p,j))return;session(p,j);inventory.transact(p,j.get("operation").getAsString(),j,()->InventoryEdits.apply(p,inventory,j),Map.of());profile.accept(p);}
}
