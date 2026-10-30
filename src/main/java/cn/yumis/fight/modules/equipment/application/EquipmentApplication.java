package cn.yumis.fight.modules.equipment.application;
import cn.yumis.fight.modules.equipment.api.Gem;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.FabricatorMenu;
import cn.yumis.fight.modules.inventory.api.SlotAccess;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.equipment.api.ArmorType;
import cn.yumis.fight.modules.equipment.api.JewelryType;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;

import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import static cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON;
import static cn.yumis.fight.modules.equipment.api.EquipmentJson.copy;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;

import cn.yumis.fight.platform.Permissions;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.professions.api.Profession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.*;
/** equipment application entry. Dependencies are explicit ports, not the global runtime. */
public final class EquipmentApplication {
    private final Journal journal;
    private final InventoryOperations inventory;
    private final Function<ServerPlayer,Progress> progressOf;
    private final Function<ServerPlayer,UUID> sessionId;
    private final BiFunction<ServerPlayer,String,Profession> professionOf;
    private final Consumer<ServerPlayer> profile;
    private final BiConsumer<ServerPlayer,String> message;
    private final Random random;
    private record CreativeSlot(UUID player,int slot){}
    private record CreativeArrival(ServerPlayer player,int slot,ItemStack expected,long due){}
    private final Map<CreativeSlot,CreativeArrival> creativeArrivals=new LinkedHashMap<>();
    private long creativeTick;
    public EquipmentApplication(Journal journal,InventoryOperations inventory,Function<ServerPlayer,Progress> progressOf,Function<ServerPlayer,UUID> sessionId,BiFunction<ServerPlayer,String,Profession> professionOf,Consumer<ServerPlayer> profile,BiConsumer<ServerPlayer,String> message,Random random){this.journal=journal;this.inventory=inventory;this.progressOf=progressOf;this.sessionId=sessionId;this.professionOf=professionOf;this.profile=profile;this.message=message;this.random=random;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("equipment","craft",this::craft);router.register("equipment","currency",this::currencyRequest);}
    /** Called after vanilla accepts a creative slot, never while reading the incoming template. */
    public void creativeArrived(ServerPlayer player,int menuSlot){
        if(!player.isCreative()||menuSlot<0||menuSlot>=player.inventoryMenu.slots.size())return;
        var slot=player.inventoryMenu.getSlot(menuSlot);if(slot.container!=player.getInventory())return;
        queueCreativeSlot(player,slot.getContainerSlot());
    }
    /** Rechecks actual ownership after inventory commits or reconnect, including a template moved before its callback. */
    public void inventoryArrived(ServerPlayer player){for(int index=0;index<player.getInventory().getContainerSize();index++)queueCreativeSlot(player,index);}
    private void queueCreativeSlot(ServerPlayer player,int index){
        var key=new CreativeSlot(player.getUUID(),index);var stack=player.getInventory().getItem(index);var view=EquipmentItems.view(stack);
        if(view==null||!view.creativeTemplate()){creativeArrivals.remove(key);return;}
        var previous=creativeArrivals.get(key);if(previous==null||!ItemStack.matches(previous.expected,stack))creativeArrivals.put(key,new CreativeArrival(player,index,stack.copy(),creativeTick+1));
    }
    public void tickCreativeArrivals(){
        creativeTick++;
        for(var iterator=creativeArrivals.values().iterator();iterator.hasNext();){
            var arrival=iterator.next();if(arrival.due>creativeTick)continue;var player=arrival.player;
            if(player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player){iterator.remove();continue;}
            if(inventory.locked(player.getUUID()))continue;iterator.remove();
            var stack=player.getInventory().getItem(arrival.slot);if(!ItemStack.matches(stack,arrival.expected))continue;
            var gear=EquipmentItems.gear(stack);if(gear==null||!gear.creativeTemplate)continue;
            var generated=CreativeEquipment.materialize(stack,random);if(generated==stack)continue;
            player.getInventory().setItem(arrival.slot,generated);player.getInventory().setChanged();
            // The creative cursor belongs to the client. Never publish a full container here.
            player.connection.send(player.getInventory().createInventoryUpdatePacket(arrival.slot));
        }
    }

