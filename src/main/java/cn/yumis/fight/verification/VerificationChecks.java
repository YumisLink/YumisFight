package cn.yumis.fight.verification;
import cn.yumis.fight.bootstrap.ServerApplication;
import cn.yumis.fight.modules.equipment.api.FabricatorMenu;
import cn.yumis.fight.modules.inventory.api.SlotAccess;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.inventory.api.ExtraInventory;
import cn.yumis.fight.modules.economy.api.ItemRarity;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixes;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.ArmorType;
import cn.yumis.fight.modules.equipment.api.JewelryType;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import static cn.yumis.fight.modules.character.api.CharacterRules.spAt;
import cn.yumis.fight.modules.economy.api.CurrencyRules;

import cn.yumis.fight.modules.combat.application.BasicAttackService;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.equipment.api.Gem;
import cn.yumis.fight.modules.equipment.api.Gear;
import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;
import static cn.yumis.fight.bootstrap.ApplicationJson.copy;
import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.nio.file.*;
/** Exercises real world registration, native saves and production transactions in isolated dev runs. */
public final class VerificationChecks {
    private final ServerApplication runtime;
    private final ServerPlayer player;
    private final List<String> passed=new ArrayList<>();
    private final String prefix="verify/"+UUID.randomUUID()+"/";
    private int sequence;
    private VerificationChecks(ServerApplication runtime,ServerPlayer player){
        this.runtime=runtime;
        this.player=player;
    }
    public static int run(ServerApplication runtime,ServerPlayer player){
        if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalArgumentException("仅隔离开发实例可运行验证");
        return new VerificationChecks(runtime,player).run();
    }
    private void check(boolean condition,String name){
        if(!condition)throw new IllegalStateException("验证失败："+name);
        passed.add(name);
    }
    private void mutate(Runnable action){
        JsonObject j=new JsonObject();
        j.addProperty("verification",prefix+sequence);
        runtime.inventory.transact(player,prefix+sequence++,j,action,Map.of());
    }
    private void clear(){
        mutate(()->{
            player.getInventory().clearContent();player.containerMenu.setCarried(ItemStack.EMPTY);MenuHooks.extra(player).clearContent();MenuHooks.extra(player).bag.clearContent();for(int i=1;i<=4;i++)player.inventoryMenu.getSlot(i).set(ItemStack.EMPTY);
        }
        );
    }
    private long regional(String id){
        long count=0;
        for(int i=0;i<36;i++){
            var s=player.getInventory().getItem(i);
            if(id.equals(EquipmentItems.material(s)))count+=s.getCount();
        }
        return count;
    }
    private long count(String id){
        long count=0;
        for(int i=0;i<36;i++){
            var s=player.getInventory().getItem(i);
            if(id.equals(ItemData.id(s)))count+=s.getCount();
        }
        return count;
    }
    private JsonObject dismantleRequest(int slot){
        var request=new JsonObject();request.addProperty("session",runtime.session(player).id.toString());String token=runtime.economyService.saleOpen(player,request);
        request.addProperty("operation",prefix+sequence++);request.addProperty("menu",player.inventoryMenu.containerId);request.addProperty("token",token);var items=new JsonArray();items.add("inventory/"+slot);request.add("items",items);return request;
    }
    private long droppedSince(Set<String> previous,String item){
        long total=0;for(String key:runtime.journal.keys("loot/"))if(!previous.contains(key)){var value=runtime.journal.get(key);if(value.isJsonObject()&&value.getAsJsonObject().has("item")){var stack=runtime.inventory.decode(player,value.getAsJsonObject().get("item"));if(ItemData.id(stack).equals(item))total+=stack.getCount();}}return total;
    }
    private JsonObject craft(Slot slot,int level){
        JsonObject j=new JsonObject();
        j.addProperty("rulesRevision",EquipmentDefinitions.current().revision);
        j.addProperty("operation",prefix+sequence++);
        j.addProperty("slot",slot.name());
        j.addProperty("weapon",Weapon.KATANA.name());
        j.addProperty("armorType",slot.armor()?ArmorType.CLOTH.name():ArmorType.UNTYPED.name());
        j.addProperty("jewelryType",slot.jewelry()?JewelryType.HEALTH.name():JewelryType.UNTYPED.name());
        j.addProperty("level",level);
        return j;
    }
    private JsonObject currency(int inventoryIndex,Integer socket){
        var g=EquipmentItems.gear(player.getInventory().getItem(inventoryIndex));
        JsonObject j=new JsonObject();
        j.addProperty("rulesRevision",EquipmentDefinitions.current().revision);
        j.addProperty("operation",prefix+sequence++);
        j.addProperty("session",runtime.session(player).id.toString());
        j.addProperty("menu",player.inventoryMenu.containerId);
        j.addProperty("slot",inventoryIndex<9?36+inventoryIndex:inventoryIndex);
        j.addProperty("equipment",g.instance.toString());
        j.addProperty("revision",g.revision);
        j.addProperty("count",player.inventoryMenu.getCarried().getCount());
        j.add("cursor",runtime.inventory.encode(player,player.inventoryMenu.getCarried()));
        if(socket!=null)j.addProperty("socket",socket);
        return j;
    }
    private void rejects(Runnable action,String label){
        boolean rejected=false;
        try{
            action.run();
        }
        catch(IllegalArgumentException|IllegalStateException e){
            rejected=true;
        }
        check(rejected,label);
    }
    private String inventory(){
        return JSON.toJson(runtime.inventory.capture(player,"comparison"));
    }
    private void currencies(){
        for(var currency:CurrencyRules.ALL){
            ItemStack stack=ItemRegistry.item(currency.id(),2);
            check(SlotAccess.isCurrency(stack)&&ItemRarity.rarity(stack)==currency.rarity()
                &&stack.getHoverName().getStyle().getColor()!=null&&stack.getHoverName().getStyle().getColor().getValue()==currency.rarity().color(),"currency_registered_rarity_and_name_"+currency.id());
        }
        Gear gear=EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.COMMON,35,new Random(91));
        gear.rarity=Rarity.LEGENDARY;gear.sockets=1;
        gear.affixes.add(new Affix("flat_strength",Stat.STRENGTH,10,false));
        gear.affixes.add(new Affix("flat_health",Stat.HEALTH,20,false));
        Affix fixed=new Affix("fixed_shield",Stat.SHIELD,5,false);fixed.fixed=true;gear.affixes.add(fixed);
        Gem gem=new Gem();gem.name="锁定保留宝石";gear.gems.add(gem);
        clear();
        mutate(()->{player.getInventory().setItem(0,EquipmentItems.gear(gear));player.inventoryMenu.setCarried(ItemRegistry.item("fracturing_orb",2));});
        JsonObject fracture=currency(0,null);
        runtime.equipmentApplication.currencyRequest(player,fracture);
        Gear first=EquipmentItems.gear(player.getInventory().getItem(0));
        check(first.affixes.stream().filter(a->a.locked).count()==1&&player.inventoryMenu.getCarried().getCount()==1,"fracturing_locks_one_and_consumes_one");
        runtime.equipmentApplication.currencyRequest(player,fracture);
        check(player.inventoryMenu.getCarried().getCount()==1&&EquipmentItems.gear(player.getInventory().getItem(0)).revision==first.revision,"duplicate_fracturing_does_not_lock_twice");
        runtime.equipmentApplication.currencyRequest(player,currency(0,null));
        Gear locked=EquipmentItems.gear(player.getInventory().getItem(0));
        check(locked.affixes.stream().filter(a->a.locked).count()==2&&player.inventoryMenu.getCarried().isEmpty(),"fracturing_can_lock_every_random_affix");
        ItemStack restored=runtime.inventory.decode(player,runtime.inventory.encode(player,player.getInventory().getItem(0)));
        check(JSON.toJson(locked).equals(JSON.toJson(EquipmentItems.gear(restored))),"locked_affixes_survive_native_item_codec");
        mutate(()->player.inventoryMenu.setCarried(ItemRegistry.item("fracturing_orb",2)));
        String before=inventory();
        rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,null)),"all_locked_fracturing_rejected");
        check(before.equals(inventory()),"failed_fracturing_keeps_equipment_and_full_cursor");
        mutate(()->player.inventoryMenu.setCarried(ItemRegistry.item("chaos_stone",2)));
        before=inventory();
        rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,null)),"all_locked_chaos_rejected");
        check(before.equals(inventory()),"failed_chaos_keeps_equipment_and_full_cursor");
        Gear eligible=copy(locked,Gear.class);
        int replace=-1;
        for(int i=0;i<eligible.affixes.size();i++)if(eligible.affixes.get(i).locked){replace=i;break;}
        eligible.affixes.get(replace).locked=false;
        mutate(()->player.getInventory().setItem(0,EquipmentItems.gear(eligible)));
        JsonObject chaos=currency(0,null);
        runtime.equipmentApplication.currencyRequest(player,chaos);
        Gear changed=EquipmentItems.gear(player.getInventory().getItem(0));
        check(changed.instance.equals(eligible.instance)&&changed.revision==eligible.revision+1&&changed.affixes.size()==eligible.affixes.size()
            &&player.inventoryMenu.getCarried().getCount()==1,"chaos_preserves_identity_count_and_consumes_one");
        for(int i=0;i<eligible.affixes.size();i++)if(i!=replace)check(JSON.toJson(eligible.affixes.get(i)).equals(JSON.toJson(changed.affixes.get(i))),"chaos_preserves_other_affix_"+i);
        check(JSON.toJson(eligible.gems).equals(JSON.toJson(changed.gems)),"chaos_preserves_embedded_gems");
        runtime.equipmentApplication.currencyRequest(player,chaos);
        check(player.inventoryMenu.getCarried().getCount()==1&&JSON.toJson(changed).equals(JSON.toJson(EquipmentItems.gear(player.getInventory().getItem(0)))),"duplicate_chaos_returns_same_result_without_reroll");
    }
    private int run(){
        player.closeContainer();
        var original=runtime.inventory.capture(player,"restore");
        var session=runtime.session(player);
        Progress originalProgress=copy(session.progress,Progress.class);
        cn.yumis.fight.modules.combat.api.Runtime originalResources=copy(session.resources,cn.yumis.fight.modules.combat.api.Runtime.class);
        Map<String,JsonElement> originals=new HashMap<>();
        for(String key:List.of("player/"+player.getUUID(),"resources/"+player.getUUID(),"slotBonus/"+player.getUUID(),"equipmentWaiting/"+player.getUUID(),"profession/"+player.getUUID()+"/craftsman"))originals.put(key,runtime.journal.get(key));
        var pos=player.blockPosition().east();
        var block=player.level().getBlockState(pos);
        Throwable failure=null;
        try{
            cn.yumis.fight.modules.combat.api.Runtime forged=copy(session.resources,cn.yumis.fight.modules.combat.api.Runtime.class);
            forged.training=!session.resources.training;
            forged.trainingBoundary=999999;
            JsonObject forgedUpload=new JsonObject();
            forgedUpload.addProperty("session",session.id.toString());forgedUpload.addProperty("generation",session.life.generation);
            forgedUpload.addProperty("serverIssuedAt",Math.max(System.currentTimeMillis(),session.resources.recordedAt));
            forgedUpload.add("state",JSON.toJsonTree(forged));runtime.combatApplication.runtime(player,forgedUpload);
            check(session.resources.training==originalResources.training,"client_runtime_cannot_enable_training_or_bypass_stamina_cost");
            check(session.resources.trainingBoundary==originalResources.trainingBoundary,"client_runtime_cannot_replace_administrator_training_boundary");
            var adminBefore=runtime.professionService.get(player.getUUID(),"craftsman");
            String adminOperation=prefix+"administrator";
            runtime.professionService.award(player.getUUID(),"craftsman",35,adminOperation,true);
            check(runtime.professionService.get(player.getUUID(),"craftsman").experience.equals(adminBefore.experience.add(java.math.BigInteger.valueOf(35)))&&runtime.professionService.get(player.getUUID(),"craftsman").coins.equals(adminBefore.coins),"administrator_profession_xp_without_coins");
            runtime.professionService.award(player.getUUID(),"craftsman",35,adminOperation,true);
            check(runtime.professionService.get(player.getUUID(),"craftsman").experience.equals(adminBefore.experience.add(java.math.BigInteger.valueOf(35))),"duplicate_profession_award_is_idempotent");
            rejects(()->runtime.professionService.award(player.getUUID(),"craftsman",36,adminOperation,true),"conflicting_profession_award_rejected");
            runtime.journal.put("profession/"+player.getUUID()+"/craftsman",adminBefore);
            var boundInventory=MenuHooks.extra(player);
            var curios=top.theillusivec4.curios.api.CuriosApi.getCuriosInventoryOrNull(player);
            check(curios!=null&&java.util.Arrays.stream(ExtraInventory.SLOTS).allMatch(slot->curios.getStacksHandler("ydnf_"+slot).map(handler->handler.getStacks().getSlots()>=1).orElse(false)),"curios_registers_six_real_accessory_slots");
            check(player.inventoryMenu.slots.stream().noneMatch(slot->slot.container instanceof ExtraInventory),"legacy_inventory_slots_removed");
            session.progress=new Progress();
            session.progress.level=80;
            session.progress.sp=spAt(80);
            runtime.savePlayer(player);
            clear();
            Gear shiftBracelet=EquipmentRules.create(Slot.BRACELET,Weapon.KATANA,Rarity.COMMON,10,new Random(11));
            ItemStack bracelet=EquipmentItems.gear(shiftBracelet);
            check(boundInventory.canPlaceItem(0,bracelet),"curios_accepts_level_valid_bracelet");
            mutate(()->boundInventory.setItem(0,bracelet));
            check(EquipmentItems.gear(curios.getStacksHandler("ydnf_bracelet").orElseThrow().getStacks().getStackInSlot(0)).instance.equals(shiftBracelet.instance),"curios_is_accessory_source_of_truth");
            player.level().setBlock(pos,cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR.get().defaultBlockState(),3);
            openFabricator(pos);
            check(player.containerMenu.stillValid(player),"fabricator_real_block_and_menu");
            clear();
            mutate(()->{
                player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(Items.STICK,64));player.getInventory().setItem(2,EquipmentItems.material("goblin_ingot",64));
            }
            );
            var profession=runtime.professionService.get(player.getUUID(),"craftsman");
            var oldXp=profession.experience;
            var oldCoins=profession.coins;
            JsonObject request=craft(Slot.WEAPON,10);
            runtime.equipmentApplication.craft(player,request);
            check(regional("goblin_ingot")==40,"craft_weapon_regional_fixed_24");
            check(count("iron_ingot")==62&&count("stick")==63,"craft_vanilla_q_formula");
            check(runtime.professionService.get(player.getUUID(),"craftsman").experience.subtract(oldXp).intValueExact()==60&&runtime.professionService.get(player.getUUID(),"craftsman").coins.subtract(oldCoins).intValueExact()==60,"craft_exact_xp_and_coins");
            long gearCount=count("katana");
            runtime.equipmentApplication.craft(player,request);
            check(count("katana")==gearCount&&regional("goblin_ingot")==40&&runtime.professionService.get(player.getUUID(),"craftsman").experience.subtract(oldXp).intValueExact()==60,"duplicate_craft_no_second_reward");
            mutate(()->player.getInventory().setItem(2,EquipmentItems.material("goblin_ingot",23)));
            String before=inventory();
            rejects(()->runtime.equipmentApplication.craft(player,craft(Slot.WEAPON,10)),"insufficient_material_rejected");
            check(before.equals(inventory()),"insufficient_material_full_rollback");
            clear();
            mutate(()->{
                for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.DIRT,64));player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(Items.STICK,64));player.getInventory().setItem(2,EquipmentItems.material("goblin_ingot",64));
            }
            );
            before=inventory();
            var xp=runtime.professionService.get(player.getUUID(),"craftsman").experience;
            rejects(()->runtime.equipmentApplication.craft(player,craft(Slot.WEAPON,10)),"full_inventory_craft_rejected");
            check(before.equals(inventory())&&runtime.professionService.get(player.getUUID(),"craftsman").experience.equals(xp),"full_inventory_no_consumption_or_xp");
            clear();
            mutate(()->{
                player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(Items.STICK,64));player.getInventory().setItem(2,EquipmentItems.material("spinel",64));
            }
            );
            rejects(()->runtime.equipmentApplication.craft(player,craft(Slot.WEAPON,20)),"regional_material_cannot_substitute_adjacent_band");
            mutate(()->player.getInventory().setItem(2,EquipmentItems.material("stigma_silk",64)));
            runtime.equipmentApplication.craft(player,craft(Slot.CHEST,24));
            check(regional("stigma_silk")==52&&count("iron_ingot")==40,"chest_fixed_12_and_level24_q3");
            player.closeContainer();
            clear();
            Gear epic=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.EPIC,78,new Random(19));
            epic.sockets=1;
            Gem embedded=new Gem();
            embedded.name="保留样本";
            epic.gems.add(embedded);
            UUID identity=epic.instance;
            String special=JSON.toJson(epic.affixes.stream().filter(a->!a.base).toList());
            mutate(()->{
                player.getInventory().setItem(0,EquipmentItems.gear(epic));player.inventoryMenu.setCarried(ItemRegistry.item("epic_upgrade_stone",2));
            }
            );
            JsonObject upgrade=currency(0,null);
            runtime.equipmentApplication.currencyRequest(player,upgrade);
            Gear upgraded=EquipmentItems.gear(player.getInventory().getItem(0));
            check(upgraded.level==80&&upgraded.instance.equals(identity)&&upgraded.gems.getFirst().name.equals("保留样本")&&special.equals(JSON.toJson(upgraded.affixes.stream().filter(a->!a.base).toList())),"upgrade_clamp_identity_specials_and_gems");
            runtime.equipmentApplication.currencyRequest(player,upgrade);
            check(player.inventoryMenu.getCarried().getCount()==1,"duplicate_upgrade_returns_original_result");
            rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,null)),"level80_upgrade_rejected");
            currencies();
            clear();
            mutate(()->{
                player.getInventory().setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,10,new Random(3))));player.inventoryMenu.setCarried(ItemRegistry.item("socket_stone",2));
            }
            );
            runtime.equipmentApplication.currencyRequest(player,currency(0,null));
            runtime.equipmentApplication.currencyRequest(player,currency(0,null));
            check(EquipmentItems.gear(player.getInventory().getItem(0)).sockets==2&&player.inventoryMenu.getCarried().isEmpty(),"two_socket_stones_open_exactly_two");
            mutate(()->player.inventoryMenu.setCarried(ItemRegistry.item("socket_stone",1)));
            rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,null)),"weapon_socket_limit");
            check(player.inventoryMenu.getCarried().getCount()==1,"failed_socket_keeps_currency");
            Gem gem=new Gem();
            gem.level=10;
            gem.group="WEAPON";
            gem.name="力量样本";
            Affix effect=new Affix("verify_strength",Stat.STRENGTH,5,false);
            effect.fixed=true;
            gem.affixes.add(effect);
            mutate(()->player.inventoryMenu.setCarried(EquipmentItems.payload("affix_gem",gem,gem.name)));
            runtime.equipmentApplication.currencyRequest(player,currency(0,0));
            mutate(()->player.inventoryMenu.setCarried(EquipmentItems.payload("affix_gem",gem,gem.name)));
            rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,1)),"duplicate_special_gem_rejected");
            Gem tooHigh=copy(gem,Gem.class);
            tooHigh.level=11;
            tooHigh.affixes.getFirst().id="verify_other";
            mutate(()->player.inventoryMenu.setCarried(EquipmentItems.payload("affix_gem",tooHigh,"高等级样本")));
            rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,1)),"gem_source_level_restriction");
            Gem replacement=copy(gem,Gem.class);
            replacement.name="覆盖样本";
            replacement.affixes.getFirst().id="verify_replacement";
            mutate(()->player.inventoryMenu.setCarried(EquipmentItems.payload("affix_gem",replacement,replacement.name)));
            runtime.equipmentApplication.currencyRequest(player,currency(0,0));
            check(EquipmentItems.gear(player.getInventory().getItem(0)).gems.size()==1&&EquipmentItems.gear(player.getInventory().getItem(0)).gems.getFirst().name.equals("覆盖样本")&&count("affix_gem")==0,"gem_overwrite_destroys_previous");
            clear();
            mutate(()->{
                player.getInventory().setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.EPIC,20,new Random(2))));player.inventoryMenu.setCarried(ItemRegistry.item("set_extraction_stone",1));
            }
            );
            runtime.equipmentApplication.currencyRequest(player,currency(0,null));
            check(count("chest_armor")==0&&count("set_stone")==1&&player.inventoryMenu.getCarried().isEmpty(),"set_extraction_consumes_entire_source_and_stone");
            ItemStack stone=java.util.stream.IntStream.range(0,36).mapToObj(player.getInventory()::getItem).filter(stack->ItemData.id(stack).equals("set_stone")).findFirst().orElseThrow().copy();
            mutate(()->{
                player.getInventory().setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.COMMON,20,new Random(2))));player.inventoryMenu.setCarried(stone.copy());
            }
            );
            rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,null)),"armor_set_rejects_jewelry");
            mutate(()->player.getInventory().setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.COMMON,19,new Random(2)))));
            rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,null)),"set_source_level_restriction");
            mutate(()->player.getInventory().setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.COMMON,20,new Random(2)))));
            runtime.equipmentApplication.currencyRequest(player,currency(0,null));
            check(EquipmentItems.gear(player.getInventory().getItem(0)).set.equals("first_sword")&&player.inventoryMenu.getCarried().isEmpty(),"set_transfer_allows_white_target");
            clear();
            mutate(()->player.getInventory().setItem(10,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.EPIC,20,new Random(2)))));
            var beforeDrops=new HashSet<>(runtime.journal.keys("loot/"));JsonObject dismantle=dismantleRequest(10);
            runtime.economyService.dismantle(player,dismantle);
            check(droppedSince(beforeDrops,"epic_upgrade_stone")==1&&count("katana")==0,"whole_epic_dismantle_drops_exactly_one_upgrade_stone");
            player.closeContainer();
            clear();
            mutate(()->player.getInventory().setItem(0,EquipmentItems.material("goblin_ingot",64)));
            MenuHooks.extra(player).bagPage=0;
            runtime.handle(player,"bag",new JsonObject());
            runtime.inventory.click(player,player.containerMenu,cn.yumis.fight.modules.inventory.api.DimensionalMenu.PLAYER_START+27,0,ContainerInput.QUICK_MOVE);
            check(MenuHooks.extra(player).bag.getItem(0).getCount()==64&&player.getInventory().getItem(0).isEmpty(),"dimensional_bag_shift_insert");
            runtime.inventory.click(player,player.containerMenu,0,0,ContainerInput.QUICK_MOVE);
            check(MenuHooks.extra(player).bag.getItem(0).isEmpty()&&regional("goblin_ingot")==64,"dimensional_bag_shift_withdraw");
            player.closeContainer();
            clear();
            String group=ItemData.NS+":bracelet";
            int oldBonus=runtime.equipmentLayout.bonus(player).getOrDefault(group,0);
            runtime.equipmentLayout.change(player,group,1);
            int slot=-1;
            for(int i=0;i<MenuHooks.extra(player).groups.size();i++)if(MenuHooks.extra(player).groups.get(i).equals(group))slot=i;
            final int removedSlot=slot;
            mutate(()->MenuHooks.extra(player).setItem(removedSlot,EquipmentItems.gear(EquipmentRules.create(Slot.BRACELET,Weapon.KATANA,Rarity.COMMON,10,new Random(7)))));
            runtime.equipmentLayout.change(player,group,-1);
            JsonElement waiting=runtime.journal.get("equipmentWaiting/"+player.getUUID());
            check(waiting.isJsonArray()&&!waiting.getAsJsonArray().isEmpty()&&runtime.equipmentLayout.bonus(player).getOrDefault(group,0)==oldBonus,"removed_extra_slot_moves_item_to_persistent_waiting");
            runtime.handle(player,"claim",new JsonObject());
            check(count("bracelet")==1,"waiting_equipment_claim_actual_inventory");
            String saved=inventory();
            player.getInventory().setItem(0,ItemStack.EMPTY);
            runtime.journal.put("inventoryApplied/"+player.getUUID(),"simulated-interruption");
            runtime.inventory.restore(player);
            check(saved.equals(inventory()),"pending_inventory_redo_from_wal_and_native_readback");
            equipmentExpansion(pos);
            containerChecks(pos);
            basicCycleChecks();
        }
        catch(Throwable e){
            failure=e;
        }
        finally{
            try{
                player.closeContainer();
                session.progress=originalProgress;
                session.resources=originalResources;
                runtime.journal.transaction(originals);
                mutate(()->runtime.inventory.verificationRestore(player,original));
                player.level().setBlock(pos,block,3);
                runtime.savePlayer(player);
                runtime.profile(player);
            }
            catch(Throwable restore){
                if(failure==null)failure=restore;
                else failure.addSuppressed(restore);
            }
            try{
                Path dir=runtime.server.getServerDirectory().resolve("verification");
                Files.createDirectories(dir);
                JsonObject report=new JsonObject();
                report.add("passed",JSON.toJsonTree(passed));
                report.addProperty("success",failure==null);
                if(failure!=null)report.addProperty("failure",failure.toString());
                Files.writeString(dir.resolve("server-checks-"+player.getScoreboardName()+".json"),JSON.toJson(report));
            }
            catch(Exception e){
                runtime.message(player,e.toString());
            }
        }
        if(failure!=null)throw new IllegalStateException("验证完成 "+passed.size()+" 项后失败："+failure,failure);
        runtime.message(player,"服务端实机验证通过 "+passed.size()+" 项");
        return passed.size();
    }
    private void basicCycleChecks(){
        player.closeContainer();clear();mutate(()->player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.LIGHTSABER,Rarity.COMMON,1,new Random(1)))));runtime.session(player).progress.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);
        BasicAttackService cycles=cn.yumis.fight.bootstrap.RuntimeAssembly.basicAttackService(runtime);JsonObject start=new JsonObject();start.addProperty("id",UUID.randomUUID().toString());cycles.start(player,start);
        rejects(()->cycles.start(player,start),"server_rejects_replayed_basic_start");
        mutate(()->player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.GREATSWORD,Rarity.COMMON,1,new Random(1)))));JsonObject switched=new JsonObject();switched.addProperty("id",UUID.randomUUID().toString());rejects(()->cycles.start(player,switched),"server_weapon_switch_does_not_reset_basic_cooldown");
        JsonObject hit=new JsonObject();hit.addProperty("basicCycle",start.get("id").getAsString());hit.addProperty("basicSlash",0);hit.addProperty("target",UUID.randomUUID().toString());cycles.validate(player,hit);cycles.committed(hit);rejects(()->cycles.validate(player,hit),"server_rejects_duplicate_basic_target_with_new_operation");
        var movement=player.getDeltaMovement();player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.knockback(.4,1,0,player.damageSources().generic(),0);check(Math.abs(Math.abs(player.getDeltaMovement().x)-.04)<.0001,"melee_knockback_retains_ten_percent_once");player.setDeltaMovement(movement);
    }
    private void openFabricator(net.minecraft.core.BlockPos pos){
        player.openMenu(new SimpleMenuProvider((id,inventory,owner)->new FabricatorMenu(id,inventory,pos),Component.literal("验证 · 装备制作器")));
    }
    private JsonObject menuCurrency(int index){
        var menu=player.containerMenu;var gear=EquipmentItems.gear(menu.getSlot(index).getItem());
        JsonObject j=new JsonObject();
        j.addProperty("rulesRevision",EquipmentDefinitions.current().revision);j.addProperty("operation",prefix+sequence++);j.addProperty("session",runtime.session(player).id.toString());
        j.addProperty("menu",menu.containerId);j.addProperty("slot",index);j.addProperty("equipment",gear.instance.toString());j.addProperty("revision",gear.revision);
        j.addProperty("count",menu.getCarried().getCount());j.add("cursor",runtime.inventory.encode(player,menu.getCarried()));return j;
    }
    private void equipmentExpansion(net.minecraft.core.BlockPos pos)throws Exception{
        player.closeContainer();clear();
        for(Gear template:EquipmentTemplates.creative()){
            Gear decoded=EquipmentItems.gear(EquipmentItems.gear(template));
            check(decoded!=null&&decoded.armorType==template.armorType&&decoded.jewelryType==template.jewelryType,"typed_item_roundtrip_"+template.slot+"_"+template.weapon+"_"+template.armorType+"_"+template.jewelryType);
        }
        check(CurrencyRules.find("upgrade_stone").naturalDrop()&&CurrencyRules.find("upgrade_stone").rarity()==Rarity.ARTIFACT,"upgrade_stone_natural_artifact_registration");
        for(Slot slot:List.of(Slot.CHEST,Slot.NECKLACE)){
            player.closeContainer();clear();
            var needs=MaterialCatalog.current().recipe(slot,50);
            mutate(()->{int index=1;for(var need:needs)player.getInventory().setItem(index++,need.regional()?EquipmentItems.material(need.id(),need.count()):new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(need.id())),need.count()));});
            openFabricator(pos);JsonObject request=craft(slot,50);runtime.equipmentApplication.craft(player,request);
            Gear result=java.util.stream.IntStream.range(0,36).mapToObj(i->EquipmentItems.gear(player.getInventory().getItem(i))).filter(Objects::nonNull).findFirst().orElseThrow();
            check(result.slot==slot&&result.level==50&&(slot.armor()?result.armorType==ArmorType.CLOTH:result.jewelryType==JewelryType.HEALTH),"typed_craft_matches_requested_"+slot);
            if(slot.jewelry())check(EquipmentRules.contribution(result).get(Stat.HEALTH)==810,"health_jewelry_craft_75_percent");
            String before=inventory();JsonObject invalid=craft(slot,50);invalid.addProperty(slot.armor()?"armorType":"jewelryType","LEGACY");
            rejects(()->runtime.equipmentApplication.craft(player,invalid),"unclassified_new_craft_rejected_"+slot);
            check(before.equals(inventory()),"invalid_type_craft_has_no_consumption_"+slot);
        }
        player.closeContainer();clear();
        Gear gear=EquipmentRules.create(Slot.HEAD,Weapon.KATANA,Rarity.COMMON,78,ArmorType.LIGHT,JewelryType.UNTYPED,new Random(0));gear.rarity=Rarity.LEGENDARY;
        Affix locked=new Affix("percent_evasion",Stat.EVASION,EquipmentAffixes.percentBaseline(78,Stat.EVASION)*1.1,false);locked.operation="PERCENT";locked.baseline=locked.value/1.1;locked.roll=1.1;locked.locked=true;gear.affixes.add(locked);
        gear.sockets=1;Gem gem=new Gem();gem.name="升级保留宝石";gear.gems.add(gem);
        mutate(()->{player.getInventory().setItem(0,EquipmentItems.gear(gear));player.inventoryMenu.setCarried(ItemRegistry.item("upgrade_stone",2));});
        JsonObject upgrade=currency(0,null);runtime.equipmentApplication.currencyRequest(player,upgrade);
        Gear raised=EquipmentItems.gear(player.getInventory().getItem(0));Affix grown=raised.affixes.stream().filter(a->a.id.equals("percent_evasion")).findFirst().orElseThrow();
        check(raised.level==80&&raised.instance.equals(gear.instance)&&raised.armorType==ArmorType.LIGHT&&grown.locked&&Math.abs(grown.value-1.375)<1e-9&&raised.gems.getFirst().name.equals(gem.name),"upgrade_stone_grows_locked_numbers_and_preserves_identity");
        check(player.inventoryMenu.getCarried().getCount()==1,"upgrade_stone_consumes_exactly_one");
        runtime.equipmentApplication.currencyRequest(player,upgrade);check(player.inventoryMenu.getCarried().getCount()==1&&EquipmentItems.gear(player.getInventory().getItem(0)).revision==raised.revision,"duplicate_new_upgrade_is_idempotent");
        rejects(()->runtime.equipmentApplication.currencyRequest(player,currency(0,null)),"new_upgrade_level80_rejected");check(player.inventoryMenu.getCarried().getCount()==1,"failed_new_upgrade_keeps_stone");
        mutate(()->{player.inventoryMenu.setCarried(ItemStack.EMPTY);player.getInventory().setItem(10,player.getInventory().getItem(0));player.getInventory().setItem(0,ItemStack.EMPTY);});
        var beforeDrops=new HashSet<>(runtime.journal.keys("loot/"));JsonObject dismantle=dismantleRequest(10);
        runtime.economyService.dismantle(player,dismantle);long produced=CurrencyRules.dismantlePool(Rarity.LEGENDARY).stream().mapToLong(id->droppedSince(beforeDrops,id)).sum();
        check(produced>=1&&produced<=3&&player.getInventory().getItem(10).isEmpty(),"legendary_dismantle_drops_one_to_three_lower_currencies");
        runtime.economyService.dismantle(player,dismantle);check(CurrencyRules.dismantlePool(Rarity.LEGENDARY).stream().mapToLong(id->droppedSince(beforeDrops,id)).sum()==produced,"duplicate_dismantle_is_idempotent");
        mutate(()->player.getInventory().setItem(10,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,50,new Random(0)))));
        JsonObject bad=dismantleRequest(10);mutate(()->player.getInventory().setItem(10,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,50,new Random(1)))));String before=inventory();
        rejects(()->runtime.economyService.dismantle(player,bad),"stale_dismantle_rejected");check(before.equals(inventory()),"failed_dismantle_keeps_equipment");
    }
    private void containerChecks(net.minecraft.core.BlockPos pos)throws Exception{
        player.closeContainer();clear();
        player.level().setBlock(pos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),3);
        var chest=(net.minecraft.world.Container)player.level().getBlockEntity(pos);
        player.openMenu(new SimpleMenuProvider((id,inv,owner)->net.minecraft.world.inventory.ChestMenu.threeRows(id,inv,chest),Component.literal("验证 · 箱子加工")));
        mutate(()->{chest.setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,10,new Random(90))));player.containerMenu.setCarried(ItemRegistry.item("transmutation_stone",3));});
        JsonObject request=menuCurrency(0);runtime.equipmentApplication.currencyRequest(player,request);
        check(EquipmentItems.gear(chest.getItem(0)).rarity==Rarity.RARE&&player.containerMenu.getCarried().getCount()==2,"chest_currency_uses_current_menu_and_consumes_once");
        runtime.equipmentApplication.currencyRequest(player,request);
        check(player.containerMenu.getCarried().getCount()==2,"chest_currency_duplicate_does_not_consume_twice");
        JsonElement before=runtime.inventory.encode(player,chest.getItem(0));
        rejects(()->runtime.equipmentApplication.currencyRequest(player,menuCurrency(0)),"chest_wrong_quality_rejected");
        check(before.equals(runtime.inventory.encode(player,chest.getItem(0)))&&player.containerMenu.getCarried().getCount()==2,"chest_failed_currency_keeps_target_and_cursor");
        rejects(()->mutate(()->{chest.setItem(0,ItemStack.EMPTY);throw new IllegalArgumentException("测试回滚");}),"external_container_transaction_failure");
        check(before.equals(runtime.inventory.encode(player,chest.getItem(0))),"external_container_failure_restores_real_slot");
        Gear recovered=EquipmentRules.process(EquipmentItems.gear(chest.getItem(0)),"augmentation_stone",new Random(91));
        JsonElement after=runtime.inventory.encode(player,EquipmentItems.gear(recovered));
        String pending="containerPending/"+player.getUUID()+"/"+prefix+"external-recovery";
        // Persist an independently constructed recovery record, without coupling checks to implementation internals.
        var block=(net.minecraft.world.level.block.entity.BlockEntity)chest;
        JsonObject address=new JsonObject();address.addProperty("kind","block");address.addProperty("dimension",player.level().dimension().identifier().toString());
        address.addProperty("owner",block.getPersistentData().getStringOr("ydnf_container_identity",""));address.addProperty("type",block.getClass().getName());
        address.addProperty("x",pos.getX());address.addProperty("y",pos.getY());address.addProperty("z",pos.getZ());address.addProperty("slot",0);
        JsonObject change=new JsonObject();change.add("address",address);change.add("before",before);change.add("after",after);
        JsonArray changes=new JsonArray();changes.add(change);JsonObject recovery=new JsonObject();recovery.addProperty("player",player.getStringUUID());
        recovery.addProperty("operation",prefix+"external-recovery");recovery.add("changes",changes);runtime.journal.put(pending,recovery);
        runtime.inventory.recoverContainers(player);
        check(EquipmentItems.gear(chest.getItem(0)).rarity==Rarity.ARTIFACT&&runtime.journal.get(pending).isJsonNull(),"external_container_wal_recovers_and_retires_once");
        player.closeContainer();clear();
        player.level().setBlock(pos,net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        player.openMenu(new SimpleMenuProvider((id,inv,owner)->new net.minecraft.world.inventory.CraftingMenu(id,inv,net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(),pos)),Component.literal("验证 · 临时输入加工")));
        var crafting=player.containerMenu;
        mutate(()->{crafting.getSlot(1).set(EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,10,new Random(92))));crafting.setCarried(ItemRegistry.item("transmutation_stone",1));});
        runtime.equipmentApplication.currencyRequest(player,menuCurrency(1));
        check(EquipmentItems.gear(crafting.getSlot(1).getItem()).rarity==Rarity.RARE,"crafting_input_currency_modifies_real_input");
        check(!runtime.journal.get("containerTransient/"+player.getUUID()).isJsonNull(),"temporary_input_after_state_is_escrowed");
        player.closeContainer();
        check(count("katana")==1&&runtime.journal.get("containerTransient/"+player.getUUID()).isJsonNull(),"normal_close_returns_inputs_and_clears_escrow");
        runtime.inventory.restore(player);
        check(count("katana")==1,"closed_temporary_menu_restore_does_not_duplicate");
        clear();
        for(boolean fillBag:new boolean[]{false,true}){
            player.openMenu(new SimpleMenuProvider((id,inv,owner)->new net.minecraft.world.inventory.CraftingMenu(id,inv,net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(),pos)),Component.literal("验证 · 满包关闭输入")));
            var fullMenu=player.containerMenu;
            mutate(()->{
                for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.DIRT,64));
                if(fillBag)for(int i=0;i<MenuHooks.extra(player).bagLimit();i++)MenuHooks.extra(player).bag.setItem(i,new ItemStack(Items.DIRT,64));
                fullMenu.getSlot(1).set(EquipmentItems.gear(EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.COMMON,10,new Random(94))));
                if(fillBag)fullMenu.setCarried(ItemRegistry.item("divine_stone",1));
            });
            JsonElement waiting=runtime.journal.get("equipmentWaiting/"+player.getUUID());int beforeWaiting=waiting.isJsonArray()?waiting.getAsJsonArray().size():0;
            player.closeContainer();
            if(fillBag)check(runtime.journal.get("equipmentWaiting/"+player.getUUID()).getAsJsonArray().size()==beforeWaiting+2,"temporary_close_full_inventory_and_bag_preserves_both_input_and_cursor_in_waiting");
            else check(EquipmentItems.gear(MenuHooks.extra(player).bag.getItem(0))!=null,"temporary_close_full_inventory_stashes_in_dimensional_bag");
            check(runtime.journal.get("containerTransient/"+player.getUUID()).isJsonNull(),"temporary_close_full_storage_retires_escrow_"+fillBag);
            clear();
        }
        ItemStack lost=EquipmentItems.gear(EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.COMMON,10,new Random(93)));
        String escrow="containerTransient/"+player.getUUID();
        JsonObject escrowState=new JsonObject();escrowState.addProperty("menu",999);JsonArray escrowItems=new JsonArray();escrowItems.add(runtime.inventory.encode(player,lost));escrowState.add("items",escrowItems);runtime.journal.put(escrow,escrowState);
        JsonElement waitingBefore=runtime.journal.get("equipmentWaiting/"+player.getUUID());int oldCount=waitingBefore.isJsonArray()?waitingBefore.getAsJsonArray().size():0;
        runtime.inventory.restore(player);runtime.inventory.restore(player);
        check(runtime.journal.get(escrow).isJsonNull()&&runtime.journal.get("equipmentWaiting/"+player.getUUID()).getAsJsonArray().size()==oldCount+1,"crashed_temporary_menu_escrow_recovers_exactly_once");
    }
}
