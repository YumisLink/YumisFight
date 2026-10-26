package cn.yumis.fight.server;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Rules.Rarity;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
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
    private final ServerRuntime runtime;
    private final ServerPlayer player;
    private final List<String> passed=new ArrayList<>();
    private final String prefix="verify/"+UUID.randomUUID()+"/";
    private int sequence;
    private VerificationChecks(ServerRuntime runtime,ServerPlayer player){
        this.runtime=runtime;
        this.player=player;
    }
    public static int run(ServerRuntime runtime,ServerPlayer player){
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
            if(id.equals(GameItems.material(s)))count+=s.getCount();
        }
        return count;
    }
    private long count(String id){
        long count=0;
        for(int i=0;i<36;i++){
            var s=player.getInventory().getItem(i);
            if(id.equals(GameItems.id(s)))count+=s.getCount();
        }
        return count;
    }
    private JsonObject craft(Slot slot,int level){
        JsonObject j=new JsonObject();
        j.addProperty("operation",prefix+sequence++);
        j.addProperty("slot",slot.name());
        j.addProperty("weapon",Weapon.KATANA.name());
        j.addProperty("level",level);
        return j;
    }
    private JsonObject currency(int inventoryIndex,Integer socket){
        var g=GameItems.gear(player.getInventory().getItem(inventoryIndex));
        JsonObject j=new JsonObject();
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
    private int run(){
        player.closeContainer();
        var original=runtime.inventory.capture(player,"restore");
        var session=runtime.session(player);
        Progress originalProgress=copy(session.progress,Progress.class);
        Models.Runtime originalResources=copy(session.resources,Models.Runtime.class);
        Map<String,JsonElement> originals=new HashMap<>();
        for(String key:List.of("player/"+player.getUUID(),"resources/"+player.getUUID(),"slotBonus/"+player.getUUID(),"equipmentWaiting/"+player.getUUID(),"profession/"+player.getUUID()+"/craftsman"))originals.put(key,runtime.journal.get(key));
        var pos=player.blockPosition().east();
        var block=player.level().getBlockState(pos);
        Throwable failure=null;
        try{
            Models.Runtime forged=copy(session.resources,Models.Runtime.class);
            forged.training=!session.resources.training;
            forged.trainingBoundary=999999;
            JsonObject forgedUpload=new JsonObject();
            forgedUpload.addProperty("session",session.id.toString());forgedUpload.addProperty("generation",session.life.generation);
            forgedUpload.addProperty("serverIssuedAt",Math.max(System.currentTimeMillis(),session.resources.recordedAt));
            forgedUpload.add("state",JSON.toJsonTree(forged));runtime.runtime(player,forgedUpload);
            check(session.resources.training==originalResources.training,"client_runtime_cannot_enable_training_or_bypass_stamina_cost");
            check(session.resources.trainingBoundary==originalResources.trainingBoundary,"client_runtime_cannot_replace_administrator_training_boundary");
            var adminBefore=runtime.profession(player,"craftsman");
            String adminOperation=prefix+"administrator";
            runtime.awardProfession(player,"craftsman",35,adminOperation,true);
            check(runtime.profession(player,"craftsman").experience.equals(adminBefore.experience.add(java.math.BigInteger.valueOf(35)))&&runtime.profession(player,"craftsman").coins.equals(adminBefore.coins),"administrator_profession_xp_without_coins");
            runtime.awardProfession(player,"craftsman",35,adminOperation,true);
            check(runtime.profession(player,"craftsman").experience.equals(adminBefore.experience.add(java.math.BigInteger.valueOf(35))),"duplicate_profession_award_is_idempotent");
            rejects(()->runtime.awardProfession(player,"craftsman",36,adminOperation,true),"conflicting_profession_award_rejected");
            runtime.journal.put("profession/"+player.getUUID()+"/craftsman",adminBefore);
            var boundInventory=MenuHooks.extra(player);
            var curios=top.theillusivec4.curios.api.CuriosApi.getCuriosInventoryOrNull(player);
            check(curios!=null&&java.util.Arrays.stream(ExtraInventory.SLOTS).allMatch(slot->curios.getStacksHandler("ydnf_"+slot.name().toLowerCase(java.util.Locale.ROOT)).map(handler->handler.getStacks().getSlots()>=1).orElse(false)),"curios_registers_six_real_accessory_slots");
            check(player.inventoryMenu.slots.stream().noneMatch(slot->slot.container instanceof ExtraInventory),"legacy_inventory_slots_removed");
            session.progress=new Progress();
            session.progress.level=80;
            session.progress.sp=spAt(80);
            runtime.savePlayer(player);
            clear();
            Gear shiftBracelet=EquipmentRules.create(Slot.BRACELET,Weapon.KATANA,Rarity.COMMON,10,new Random(11));
            ItemStack bracelet=GameItems.gear(shiftBracelet);
            check(boundInventory.canPlaceItem(0,bracelet),"curios_accepts_level_valid_bracelet");
            mutate(()->boundInventory.setItem(0,bracelet));
            check(GameItems.gear(curios.getStacksHandler("ydnf_bracelet").orElseThrow().getStacks().getStackInSlot(0)).instance.equals(shiftBracelet.instance),"curios_is_accessory_source_of_truth");
            player.level().setBlock(pos,FightMod.FABRICATOR.get().defaultBlockState(),3);
            openFabricator(pos);
            check(player.containerMenu.stillValid(player),"fabricator_real_block_and_menu");
            clear();
            mutate(()->{
                player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(Items.STICK,64));player.getInventory().setItem(2,GameItems.material("goblin_ingot",64));
            }
            );
            var profession=runtime.profession(player,"craftsman");
            var oldXp=profession.experience;
            var oldCoins=profession.coins;
            JsonObject request=craft(Slot.WEAPON,10);
            runtime.craft(player,request);
            check(regional("goblin_ingot")==40,"craft_weapon_regional_fixed_24");
            check(count("iron_ingot")==62&&count("stick")==63,"craft_vanilla_q_formula");
            check(runtime.profession(player,"craftsman").experience.subtract(oldXp).intValueExact()==60&&runtime.profession(player,"craftsman").coins.subtract(oldCoins).intValueExact()==60,"craft_exact_xp_and_coins");
            long gearCount=count("katana");
            runtime.craft(player,request);
            check(count("katana")==gearCount&&regional("goblin_ingot")==40&&runtime.profession(player,"craftsman").experience.subtract(oldXp).intValueExact()==60,"duplicate_craft_no_second_reward");
            mutate(()->player.getInventory().setItem(2,GameItems.material("goblin_ingot",23)));
            String before=inventory();
            rejects(()->runtime.craft(player,craft(Slot.WEAPON,10)),"insufficient_material_rejected");
            check(before.equals(inventory()),"insufficient_material_full_rollback");
            clear();
            mutate(()->{
                for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.DIRT,64));player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(Items.STICK,64));player.getInventory().setItem(2,GameItems.material("goblin_ingot",64));
            }
            );
            before=inventory();
            var xp=runtime.profession(player,"craftsman").experience;
            rejects(()->runtime.craft(player,craft(Slot.WEAPON,10)),"full_inventory_craft_rejected");
            check(before.equals(inventory())&&runtime.profession(player,"craftsman").experience.equals(xp),"full_inventory_no_consumption_or_xp");
            clear();
            mutate(()->{
                player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(Items.STICK,64));player.getInventory().setItem(2,GameItems.material("spinel",64));
            }
            );
            rejects(()->runtime.craft(player,craft(Slot.WEAPON,20)),"regional_material_cannot_substitute_adjacent_band");
            mutate(()->player.getInventory().setItem(2,GameItems.material("stigma_silk",64)));
            runtime.craft(player,craft(Slot.CHEST,24));
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
                player.getInventory().setItem(0,GameItems.gear(epic));player.inventoryMenu.setCarried(GameItems.item("epic_upgrade_stone",2));
            }
            );
            JsonObject upgrade=currency(0,null);
            runtime.currencyRequest(player,upgrade);
            Gear upgraded=GameItems.gear(player.getInventory().getItem(0));
            check(upgraded.level==80&&upgraded.instance.equals(identity)&&upgraded.gems.getFirst().name.equals("保留样本")&&special.equals(JSON.toJson(upgraded.affixes.stream().filter(a->!a.base).toList())),"upgrade_clamp_identity_specials_and_gems");
            runtime.currencyRequest(player,upgrade);
            check(player.inventoryMenu.getCarried().getCount()==1,"duplicate_upgrade_returns_original_result");
            rejects(()->runtime.currencyRequest(player,currency(0,null)),"level80_upgrade_rejected");
            clear();
            mutate(()->{
                player.getInventory().setItem(0,GameItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,10,new Random(3))));player.inventoryMenu.setCarried(GameItems.item("socket_stone",2));
            }
            );
            runtime.currencyRequest(player,currency(0,null));
            runtime.currencyRequest(player,currency(0,null));
            check(GameItems.gear(player.getInventory().getItem(0)).sockets==2&&player.inventoryMenu.getCarried().isEmpty(),"two_socket_stones_open_exactly_two");
            mutate(()->player.inventoryMenu.setCarried(GameItems.item("socket_stone",1)));
            rejects(()->runtime.currencyRequest(player,currency(0,null)),"weapon_socket_limit");
            check(player.inventoryMenu.getCarried().getCount()==1,"failed_socket_keeps_currency");
            Gem gem=new Gem();
            gem.level=10;
            gem.group="WEAPON";
            gem.name="力量样本";
            Affix effect=new Affix("verify_strength",Stat.STRENGTH,5,false);
            effect.fixed=true;
            gem.affixes.add(effect);
            mutate(()->player.inventoryMenu.setCarried(GameItems.payload("affix_gem",gem,gem.name)));
            runtime.currencyRequest(player,currency(0,0));
            mutate(()->player.inventoryMenu.setCarried(GameItems.payload("affix_gem",gem,gem.name)));
            rejects(()->runtime.currencyRequest(player,currency(0,1)),"duplicate_special_gem_rejected");
            Gem tooHigh=copy(gem,Gem.class);
            tooHigh.level=11;
            tooHigh.affixes.getFirst().id="verify_other";
            mutate(()->player.inventoryMenu.setCarried(GameItems.payload("affix_gem",tooHigh,"高等级样本")));
            rejects(()->runtime.currencyRequest(player,currency(0,1)),"gem_source_level_restriction");
            Gem replacement=copy(gem,Gem.class);
            replacement.name="覆盖样本";
            replacement.affixes.getFirst().id="verify_replacement";
            mutate(()->player.inventoryMenu.setCarried(GameItems.payload("affix_gem",replacement,replacement.name)));
            runtime.currencyRequest(player,currency(0,0));
            check(GameItems.gear(player.getInventory().getItem(0)).gems.size()==1&&GameItems.gear(player.getInventory().getItem(0)).gems.getFirst().name.equals("覆盖样本")&&count("affix_gem")==0,"gem_overwrite_destroys_previous");
            clear();
            mutate(()->{
                player.getInventory().setItem(0,GameItems.gear(EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.EPIC,20,new Random(2))));player.inventoryMenu.setCarried(GameItems.item("set_extraction_stone",1));
            }
            );
            runtime.currencyRequest(player,currency(0,null));
            check(count("chest_armor")==0&&count("set_stone")==1&&player.inventoryMenu.getCarried().isEmpty(),"set_extraction_consumes_entire_source_and_stone");
            ItemStack stone=java.util.stream.IntStream.range(0,36).mapToObj(player.getInventory()::getItem).filter(stack->GameItems.id(stack).equals("set_stone")).findFirst().orElseThrow().copy();
            mutate(()->{
                player.getInventory().setItem(0,GameItems.gear(EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.COMMON,20,new Random(2))));player.inventoryMenu.setCarried(stone.copy());
            }
            );
            rejects(()->runtime.currencyRequest(player,currency(0,null)),"armor_set_rejects_jewelry");
            mutate(()->player.getInventory().setItem(0,GameItems.gear(EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.COMMON,19,new Random(2)))));
            rejects(()->runtime.currencyRequest(player,currency(0,null)),"set_source_level_restriction");
            mutate(()->player.getInventory().setItem(0,GameItems.gear(EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.COMMON,20,new Random(2)))));
            runtime.currencyRequest(player,currency(0,null));
            check(GameItems.gear(player.getInventory().getItem(0)).set.equals("first_sword")&&player.inventoryMenu.getCarried().isEmpty(),"set_transfer_allows_white_target");
            clear();
            mutate(()->player.getInventory().setItem(0,GameItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.EPIC,20,new Random(2)))));
            openFabricator(pos);
            JsonObject salvage=new JsonObject();
            salvage.addProperty("operation",prefix+sequence++);
            salvage.addProperty("inventorySlot",0);
            runtime.salvage(player,salvage);
            check(count("epic_upgrade_stone")==1&&count("katana")==0,"whole_epic_salvage_exactly_one_upgrade_stone");
            player.closeContainer();
            clear();
            mutate(()->player.getInventory().setItem(0,GameItems.material("goblin_ingot",64)));
            runtime.handle(player,"bag",new JsonObject());
            runtime.inventory.click(player,player.containerMenu,81,0,ContainerInput.QUICK_MOVE);
            check(MenuHooks.extra(player).bag.getItem(0).getCount()==64&&player.getInventory().getItem(0).isEmpty(),"dimensional_bag_shift_insert");
            runtime.inventory.click(player,player.containerMenu,0,0,ContainerInput.QUICK_MOVE);
            check(MenuHooks.extra(player).bag.getItem(0).isEmpty()&&regional("goblin_ingot")==64,"dimensional_bag_shift_withdraw");
            player.closeContainer();
            clear();
            String group=GameItems.NS+":bracelet";
            int oldBonus=EquipmentService.bonus(runtime,player).getOrDefault(group,0);
            EquipmentService.change(runtime,player,group,1);
            int slot=-1;
            for(int i=0;i<MenuHooks.extra(player).groups.size();i++)if(MenuHooks.extra(player).groups.get(i).equals(group))slot=i;
            final int removedSlot=slot;
            mutate(()->MenuHooks.extra(player).setItem(removedSlot,GameItems.gear(EquipmentRules.create(Slot.BRACELET,Weapon.KATANA,Rarity.COMMON,10,new Random(7)))));
            EquipmentService.change(runtime,player,group,-1);
            JsonElement waiting=runtime.journal.get("equipmentWaiting/"+player.getUUID());
            check(waiting.isJsonArray()&&!waiting.getAsJsonArray().isEmpty()&&EquipmentService.bonus(runtime,player).getOrDefault(group,0)==oldBonus,"removed_extra_slot_moves_item_to_persistent_waiting");
            runtime.handle(player,"claim",new JsonObject());
            check(count("bracelet")==1,"waiting_equipment_claim_actual_inventory");
            String saved=inventory();
            player.getInventory().setItem(0,ItemStack.EMPTY);
            runtime.journal.put("inventoryApplied/"+player.getUUID(),"simulated-interruption");
            runtime.inventory.restore(player);
            check(saved.equals(inventory()),"pending_inventory_redo_from_wal_and_native_readback");
            containerChecks(pos);
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
    private void openFabricator(net.minecraft.core.BlockPos pos){
        player.openMenu(new SimpleMenuProvider((id,inventory,owner)->new FabricatorMenu(id,inventory,pos),Component.literal("验证 · 装备制作器")));
    }
    private JsonObject menuCurrency(int index){
        var menu=player.containerMenu;var gear=GameItems.gear(menu.getSlot(index).getItem());
        JsonObject j=new JsonObject();j.addProperty("operation",prefix+sequence++);j.addProperty("session",runtime.session(player).id.toString());
        j.addProperty("menu",menu.containerId);j.addProperty("slot",index);j.addProperty("equipment",gear.instance.toString());j.addProperty("revision",gear.revision);
        j.addProperty("count",menu.getCarried().getCount());j.add("cursor",runtime.inventory.encode(player,menu.getCarried()));return j;
    }
    private void containerChecks(net.minecraft.core.BlockPos pos)throws Exception{
        player.closeContainer();clear();
        player.level().setBlock(pos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),3);
        var chest=(net.minecraft.world.Container)player.level().getBlockEntity(pos);
        player.openMenu(new SimpleMenuProvider((id,inv,owner)->net.minecraft.world.inventory.ChestMenu.threeRows(id,inv,chest),Component.literal("验证 · 箱子加工")));
        mutate(()->{chest.setItem(0,GameItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,10,new Random(90))));player.containerMenu.setCarried(GameItems.item("transmutation_stone",3));});
        JsonObject request=menuCurrency(0);runtime.currencyRequest(player,request);
        check(GameItems.gear(chest.getItem(0)).rarity==Rarity.RARE&&player.containerMenu.getCarried().getCount()==2,"chest_currency_uses_current_menu_and_consumes_once");
        runtime.currencyRequest(player,request);
        check(player.containerMenu.getCarried().getCount()==2,"chest_currency_duplicate_does_not_consume_twice");
        JsonElement before=runtime.inventory.encode(player,chest.getItem(0));
        rejects(()->runtime.currencyRequest(player,menuCurrency(0)),"chest_wrong_quality_rejected");
        check(before.equals(runtime.inventory.encode(player,chest.getItem(0)))&&player.containerMenu.getCarried().getCount()==2,"chest_failed_currency_keeps_target_and_cursor");
        rejects(()->mutate(()->{chest.setItem(0,ItemStack.EMPTY);throw new IllegalArgumentException("测试回滚");}),"external_container_transaction_failure");
        check(before.equals(runtime.inventory.encode(player,chest.getItem(0))),"external_container_failure_restores_real_slot");
        var participants=new ContainerParticipants(runtime,runtime.inventory);
        var capture=participants.capture(player);
        var ref=capture.refs().stream().filter(r->r.container()==chest&&r.slot()==0).findFirst().orElseThrow();
        Gear recovered=EquipmentRules.process(GameItems.gear(chest.getItem(0)),"augmentation_stone",new Random(91));
        JsonElement after=runtime.inventory.encode(player,GameItems.gear(recovered));
        String pending="containerPending/"+player.getUUID()+"/"+prefix+"external-recovery";
        runtime.journal.put(pending,new ContainerParticipants.Pending(player.getStringUUID(),prefix+"external-recovery",List.of(new ContainerParticipants.Change(ref.address(),before,after))));
        runtime.inventory.recoverContainers(player);
        check(GameItems.gear(chest.getItem(0)).rarity==Rarity.ARTIFACT&&runtime.journal.get(pending).isJsonNull(),"external_container_wal_recovers_and_retires_once");
        player.closeContainer();clear();
        player.level().setBlock(pos,net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        player.openMenu(new SimpleMenuProvider((id,inv,owner)->new net.minecraft.world.inventory.CraftingMenu(id,inv,net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(),pos)),Component.literal("验证 · 临时输入加工")));
        var crafting=player.containerMenu;
        mutate(()->{crafting.getSlot(1).set(GameItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,10,new Random(92))));crafting.setCarried(GameItems.item("transmutation_stone",1));});
        runtime.currencyRequest(player,menuCurrency(1));
        check(GameItems.gear(crafting.getSlot(1).getItem()).rarity==Rarity.RARE,"crafting_input_currency_modifies_real_input");
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
                if(fillBag)for(int i=0;i<54;i++)MenuHooks.extra(player).bag.setItem(i,new ItemStack(Items.DIRT,64));
                fullMenu.getSlot(1).set(GameItems.gear(EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.COMMON,10,new Random(94))));
                if(fillBag)fullMenu.setCarried(GameItems.item("divine_stone",1));
            });
            JsonElement waiting=runtime.journal.get("equipmentWaiting/"+player.getUUID());int beforeWaiting=waiting.isJsonArray()?waiting.getAsJsonArray().size():0;
            player.closeContainer();
            if(fillBag)check(runtime.journal.get("equipmentWaiting/"+player.getUUID()).getAsJsonArray().size()==beforeWaiting+2,"temporary_close_full_inventory_and_bag_preserves_both_input_and_cursor_in_waiting");
            else check(GameItems.gear(MenuHooks.extra(player).bag.getItem(0))!=null,"temporary_close_full_inventory_stashes_in_dimensional_bag");
            check(runtime.journal.get("containerTransient/"+player.getUUID()).isJsonNull(),"temporary_close_full_storage_retires_escrow_"+fillBag);
            clear();
        }
        ItemStack lost=GameItems.gear(EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.COMMON,10,new Random(93)));
        String escrow="containerTransient/"+player.getUUID();
        runtime.journal.put(escrow,new ContainerParticipants.Escrow(999,List.of(runtime.inventory.encode(player,lost))));
        JsonElement waitingBefore=runtime.journal.get("equipmentWaiting/"+player.getUUID());int oldCount=waitingBefore.isJsonArray()?waitingBefore.getAsJsonArray().size():0;
        runtime.inventory.restore(player);runtime.inventory.restore(player);
        check(runtime.journal.get(escrow).isJsonNull()&&runtime.journal.get("equipmentWaiting/"+player.getUUID()).getAsJsonArray().size()==oldCount+1,"crashed_temporary_menu_escrow_recovers_exactly_once");
    }
}