    public void craft(ServerPlayer p,JsonObject j){
        requireRuleRevision(p,j);
        Progress progress=progressOf.apply(p);
        if(!(p.containerMenu instanceof FabricatorMenu m)||!m.stillValid(p))throw new IllegalArgumentException("请使用装备制作器");
        if(!Permissions.allowed(p.getUUID(),"craft"))throw new IllegalArgumentException("没有制作权限");
        Slot slot=Slot.valueOf(j.get("slot").getAsString());
        int level=j.get("level").getAsInt();
        if(level>progress.level)throw new IllegalArgumentException("制作等级超过角色等级");
        Weapon weapon=Weapon.valueOf(j.get("weapon").getAsString());
        ArmorType armorType=j.has("armorType")?ArmorType.valueOf(j.get("armorType").getAsString()):ArmorType.UNTYPED;
        JewelryType jewelryType=j.has("jewelryType")?JewelryType.valueOf(j.get("jewelryType").getAsString()):JewelryType.UNTYPED;
        if(slot.armor()&&(armorType==ArmorType.UNTYPED||jewelryType!=JewelryType.UNTYPED)||slot.jewelry()&&(jewelryType==JewelryType.UNTYPED||armorType!=ArmorType.UNTYPED)||!slot.armor()&&!slot.jewelry()&&(armorType!=ArmorType.UNTYPED||jewelryType!=JewelryType.UNTYPED))throw new IllegalArgumentException("请选择有效装备类型");
        List<MaterialCatalog.Need> needs=MaterialCatalog.current().recipe(slot,level);
        String op=j.get("operation").getAsString();
        Profession prof=professionOf.apply(p,"craftsman");
        prof.award(60,false);
        Map<String,JsonElement> edits=Map.of("profession/"+p.getUUID()+"/craftsman",JSON.toJsonTree(prof));
        boolean applied=inventory.transact(p,op,j,()->{
            for(var need:needs){
                int total=0;for(int i=0;i<36;i++){
                    ItemStack stack=p.getInventory().getItem(i);if(matches(stack,need))total+=stack.getCount();
                }
                if(total<need.count())throw new IllegalArgumentException("材料不足："+(need.regional()?MaterialCatalog.current().materials.get(need.id()).name():need.id()));
            }
            for(var need:needs){
                int remaining=need.count();for(int i=0;i<36&&remaining>0;i++){
                    ItemStack stack=p.getInventory().getItem(i);if(matches(stack,need)){
                        int n=Math.min(remaining,stack.getCount());stack.shrink(n);remaining-=n;
                    }
                }
            }
            inventory.requireSpace(p,EquipmentItems.gear(EquipmentRules.create(slot,weapon,Rarity.COMMON,level,armorType,jewelryType,random)));
        }
        ,edits);
        if(applied)message.accept(p,"制作成功，工匠经验 +60");
        profile.accept(p);
    }
    public boolean matches(ItemStack stack,MaterialCatalog.Need need){
        return !stack.isEmpty()&&(need.regional()?EquipmentItems.material(stack).equals(need.id()):BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(need.id()));
    }
    public boolean currencyClick(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input,int socket){
        if(button!=1||input!=ContainerInput.PICKUP||slot<0||slot>=menu.slots.size())return false;
        ItemStack cursor=menu.getCarried(),stack=menu.slots.get(slot).getItem();
        Gear gear=EquipmentItems.gear(stack);
        if(gear==null||cursor.isEmpty())return false;
        String id=ItemData.id(cursor);
        if(!SlotAccess.isCurrency(cursor))return false;
        SlotAccess.requireWritable(p,menu,slot);
        if(gear.corrupted&&!id.equals("affix_gem"))throw new IllegalArgumentException("腐化装备仅允许继续镶嵌宝石");
        if(id.equals("set_extraction_stone")){
            if(gear.set.isEmpty())throw new IllegalArgumentException("该装备没有套装");
            ItemStack stone=EquipmentItems.payload("set_stone",Map.of("set",gear.set,"level",gear.level,"group",gear.slot.armor()?"armor":"jewelry"),"套装石 · "+EquipmentDefinitions.current().sets.get(gear.set).name());
            menu.slots.get(slot).set(ItemStack.EMPTY);
            cursor.shrink(1);
            inventory.requireSpace(p,stone);
            return true;
        }
        if(id.equals("set_stone")){
            JsonObject payload=JsonParser.parseString(EquipmentItems.payload(cursor)).getAsJsonObject();
            boolean armor=payload.get("group").getAsString().equals("armor");
            if(gear.level<payload.get("level").getAsInt()||armor&&!gear.slot.armor()||!armor&&!gear.slot.jewelry())throw new IllegalArgumentException("套装石部位或来源等级不符");
            String setId=payload.get("set").getAsString();
            var definition=EquipmentDefinitions.current().sets.get(setId);
            if(definition==null||!definition.group().equals(payload.get("group").getAsString()))throw new IllegalArgumentException("套装定义或分类不符");
            gear.set=setId;
            gear.revision++;
        }
        else if(id.equals("affix_gem")){
            Gem gem=JSON.fromJson(EquipmentItems.payload(cursor),Gem.class);
            gear=EquipmentRules.socket(gear,gem,socket>=0?socket:gear.gems.size()<gear.sockets?gear.gems.size():0);
        }
        else if(id.equals("affix_extraction_stone")){
            Gem gem=GemExtraction.create(gear);ItemStack output=EquipmentItems.payload("affix_gem",gem,gem.name);
            menu.slots.get(slot).set(ItemStack.EMPTY);cursor.shrink(1);inventory.requireSpace(p,output);return true;
        }
        else gear=EquipmentRules.process(gear,id,random);
        ItemStack updated=stack.copy();
        EquipmentItems.write(updated,gear);
        menu.slots.get(slot).set(updated);
        cursor.shrink(1);
        return true;
    }
    public void requireRuleRevision(ServerPlayer p,JsonObject request){
        if(!request.has("rulesRevision")||request.get("rulesRevision").getAsLong()!=EquipmentDefinitions.current().revision){profile.accept(p);throw new IllegalArgumentException("装备规则已更新，请刷新界面后重试");}
    }
    public boolean versionlessCurrencyClick(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input){
        if(button==1&&input==ContainerInput.PICKUP&&slot>=0&&slot<menu.slots.size()&&SlotAccess.isCurrency(menu.getCarried())&&EquipmentItems.gear(menu.slots.get(slot).getItem())!=null){
            profile.accept(p);throw new IllegalArgumentException("加工请求缺少规则版本，请刷新界面后重试");
        }
        return false;
    }
    public void currencyRequest(ServerPlayer p,JsonObject j){
        JsonElement prior=journal.get("inventoryReceipt/"+p.getUUID()+"/"+j.get("operation").getAsString());
        if(!prior.isJsonNull()){
            if(!prior.getAsJsonObject().get("intent").equals(j))throw new IllegalArgumentException("操作号冲突");
            inventory.restore(p);
            profile.accept(p);
            return;
        }
        requireRuleRevision(p,j);
        if(!j.get("session").getAsString().equals(sessionId.apply(p).toString())||j.get("menu").getAsInt()!=p.containerMenu.containerId)throw new IllegalArgumentException("加工界面或会话已失效");
        int index=j.get("slot").getAsInt();
        var targetSlot=SlotAccess.requireWritable(p,p.containerMenu,index);
        Gear g=EquipmentItems.gear(targetSlot.getItem());
        if(g==null||!g.instance.toString().equals(j.get("equipment").getAsString())||g.revision!=j.get("revision").getAsInt()||!j.has("cursor")||!inventory.encode(p,p.containerMenu.getCarried()).equals(j.get("cursor")))throw new IllegalArgumentException("装备或通货已发生变化");
        inventory.transact(p,j.get("operation").getAsString(),j,()->{
            if(!currencyClick(p,p.containerMenu,index,1,ContainerInput.PICKUP,j.has("socket")?j.get("socket").getAsInt():-1))throw new IllegalArgumentException("无法加工");
        }
        ,Map.of());
        profile.accept(p);
    }
}
