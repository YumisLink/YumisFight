package cn.yumis.fight.verification;
import cn.yumis.fight.bootstrap.ServerApplication;

import cn.yumis.fight.modules.inventory.api.InventoryPacking;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.world.inventory.Slot;
import cn.yumis.fight.modules.industry.platform.MachineBlockEntity;
import cn.yumis.fight.modules.industry.platform.MachineBlock;
import cn.yumis.fight.modules.industry.platform.IndustrialWorld;
import cn.yumis.fight.modules.industry.platform.IndustrialUpgrades;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.platform.IndustrialMenu;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import cn.yumis.fight.modules.industry.api.Fraction;
import cn.yumis.fight.modules.inventory.application.InventoryTransactions;

import cn.yumis.fight.modules.industry.api.DeviceType;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;


import com.google.gson.*;

import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;

import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.*;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import java.nio.file.*;
import java.util.*;
import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;

/** Real-server integration checks. Only available in the isolated verification runtime. */
public final class IndustrialVerification {
    private final ServerPlayer player;
    private final ServerLevel level;
    private final IndustrialWorld world;
    private final ServerApplication runtime;
    private final JsonArray checks=new JsonArray();
    private int area;
    private IndustrialVerification(ServerPlayer p){player=p;level=p.level();world=IndustrialWorld.get(level);runtime=ServerApplication.get();}
    public static void register(RegisterCommandsEvent e){
        var command=Commands.literal("ydnf").then(Commands.literal("industry").requires(s->Boolean.getBoolean("ydnf.verify")&&s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("verify").executes(c->{new IndustrialVerification(c.getSource().getPlayerOrException()).run();return 1;}))
            .then(Commands.literal("setup").executes(c->{new IndustrialVerification(c.getSource().getPlayerOrException()).setup();return 1;}))
            .then(Commands.literal("snapshot").executes(c->{var p=c.getSource().getPlayerOrException();JsonArray machines=new JsonArray();for(var be:IndustrialWorld.get(p.level()).loaded.values())machines.add(IndustrialWorld.get(p.level()).info(be));write(p,"industrial-live",machines);return 1;})));
        e.getDispatcher().register(command);
    }
    private static void write(ServerPlayer p,String name,JsonElement value){try{Path out=p.level().getServer().getServerDirectory().resolve("verification");Files.createDirectories(out);Files.writeString(out.resolve(name+".json"),JSON.toJson(value));}catch(Exception e){throw new IllegalStateException(e);}}
    private interface Check {void run()throws Exception;}
    private void check(String name,Check check){JsonObject item=new JsonObject();item.addProperty("name",name);try{check.run();item.addProperty("passed",true);}catch(Throwable failure){item.addProperty("passed",false);item.addProperty("error",failure.toString());failure.printStackTrace();}checks.add(item);}
    private void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private void equal(long want,long got,String message){require(want==got,message+": expected="+want+", actual="+got);}
    private BlockPos area(){int x=1024+(area++%8)*80,z=1024+(area/8)*80;level.getChunk(x>>4,z>>4);return new BlockPos(x,80,z);}
    private MachineBlockEntity machine(String id,BlockPos p){level.getChunk(p.getX()>>4,p.getZ()>>4);level.setBlock(p,Blocks.AIR.defaultBlockState(),3);level.setBlock(p,IndustrialContent.ALL_BLOCKS.get(id).get().defaultBlockState(),3);var be=(MachineBlockEntity)level.getBlockEntity(p);world.placed(be);return be;}
    private MachineBlockEntity beacon(BlockPos p,int tier){var be=machine("mana_beacon_t"+tier,p);world.refreshCoverage(be);ticks(1);require(world.info(be).get("network_capacity").getAsLong()>0,"裸放信标应有效");return be;}
    private ChestBlockEntity chest(BlockPos p){level.getChunk(p.getX()>>4,p.getZ()>>4);level.setBlock(p,Blocks.AIR.defaultBlockState(),3);level.setBlock(p,Blocks.CHEST.defaultBlockState(),3);return (ChestBlockEntity)level.getBlockEntity(p);}
    private void ticks(int count){for(int i=0;i<count;i++)IndustrialWorld.tick(runtime.server);}
    private long outputCount(MachineBlockEntity be,Item item){long n=0;for(int i=4;i<13;i++)if(be.getItem(i).is(item))n+=be.getItem(i).getCount();return n;}
    private long chestCount(ChestBlockEntity chest,Item item){long n=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(item))n+=chest.getItem(i).getCount();return n;}
    private void fillFluid(MachineBlockEntity be,FluidResource resource,int amount){try(var tx=Transaction.openRoot()){equal(amount,be.fluidsFor(Direction.UP).insert(resource,amount,tx),"流体插入");tx.commit();}}
    private void funded(MachineBlockEntity be,long energy,int ticks,ItemStack result){be.batchRecipe="verification:batch";be.batchEnergy=energy;be.batchTicks=ticks;be.batchSpeed=1;be.batchResult=result;be.paid=0;be.work=Fraction.ZERO;}
    private void run(){
        var before=runtime.inventory.capture(player,"industrial-verification-before");var position=player.position();float yaw=player.getYRot(),pitch=player.getXRot();player.closeContainer();
        try{
            check("工业配方、通用材料和限定投放",()->{
                var recipes=level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get());require(recipes.size()>=70,"工业配方未完整加载："+recipes.size());require(IndustrialContent.stack("lead_ingot",1).is(TagKey.create(Registries.ITEM,Identifier.parse("c:ingots/lead"))),"铅未进入通用标签");require(IndustrialContent.stack("industrial_magic_stone",1).getItem()!=ItemRegistry.item("magic_stone",1).getItem(),"工业材料覆盖了旧魔法石装备");for(var h:recipes)require(!h.id().identifier().getPath().contains("generate_uranium")&&!h.id().identifier().getPath().contains("generate_magic"),"未批准资源进入生成器");
            });
            check("生成器未选配方不自动消耗能源",()->{var be=machine("mineral_generator_t1",area());be.crystalRemaining=800;ticks(1);equal(800,be.crystalRemaining,"未选择就扣能");require(be.batchRecipe.isEmpty(),"自动锁入煤炭配方");be.running=false;});
            check("五级插槽和自动化访问面",()->{for(int tier=1;tier<=5;tier++){var be=machine("mana_crusher_t"+tier,area());for(int crystal=1;crystal<=10;crystal++){var stack=IndustrialContent.stack("energy_crystal_"+crystal,1);boolean yes=crystal==tier*2-1||crystal==tier*2;require(be.canPlaceItem(13,stack)==yes,"插槽阶位错误");try(var tx=Transaction.openRoot()){int n=be.itemsFor(Direction.SOUTH).insert(ItemResource.of(stack),1,tx);equal(yes?1:0,n,"自动插槽校验");}require(be.getItem(13).isEmpty(),"模拟插入未回滚");}try(var tx=Transaction.openRoot()){equal(0,be.itemsFor(Direction.UP).insert(ItemResource.of(IndustrialContent.stack("energy_crystal_"+(tier*2-1),1)),1,tx),"顶部误收水晶");}}});
            check("信标合网、卸载中继、恢复、拆分守恒",()->{BlockPos p=area();var a=beacon(p,1);var b=beacon(p.east(3),1);var c=beacon(p.east(6),1);world.link(a,b);world.link(b,c);a.energy=700;b.energy=400;c.energy=300;ticks(1);equal(1400,world.info(a).get("network_energy").getAsLong(),"合网余额");world.unload(b);ticks(1);equal(1000,world.info(a).get("network_energy").getAsLong(),"卸载后共享余额");equal(400,b.energy,"卸载核心余额");world.register(b);ticks(1);equal(1400,world.info(c).get("network_energy").getAsLong(),"重载余额");level.setBlock(b.getBlockPos(),Blocks.AIR.defaultBlockState(),3);ticks(1);equal(700,world.info(a).get("network_energy").getAsLong(),"分网A余额");equal(300,world.info(c).get("network_energy").getAsLong(),"分网C余额");});
            check("信标闭环不重复、下方方块不影响及拆除守恒",()->{BlockPos p=area();var a=beacon(p,2);var b=beacon(p.east(3),2);var c=beacon(p.south(3),2);world.link(a,b);world.link(b,c);world.link(c,a);a.energy=100;b.energy=200;c.energy=300;ticks(1);equal(600,world.info(a).get("network_energy").getAsLong(),"闭环重复能量");equal(3000,world.info(a).get("network_capacity").getAsLong(),"闭环重复容量");BlockPos base=b.getBlockPos().below();level.setBlock(base,Blocks.AIR.defaultBlockState(),3);world.changed(level,base);ticks(1);equal(600,world.info(a).get("network_energy").getAsLong(),"下方方块改变不应停用核心");equal(200,b.energy,"下方方块改变丢余能");level.setBlock(base,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);world.changed(level,base);ticks(1);equal(600,world.info(a).get("network_energy").getAsLong(),"下方放置方块改变余额");level.setBlock(a.getBlockPos(),Blocks.AIR.defaultBlockState(),3);ticks(1);equal(500,world.info(b).get("network_energy").getAsLong(),"拆除错误清空其他核心");});
            check("当期发能越过1000缓存直接供给",()->{BlockPos p=area();var core=beacon(p,1);var generator=machine("nuclear_reactor",p.east());world.bind(core,generator);generator.fuelTotal=generator.fuelRemaining=20;generator.fuelEnergy=100000;generator.fuelFluidRate=0;var consumer=machine("mana_crusher_t1",p.west());world.bind(core,consumer);funded(consumer,90000,20,new ItemStack(Items.COBBLESTONE));ticks(1);equal(4500,consumer.paid,"当期直接供能");equal(500,core.energy,"当期剩余入缓存");consumer.running=false;generator.running=false;});
            check("燃煤满网仍燃烧且产能精确",()->{BlockPos p=area();var core=beacon(p,1);core.energy=1000;var gen=machine("coal_generator",p.east());world.bind(core,gen);gen.setItem(14,new ItemStack(Items.COAL));long generated=0;for(int i=0;i<640;i++){ticks(1);generated+=gen.generated;}equal(1920,generated,"每煤产能");equal(0,gen.fuelRemaining,"燃料余时");equal(1000,core.energy,"满网容量");require(gen.getItem(14).isEmpty(),"煤炭未消耗");});
            check("核电缺冷却暂停燃料并按整数mB消费",()->{BlockPos p=area();var core=beacon(p,1);var gen=machine("nuclear_reactor",p.east());world.bind(core,gen);gen.setItem(14,IndustrialContent.stack("uranium_ingot",1));ticks(1);equal(1,gen.getItem(14).getCount(),"缺液启动不应吃铀");fillFluid(gen,FluidResource.of(IndustrialContent.COOLANT.get()),1000);long output=0;for(int i=0;i<20;i++){ticks(1);output+=gen.generated;}equal(108000,output,"核电一秒产能");equal(0,gen.fluids.getAmountAsLong(0),"一秒冷却液");int fuel=gen.fuelRemaining;ticks(1);equal(fuel,gen.fuelRemaining,"断液不得推进燃料");});
            check("RTG一铀8000秒并无需冷却",()->{var gen=machine("rtg_generator",area());gen.setItem(14,IndustrialContent.stack("uranium_ingot",1));ticks(1);equal(160000,gen.fuelTotal,"RTG燃料时间");equal(159999,gen.fuelRemaining,"RTG已运行一刻");equal(540,gen.generated,"RTG每刻发能");equal(86400000,gen.fuelEnergy,"每铀总产能");gen.running=false;});
            check("熔炉共享吞吐、小数tick及向下输出",()->{var be=machine("mana_furnace_t5",area());be.setItem(0,new ItemStack(Items.RAW_IRON,64));be.setItem(1,new ItemStack(Items.RAW_COPPER,64));be.setItem(2,new ItemStack(Items.RAW_GOLD,64));be.crystalRemaining=100000;var out=chest(be.getBlockPos().below());ticks(200);long count=chestCount(out,Items.IRON_INGOT)+chestCount(out,Items.COPPER_INGOT)+chestCount(out,Items.GOLD_INGOT);equal(128,count,"整机128倍吞吐");require(chestCount(out,Items.IRON_INGOT)>0&&chestCount(out,Items.COPPER_INGOT)>0&&chestCount(out,Items.GOLD_INGOT)>0,"输入槽未公平轮转");be.running=false;});
            check("高额配方低供给仍积累精确工作量",()->{BlockPos p=area();var core=beacon(p,1);var gen=machine("coal_generator",p.east());world.bind(core,gen);gen.setItem(14,new ItemStack(Items.COAL));var be=machine("mana_crusher_t1",p.west());world.bind(core,be);funded(be,209715200,2400,new ItemStack(Items.COBBLESTONE));ticks(100);equal(300,be.paid,"低供给累计扣费");require(be.work.positive(),"低供给没有推进工作");be.running=false;gen.running=false;});
            check("冷却液混合器100tick完成且能力事务回滚",()->{var be=machine("coolant_mixer",area());fillFluid(be,FluidResource.of(Fluids.WATER),1000);be.setItem(0,new ItemStack(Items.LAPIS_LAZULI));be.crystalRemaining=1000;ticks(100);equal(1000,be.fluids.getAmountAsLong(1),"混合产物");equal(0,be.fluids.getAmountAsLong(0),"混合耗水");equal(0,be.crystalRemaining,"混合耗能");try(var tx=Transaction.openRoot()){equal(1000,be.fluidsFor(Direction.DOWN).extract(FluidResource.of(IndustrialContent.COOLANT.get()),1000,tx),"流体提取模拟");}equal(1000,be.fluids.getAmountAsLong(1),"流体事务回滚");be.running=false;});
            check("锻炉25外壳及底层控制器向下输出",()->{var be=machine("mana_forge_t1",area());BlockPos center=be.getBlockPos().south().above();level.setBlock(center,Blocks.AIR.defaultBlockState(),3);int shells=0;for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){BlockPos at=center.offset(x,y,z);if(at.equals(be.getBlockPos())||x==0&&y==0&&z==0)continue;level.setBlock(at,cn.yumis.fight.modules.industry.platform.IndustrialStructures.structureMaterial(1).defaultBlockState(),3);shells++;}equal(25,shells,"外壳数量");var out=chest(be.getBlockPos().below());world.changed(level,be.getBlockPos());be.setItem(0,IndustrialContent.stack("lead_ingot",1));be.setItem(1,new ItemStack(Items.SAND));be.setItem(2,new ItemStack(Items.COAL));be.crystalRemaining=100;ticks(100);require(be.structureValid,"正确锻炉无效");equal(1,chestCount(out,IndustrialContent.ALL_ITEMS.get("sulfide").get()),"硫化物未向下输出");be.running=false;});
            check("通货结果锁定、保存状态和满仓零扣费",()->{var be=machine("currency_fabricator_t1",area());be.crystalRemaining=10000;ticks(1);ItemStack locked=be.batchResult.copy();long paid=be.paid;require(!locked.isEmpty()&&paid>0,"未锁定通货结果");var data=be.saveCustomOnly(level.registryAccess());var restored=machine("currency_fabricator_t1",be.getBlockPos().east(3));restored.loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),data));world.register(restored);require(ItemStack.isSameItemSameComponents(locked,restored.batchResult),"拆放重抽通货");equal(paid,restored.paid,"拆放丢进度");for(int i=4;i<13;i++)restored.setItem(i,new ItemStack(Items.STONE,64));ticks(20);equal(paid,restored.paid,"满仓仍扣费");be.running=false;restored.running=false;});
            check("传送逐件扣费、目标替换恢复、不加载远端",()->{BlockPos p=area();var core=beacon(p,1);var be=machine("transfer_crystal_t1",p.east(2));world.bind(core,be);var source=chest(be.getBlockPos().below());var target=chest(p.east(10));source.setItem(0,new ItemStack(Items.IRON_INGOT,8));be.targetDimension=level.dimension().identifier().toString();be.targetPosition=target.getBlockPos().asLong();core.energy=1000;be.targetFace=Direction.UP;int interval=IndustrialRules.current().transferIntervals[0];IndustrialRules.current().transferIntervals[0]=1;try{ticks(1);}finally{IndustrialRules.current().transferIntervals[0]=interval;}equal(8,chestCount(target,Items.IRON_INGOT),"传送数量");equal(200,core.energy,"实际件数扣费");BlockPos targetPos=target.getBlockPos();level.setBlock(targetPos,Blocks.AIR.defaultBlockState(),3);source.setItem(0,new ItemStack(Items.IRON_INGOT));core.energy=1000;int previous=IndustrialRules.current().transferIntervals[0];IndustrialRules.current().transferIntervals[0]=1;try{ticks(1);equal(1,source.getItem(0).getCount(),"目标消失吞物");equal(1000,core.energy,"目标消失扣费");var replacement=chest(targetPos);ticks(1);equal(1,chestCount(replacement,Items.IRON_INGOT),"同坐标容器恢复未继续");equal(900,core.energy,"恢复转移扣费");for(int slot=0;slot<replacement.getContainerSize();slot++)replacement.setItem(slot,new ItemStack(Items.STONE,64));source.setItem(0,new ItemStack(Items.IRON_INGOT));ticks(1);equal(900,core.energy,"满箱仍扣费");equal(1,source.getItem(0).getCount(),"满箱吞物");}finally{IndustrialRules.current().transferIntervals[0]=previous;}BlockPos distant=new BlockPos(20000000,80,20000000);be.getLevel().setBlock(be.getBlockPos(),be.getBlockState().setValue(MachineBlock.TIER,3),2);be.targetPosition=distant.asLong();require(!level.hasChunkAt(distant),"测试远端已意外加载");ticks(1);require(!level.hasChunkAt(distant),"运行主动加载远端区块");be.running=false;});
            check("成熟收获保留作物、完整掉落、缺能和满箱不重置",()->{BlockPos p=area();var core=beacon(p,1);var be=machine("harvest_crystal_t1",p.east(3));world.bind(core,be);var out=chest(be.getBlockPos().below());BlockPos crop=be.getBlockPos().below().east();level.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);be.harvestPhase=499;be.harvestCursor=5;core.energy=0;ticks(1);equal(7,level.getBlockState(crop).getValue(CropBlock.AGE),"缺能重置作物");core.energy=1000;ticks(2);equal(0,level.getBlockState(crop).getValue(CropBlock.AGE),"成熟作物未重置");equal(800,core.energy,"每株收获费用");require(chestCount(out,Items.WHEAT)>0,"漏掉成熟产物");for(int slot=0;slot<out.getContainerSize();slot++)out.setItem(slot,new ItemStack(Items.STONE,64));level.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);be.harvestPhase=499;be.harvestCursor=5;core.energy=1000;ticks(1);equal(7,level.getBlockState(crop).getValue(CropBlock.AGE),"满箱重置作物");equal(1000,core.energy,"满箱仍扣费");be.running=false;});
            check("传送直抽全档工业机器九个产物槽、保留同种原料及能源",()->verifyMachineTransfer());
            check("工业直抽无产物、满箱、部分接收和缺能守恒",()->verifyMachineTransferLimits());
            check("原位升级满包回滚及成功保留批次余能",()->verifyUpgrade());
            check("全档组件和功能水晶逐级升级、防重入及持久化",()->verifyUpgradeTiers());
            check("标准冷却液桶世界放置回收及禁止无限源",()->verifyCoolant());
            check("水晶自动供能兼容旧关闭状态",()->{var be=machine("mana_crusher_t1",area());be.crystalRemaining=77;var saved=be.saveCustomOnly(level.registryAccess());saved.putBoolean("backup",false);be.loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),saved));equal(25,be.crystalSupply(25),"旧关闭状态阻止供能");equal(52,be.crystalRemaining,"自动供能扣费");be.running=false;});
            check("直连光柱连接、10秒期限及关闭旧UI升级",()->verifyConnections());
            check("拆机掉空机器、库存和托管原料且不重复",()->verifyBreak());
            check("燃烧中生存手挖清空液体和剩余燃料时间",()->verifyFuelBreak());
            check("工作台有序升级及旧机器数据保护",()->verifyCrafting());
            check("未到区块的菜单保留机器类型等级及锻炉朝向",()->{var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{buf.writeBlockPos(area().north(20));buf.writeUtf("mana_forge_t3");buf.writeEnum(Direction.WEST);var menu=new IndustrialMenu(99,player.getInventory(),buf);require(menu.machine.device()==DeviceType.FORGE&&menu.machine.tier()==3&&menu.machine.getBlockState().getValue(MachineBlock.FACING)==Direction.WEST,"缺区块回退类型或朝向错误");}finally{buf.release();}});
            check("旧手选存档自动匹配及批次保留",()->{var be=machine("mana_crusher_t1",area());be.selected="missing:old_recipe";be.queued="missing:next";be.setItem(0,new ItemStack(Items.RAW_IRON));be.crystalRemaining=100;ticks(1);require(!be.batchRecipe.isEmpty()&&be.batchResult.is(IndustrialContent.ALL_ITEMS.get("crushed_iron").get()),"旧选择阻断自动匹配");long sequence=be.batchSequence;ticks(1);equal(sequence,be.batchSequence,"进行中批次被替换");var menu=new IndustrialMenu(91,player.getInventory(),be);require(!menu.clickMenuButton(player,100),"非生成器仍可手选");be.running=false;});
            check("权杖真实容器点击先于打开及传送绑定",()->verifyWand());
            check("同一双箱拒绝传送且不扣费",()->{BlockPos p=area();var core=beacon(p,1);var be=machine("transfer_crystal_t1",p.east(3));world.bind(core,be);BlockPos left=be.getBlockPos().below(),right=left.east();var one=chest(left);var two=chest(right);level.setBlock(left,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,ChestType.LEFT).setValue(ChestBlock.FACING,Direction.NORTH),3);level.setBlock(right,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,ChestType.RIGHT).setValue(ChestBlock.FACING,Direction.NORTH),3);one.setItem(0,new ItemStack(Items.DIAMOND,2));be.targetDimension=level.dimension().identifier().toString();be.targetPosition=right.asLong();core.energy=1000;int interval=IndustrialRules.current().transferIntervals[0];IndustrialRules.current().transferIntervals[0]=1;try{ticks(1);}finally{IndustrialRules.current().transferIntervals[0]=interval;}equal(1000,core.energy,"同箱扣费");equal(2,chestCount(one,Items.DIAMOND)+chestCount(two,Items.DIAMOND),"同箱物品变化");require(be.status.contains("同一"),"未识别同一双箱");be.running=false;});
            check("涡轮与温差真实燃烧总量、流体比例",()->{var turbine=machine("turbine_generator",area());turbine.setItem(14,new ItemStack(Items.COAL));var thermal=machine("thermal_generator",area());thermal.setItem(14,IndustrialContent.stack("sulfide",1));fillFluid(thermal,FluidResource.of(IndustrialContent.COOLANT.get()),3200);long water=0,turbinePower=0,thermalPower=0;for(int i=0;i<640;i++){if(i%20==0){fillFluid(turbine,FluidResource.of(Fluids.WATER),1000);water+=1000;}ticks(1);turbinePower+=turbine.generated;thermalPower+=thermal.generated;}equal(32000,water,"涡轮供水总量");equal(0,turbine.fluids.getAmountAsLong(0),"涡轮耗水比例");equal(0,thermal.fluids.getAmountAsLong(0),"温差耗冷却液比例");equal(34560,turbinePower,"涡轮每煤总产能");equal(204800,thermalPower,"温差每硫化物总产能");equal(0,turbine.fuelRemaining+thermal.fuelRemaining,"燃烧时间");turbine.running=thermal.running=false;});
            check("实际天空、玻璃、夜间和风能高度边界",()->verifySky());
            if(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource){JsonObject skipped=new JsonObject();skipped.addProperty("name","自然矿物生成（普通世界专用）");skipped.addProperty("passed",true);skipped.addProperty("skipped",true);skipped.addProperty("reason","当前隔离世界为超平坦，不含主世界天然石层或草丛");checks.add(skipped);}else check("自然新区块铅、铀、魔力草矿和末地外岛钛",()->verifyWorldgen());
            check("416格大状态分段NBT及旧格式可读",()->{String text="装备与工业".repeat(20000);var encoded=cn.yumis.fight.modules.inventory.api.InventoryContentTypes.STATE_CODEC.codec().encodeStart(NbtOps.INSTANCE,text).getOrThrow();require(encoded instanceof CompoundTag,"附件编码不是NBT复合");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();NbtIo.write((CompoundTag)encoded,new java.io.DataOutputStream(bytes));String decoded=cn.yumis.fight.modules.inventory.api.InventoryContentTypes.STATE_CODEC.codec().parse(NbtOps.INSTANCE,encoded).getOrThrow();require(text.equals(decoded),"分段大状态回读不一致");CompoundTag legacy=new CompoundTag();legacy.putString("value","legacy");require(cn.yumis.fight.modules.inventory.api.InventoryContentTypes.STATE_CODEC.codec().parse(NbtOps.INSTANCE,legacy).getOrThrow().equals("legacy"),"旧格式不兼容");});
        }finally{player.closeContainer();runtime.inventory.transact(player,"industrial-verification-restore/"+UUID.randomUUID(),new JsonObject(),()->runtime.inventory.verificationRestore(player,before),Map.of());player.teleportTo(level,position.x,position.y,position.z,Set.of(),yaw,pitch,true);runtime.profile(player);}
        JsonObject report=new JsonObject();report.add("checks",checks);long failures=0;for(JsonElement e:checks)if(!e.getAsJsonObject().get("passed").getAsBoolean())failures++;report.addProperty("failures",failures);report.addProperty("skipped",checks.asList().stream().filter(e->e.getAsJsonObject().has("skipped")).count());report.addProperty("protocol",cn.yumis.fight.architecture.ProtocolVersion.CURRENT);report.addProperty("third_party_pipe_mods_tested",false);write(player,"industrial-server-checks",report);player.sendSystemMessage(Component.literal("工业验收："+(checks.size()-failures-report.get("skipped").getAsLong())+" 项通过，"+report.get("skipped").getAsLong()+" 项跳过，"+failures+" 项失败"));
    }
    private void verifyMachineTransfer(){
        int interval=IndustrialRules.current().transferIntervals[0];IndustrialRules.current().transferIntervals[0]=1;
        try{
            for(var spec:IndustrialContent.SPECS.values())if(spec.device().processing()){
                BlockPos p=area();var core=beacon(p,1);var crystal=machine("transfer_crystal_t1",p.east(2));var source=machine(spec.id(),crystal.getBlockPos().below());source.running=false;world.bind(core,crystal);var target=chest(p.east(10));
                for(int i=0;i<source.slots().outputStart();i++)source.setItem(i,new ItemStack(Items.IRON_INGOT,7));
                for(int i=source.slots().outputStart();i<source.slots().outputEnd();i++)source.setItem(i,new ItemStack(Items.IRON_INGOT));
                source.setItem(source.slots().crystal(),IndustrialContent.stack("energy_crystal_"+(spec.tier()*2-1),2));source.setItem(source.slots().fuel(),new ItemStack(Items.COAL,3));
                crystal.targetDimension=level.dimension().identifier().toString();crystal.targetPosition=target.getBlockPos().asLong();core.energy=1000;
                try{
                    ticks(1);equal(8,chestCount(target,Items.IRON_INGOT),spec.id()+"首次搬运数量");equal(1,outputCount(source,Items.IRON_INGOT),spec.id()+"产物余量");equal(200,core.energy,spec.id()+"逐件扣费");
                    ticks(1);equal(9,chestCount(target,Items.IRON_INGOT),spec.id()+"九个产物槽未全部抽取");equal(0,outputCount(source,Items.IRON_INGOT),spec.id()+"产物未抽空");equal(100,core.energy,spec.id()+"尾批扣费");
                    ticks(1);equal(9,chestCount(target,Items.IRON_INGOT),spec.id()+"抽走同种原料");equal(100,core.energy,spec.id()+"无产物仍扣费");
                    for(int i=0;i<source.slots().outputStart();i++)equal(7,source.getItem(i).getCount(),spec.id()+"原料槽被转移："+i);
                    equal(2,source.getItem(source.slots().crystal()).getCount(),spec.id()+"供能水晶被转移");equal(3,source.getItem(source.slots().fuel()).getCount(),spec.id()+"燃料被转移");
                }finally{crystal.running=false;}
            }
        }finally{IndustrialRules.current().transferIntervals[0]=interval;}
    }
    private void verifyMachineTransferLimits(){
        BlockPos p=area();var core=beacon(p,1);var crystal=machine("transfer_crystal_t1",p.east(2));var source=machine("mana_crusher_t1",crystal.getBlockPos().below());source.running=false;world.bind(core,crystal);var target=chest(p.east(10));
        source.setItem(0,new ItemStack(Items.IRON_INGOT,8));crystal.targetDimension=level.dimension().identifier().toString();crystal.targetPosition=target.getBlockPos().asLong();core.energy=1000;
        int interval=IndustrialRules.current().transferIntervals[0];IndustrialRules.current().transferIntervals[0]=1;
        try{
            ticks(1);equal(0,chestCount(target,Items.IRON_INGOT),"无产物时转移原料");equal(1000,core.energy,"无产物时扣费");
            source.setItem(source.slots().outputStart(),new ItemStack(Items.IRON_INGOT,7));for(int i=0;i<target.getContainerSize();i++)target.setItem(i,new ItemStack(Items.STONE,64));
            ticks(1);equal(7,outputCount(source,Items.IRON_INGOT),"满箱吞产物");equal(1000,core.energy,"满箱仍扣费");
            target.setItem(26,new ItemStack(Items.IRON_INGOT,61));ticks(1);equal(64,chestCount(target,Items.IRON_INGOT),"部分接收数量");equal(4,outputCount(source,Items.IRON_INGOT),"部分接收源数量");equal(700,core.energy,"部分接收应仅扣三件费用");
            target.setItem(26,ItemStack.EMPTY);core.energy=0;ticks(1);equal(4,outputCount(source,Items.IRON_INGOT),"缺能吞产物");equal(0,chestCount(target,Items.IRON_INGOT),"缺能仍传送");
            core.energy=200;ticks(1);equal(2,outputCount(source,Items.IRON_INGOT),"部分供能源数量");equal(2,chestCount(target,Items.IRON_INGOT),"部分供能目标数量");equal(0,core.energy,"部分供能扣费");equal(8,source.getItem(0).getCount(),"边界检查转移原料");
        }finally{crystal.running=false;IndustrialRules.current().transferIntervals[0]=interval;}
    }
    private void verifyBreak(){
        var be=machine("mana_crusher_t2",area());var p=be.getBlockPos();be.crystalRemaining=777;be.setItem(0,new ItemStack(Items.RAW_IRON,7));be.setItem(4,new ItemStack(Items.DIAMOND,2));be.setItem(13,IndustrialContent.stack("energy_crystal_3",3));funded(be,100,100,IndustrialContent.stack("crushed_iron",2));be.paid=35;be.batchInputs.add(new ItemStack(Items.RAW_IRON));
        var packed=Block.getDrops(be.getBlockState(),level,p,be,player,ItemStack.EMPTY);equal(1,packed.size(),"空机器掉落");require(!packed.getFirst().has(DataComponents.BLOCK_ENTITY_DATA),"机器仍携带内容");
        var legacy=IndustrialContent.stack("mana_crusher_t2",1);legacy.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(be.getType(),be.saveCustomOnly(level.registryAccess())));
        var restored=machine("mana_crusher_t2",p.east(4));require(BlockItem.updateCustomBlockEntityTag(level,player,restored.getBlockPos(),legacy),"旧数据不能放置");equal(777,restored.crystalRemaining,"旧数据余能");restored.running=false;
        level.setBlock(p,be.getBlockState().setValue(MachineBlock.ACTIVE,true).setValue(MachineBlock.TIER,3),2);equal(7,be.getItem(0).getCount(),"状态变化散落库存");world.unload(be);equal(7,be.getItem(0).getCount(),"卸载散落库存");world.register(be);
        level.destroyBlock(p,true);var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p).inflate(2)).stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).toList();
        equal(8,drops.stream().filter(i->i.is(Items.RAW_IRON)).mapToLong(ItemStack::getCount).sum(),"原料未完整退回");equal(2,drops.stream().filter(i->i.is(Items.DIAMOND)).mapToLong(ItemStack::getCount).sum(),"产物遗失");equal(0,drops.stream().filter(i->i.is(IndustrialContent.ALL_ITEMS.get("crushed_iron").get())).count(),"未完成产物重复掉落");require(be.takeBreakContents().isEmpty(),"重复散落");equal(0,be.crystalRemaining+be.energy+be.fuelRemaining,"拆机状态未清空");
    }
    private void verifyFuelBreak(){
        var be=machine("thermal_generator",area());var p=be.getBlockPos();be.setItem(14,IndustrialContent.stack("sulfide",2));fillFluid(be,FluidResource.of(IndustrialContent.COOLANT.get()),2000);ticks(1);equal(1,be.getItem(14).getCount(),"燃料未开工");require(be.fuelRemaining>0,"燃烧未开始");
        var old=player.gameMode.getGameModeForPlayer();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.teleportTo(level,p.getX()+1,81,p.getZ()+1,Set.of(),0,0,true);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        try{require(player.gameMode.destroyBlock(p),"生存手挖未拆除");}finally{player.setGameMode(old);}
        var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p).inflate(2)).stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).toList();equal(1,drops.stream().filter(i->i.is(IndustrialContent.ALL_ITEMS.get("sulfide").get())).mapToInt(ItemStack::getCount).sum(),"燃烧中燃料被退回或库存遗失");equal(0,be.fluids.getAmountAsLong(0)+be.fuelRemaining,"拆除保留流体或燃烧");
    }
    private void verifyCrafting(){
        var holder=level.recipeAccess().recipeMap().byType(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream().filter(h->h.id().identifier().getPath().equals("industrial/mana_crusher_t2")).findFirst().orElseThrow();var recipe=holder.value();
        List<ItemStack> grid=new ArrayList<>();for(int i=0;i<9;i++)grid.add(i==4?IndustrialContent.stack("mana_crusher_t1",1):i%2==0?IndustrialContent.stack("energy_crystal_2",1):new ItemStack(Items.DIAMOND));
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,grid);require(recipe.matches(input,level),"有序升级未匹配");require(recipe.assemble(input).is(IndustrialContent.ALL_ITEMS.get("mana_crusher_t2").get()),"升级结果不对");
        var legacy=machine("mana_crusher_t1",area());grid.get(4).set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(legacy.getType(),legacy.saveCustomOnly(level.registryAccess())));input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,grid);require(!recipe.matches(input,level)&&recipe.assemble(input).isEmpty(),"旧数据被配方吞掉");legacy.running=false;
    }
    private void verifyWand(){
        var be=machine("transfer_crystal_t1",area());var target=chest(be.getBlockPos().east(3));player.teleportTo(level,be.getBlockPos().getX()+1,81,be.getBlockPos().getZ()+1,Set.of(),0,0,true);player.closeContainer();player.setShiftKeyDown(false);var wand=IndustrialContent.stack("mana_wand",1);CompoundTag data=new CompoundTag();data.putInt("mode",2);wand.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wand);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target.getBlockPos()),Direction.NORTH,target.getBlockPos(),false);require(player.gameMode.useItemOn(player,level,wand,net.minecraft.world.InteractionHand.MAIN_HAND,hit).consumesAction(),"容器点击未处理");require(player.containerMenu==player.inventoryMenu,"权杖打开了箱子");
        hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()),Direction.UP,be.getBlockPos(),false);player.gameMode.useItemOn(player,level,wand,net.minecraft.world.InteractionHand.MAIN_HAND,hit);equal(target.getBlockPos().asLong(),be.targetPosition,"目标绑定错误");require(be.targetFace==Direction.NORTH&&!be.targetDimension.isEmpty(),"接收面绑定错误");
        var sideHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target.getBlockPos()),Direction.WEST,target.getBlockPos(),false);player.gameMode.useItemOn(player,level,wand,net.minecraft.world.InteractionHand.MAIN_HAND,sideHit);player.gameMode.useItemOn(player,level,wand,net.minecraft.world.InteractionHand.MAIN_HAND,hit);require(be.targetFace==Direction.WEST,"重新绑定接收面失败");
        var distant=chest(be.getBlockPos().east(26));player.teleportTo(level,distant.getBlockPos().getX()+1,81,distant.getBlockPos().getZ()+1,Set.of(),0,0,true);var farHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(distant.getBlockPos()),Direction.UP,distant.getBlockPos(),false);player.gameMode.useItemOn(player,level,wand,net.minecraft.world.InteractionHand.MAIN_HAND,farHit);player.teleportTo(level,be.getBlockPos().getX()+1,81,be.getBlockPos().getZ()+1,Set.of(),0,0,true);player.gameMode.useItemOn(player,level,wand,net.minecraft.world.InteractionHand.MAIN_HAND,hit);equal(target.getBlockPos().asLong(),be.targetPosition,"越距失败覆盖旧绑定");
        player.setShiftKeyDown(true);player.gameMode.useItemOn(player,level,wand,net.minecraft.world.InteractionHand.MAIN_HAND,hit);player.setShiftKeyDown(false);require(be.targetDimension.isEmpty(),"清除绑定失败");be.running=false;
    }

    private void verifyUpgrade(){
        var be=machine("mana_crusher_t1",area());player.teleportTo(level,be.getBlockPos().getX()+2,81,be.getBlockPos().getZ(),Set.of(),0,0,true);be.crystalRemaining=77;be.setItem(13,IndustrialContent.stack("energy_crystal_1",3));funded(be,100,100,IndustrialContent.stack("crushed_iron",2));be.paid=25;be.work=Fraction.of(25*128);for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));player.getInventory().setItem(0,IndustrialContent.stack("industrial_upgrade_2",64));var extra=MenuHooks.extra(player);extra.bagTier=1;extra.bagSchema=1;for(int i=0;i<8;i++)extra.bag.setItem(i,new ItemStack(Items.STONE,64));player.closeContainer();player.getInventory().setSelectedSlot(0);
        try{IndustrialUpgrades.use(player,net.minecraft.world.InteractionHand.MAIN_HAND,be);throw new AssertionError("满包升级没有取消");}catch(InventoryTransactions.NoSpace expected){}equal(1,be.tier(),"失败升级改变等级");equal(64,player.getInventory().getItem(0).getCount(),"失败升级消耗组件");equal(3,be.getItem(13).getCount(),"失败升级丢水晶");player.getInventory().setItem(1,ItemStack.EMPTY);IndustrialUpgrades.use(player,net.minecraft.world.InteractionHand.MAIN_HAND,be);equal(2,be.tier(),"升级未生效");equal(63,player.getInventory().getItem(0).getCount(),"组件扣除数量");equal(77,be.crystalRemaining,"升级丢余能");equal(25,be.paid,"升级丢批次进度");require(be.getItem(13).isEmpty(),"旧水晶未退回");require(player.getInventory().getItem(1).is(IndustrialContent.ALL_ITEMS.get("energy_crystal_1").get()),"水晶未回背包");be.running=false;player.closeContainer();
    }
    private void verifyUpgradeTiers(){
        var be=machine("mana_crusher_t1",area());player.teleportTo(level,be.getBlockPos().getX()+2,81,be.getBlockPos().getZ(),Set.of(),0,0,true);player.getInventory().setSelectedSlot(0);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,IndustrialContent.stack("industrial_upgrade_3",2));
        try{IndustrialUpgrades.use(player,net.minecraft.world.InteractionHand.MAIN_HAND,be);throw new AssertionError("允许跳级");}catch(IllegalArgumentException expected){}equal(1,be.tier(),"跳级改变机器");equal(2,player.getMainHandItem().getCount(),"跳级消耗组件");
        be.crystalRemaining=123;be.setItem(0,new ItemStack(Items.RAW_IRON,7));be.beacon=UUID.randomUUID().toString();var binding=be.beacon;funded(be,100,100,IndustrialContent.stack("crushed_iron",2));be.paid=25;
        for(int next=2;next<=5;next++){player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,IndustrialContent.stack("industrial_upgrade_"+next,2));IndustrialUpgrades.use(player,net.minecraft.world.InteractionHand.MAIN_HAND,be);equal(next,be.tier(),"逐级结果");equal(1,player.getMainHandItem().getCount(),"逐级消耗");try{IndustrialUpgrades.use(player,net.minecraft.world.InteractionHand.MAIN_HAND,be);throw new AssertionError("重复操作再次升级");}catch(IllegalArgumentException expected){}equal(next,be.tier(),"重复操作改变机器");equal(1,player.getMainHandItem().getCount(),"重复操作消耗组件");}
        equal(123,be.crystalRemaining,"逐级丢余能");equal(25,be.paid,"逐级丢批次");equal(7,be.getItem(0).getCount(),"逐级丢库存");require(Objects.equals(binding,be.beacon),"逐级丢绑定");
        var restored=machine("mana_crusher_t5",be.getBlockPos().east(3));restored.loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),be.saveCustomOnly(level.registryAccess())));equal(123,restored.crystalRemaining,"回读丢余能");equal(25,restored.paid,"回读丢批次");require(Objects.equals(binding,restored.beacon),"回读丢绑定");be.running=restored.running=false;
        for(String id:List.of("transfer_crystal_t1","harvest_crystal_t1")){var crystal=machine(id,area());player.teleportTo(level,crystal.getBlockPos().getX()+2,81,crystal.getBlockPos().getZ(),Set.of(),0,0,true);for(int next=2;next<=3;next++){player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,IndustrialContent.stack(IndustrialUpgrades.required(crystal.device(),next),1));IndustrialUpgrades.use(player,net.minecraft.world.InteractionHand.MAIN_HAND,crystal);equal(next,crystal.tier(),"功能水晶逐级结果");require(player.getMainHandItem().isEmpty(),"功能升级未消耗道具");}crystal.running=false;}
    }
    private void verifyCoolant(){
        var oldMode=player.gameMode.getGameModeForPlayer();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);BlockPos source=area();
        try{for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setBlock(source.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<4;y++)level.setBlock(source.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}player.teleportTo(level,source.getX()+.5,source.getY()+1,source.getZ()+.5,Set.of(),0,90,true);player.setYRot(0);player.setXRot(90);player.getInventory().setSelectedSlot(0);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,IndustrialContent.stack("coolant_bucket",1));
            var placed=player.gameMode.useItem(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);require(placed.consumesAction(),"标准桶放置未成功");require(level.getFluidState(source).is(IndustrialContent.COOLANT.get())&&level.getFluidState(source).isSource(),"冷却液源未落地");require(player.getMainHandItem().is(Items.BUCKET),"放置未返回空桶");
            var picked=player.gameMode.useItem(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);require(picked.consumesAction(),"原版空桶回收失败");require(player.getMainHandItem().is(IndustrialContent.ALL_ITEMS.get("coolant_bucket").get()),"回收未得到冷却液桶");require(level.getFluidState(source).isEmpty(),"回收未移除源头");require(!IndustrialContent.COOLANT_TYPE.get().canConvertToSource(new net.neoforged.neoforge.fluids.FluidStack(IndustrialContent.COOLANT.get(),1000)),"冷却液允许无限源");
        }finally{player.setGameMode(oldMode);}
    }
    private void verifyConnections(){
        BlockPos p=area();var a=beacon(p,1);var b=beacon(p.east(3),1);world.link(a,b);var one=machine("mana_crusher_t1",p.west(2));var two=machine("coal_generator",p.east(6));world.bind(a,one);world.bind(b,two);var preview=world.connectionPreview(one);equal(1,preview.getAsJsonArray("beams").size(),"连线应仅包含本机与核心");equal(2,world.connectionPreview(a).getAsJsonArray("beams").size(),"信标直连应含机器和相邻信标");long remaining=preview.get("expires").getAsLong()-System.currentTimeMillis();require(remaining>9500&&remaining<=10000,"光柱不是10秒");var menu=new IndustrialMenu(91,player.getInventory(),one);require(!menu.clickMenuButton(player,2),"旧UI升级通路仍开放");require(!menu.clickMenuButton(player,1),"旧水晶备用开关仍开放");one.running=two.running=false;
    }
    private void verifySky(){
        var clock=level.dimensionType().defaultClock().orElseThrow();long old=level.clockManager().getTotalTicks(clock);boolean rain=level.isRaining(),thunder=level.isThundering();List<MachineBlockEntity> devices=new ArrayList<>();
        try{level.clockManager().setTotalTicks(clock,6000);level.getServer().setWeatherParameters(6000,6000,false,false);level.setRainLevel(0);level.setThunderLevel(0);BlockPos p=area().atY(200);for(int h=p.getY()+1;h<level.getMaxY();h++)level.setBlock(p.atY(h),Blocks.AIR.defaultBlockState(),2);var solar=machine("solar_collector",p);devices.add(solar);long total=0;for(int i=0;i<20;i++){ticks(1);total+=solar.generated;}equal(2,total,"日曜整数累计");level.setBlock(p.above(),Blocks.GLASS.defaultBlockState(),3);ticks(1);equal(2,solar.currentPower,"玻璃不应遮光");level.setBlock(p.above(),Blocks.TINTED_GLASS.defaultBlockState(),3);ticks(1);equal(0,solar.currentPower,"遮光玻璃应停机");level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),3);level.clockManager().setTotalTicks(clock,12000);ticks(1);equal(0,solar.currentPower,"夜间应停机");for(int y:new int[]{59,60,64,65}){BlockPos at=p.east(y-58).atY(y);level.getChunk(at.getX()>>4,at.getZ()>>4);for(int h=y+1;h<level.getMaxY();h++)level.setBlock(at.atY(h),Blocks.AIR.defaultBlockState(),2);var wind=machine("wind_generator",at);devices.add(wind);ticks(1);equal(y==59?0:y==65?51:50,wind.currentPower,"风能高度 "+y);level.setBlock(at.above(3),Blocks.STONE.defaultBlockState(),3);ticks(1);equal(0,wind.currentPower,"非露天风能应停机");}}
        finally{for(var be:devices)be.running=false;level.clockManager().setTotalTicks(clock,old);level.getServer().setWeatherParameters(0,6000,rain,thunder);level.setRainLevel(rain?1:0);level.setThunderLevel(thunder?1:0);}
    }
    private void verifyWorldgen(){
        int freshRegion=2000+(int)Math.floorMod(System.nanoTime(),10000);Map<String,Integer> counts=new LinkedHashMap<>();for(String id:List.of("lead_ore","uranium_ore","magic_ore","magic_grass","titanium_ore"))counts.put(id,0);
        for(int i=0;i<32;i++){var chunk=level.getChunk(freshRegion+i%8,freshRegion+34+i/8);int x0=chunk.getPos().x()*16,z0=chunk.getPos().z()*16;for(int x=0;x<16;x++)for(int z=0;z<16;z++){for(int y=-64;y<=48;y++){BlockState state=chunk.getBlockState(new BlockPos(x0+x,y,z0+z));for(String id:List.of("lead_ore","uranium_ore","magic_ore"))if(state.is(IndustrialContent.ALL_BLOCKS.get(id).get())){counts.merge(id,1,Integer::sum);require(id.equals("lead_ore")?y>=-32&&y<=48:id.equals("uranium_ore")?y<=0:y>=-32&&y<=32,"矿石超出配置高度");}}for(int y=49;y<level.getMaxY();y++)if(chunk.getBlockState(new BlockPos(x0+x,y,z0+z)).is(IndustrialContent.ALL_BLOCKS.get("magic_grass").get()))counts.merge("magic_grass",1,Integer::sum);}}
        var end=level.getServer().getLevel(net.minecraft.world.level.Level.END);require(end!=null,"末地不可用");for(int i=0;i<32&&counts.get("titanium_ore")==0;i++){var chunk=end.getChunk(320+i%8,320+i/8);int x0=chunk.getPos().x()*16,z0=chunk.getPos().z()*16;for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<=80;y++)if(chunk.getBlockState(new BlockPos(x0+x,y,z0+z)).is(IndustrialContent.ALL_BLOCKS.get("titanium_ore").get()))counts.merge("titanium_ore",1,Integer::sum);}
        write(player,"industrial-worldgen",JSON.toJsonTree(counts));for(var entry:counts.entrySet())require(entry.getValue()>0,"未发现自然生成 "+entry.getKey());
    }
    private void setup(){
        BlockPos base=new BlockPos(0,80,0);for(int x=-12;x<=20;x++)for(int z=-10;z<=14;z++){level.getChunk(x>>4,z>>4);for(int y=79;y<=88;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);level.setBlock(new BlockPos(x,78,z),Blocks.STONE_BRICKS.defaultBlockState(),3);}player.teleportTo(level,-4,79,3,Set.of(),-145,0,true);
        var core=beacon(base,1);var coal=machine("coal_generator",base.east());coal.setItem(14,new ItemStack(Items.COAL,64));world.bind(core,coal);
        var second=machine("coal_generator",base.south());second.setItem(14,new ItemStack(Items.COAL,64));world.bind(core,second);
        var miner=machine("mineral_generator_t1",base.west(2).above(2));miner.selected=IndustrialContent.id("industrial/generate_iron").toString();world.bind(core,miner);
        var crusher=machine("mana_crusher_t1",miner.getBlockPos().below());world.bind(core,crusher);var furnace=machine("mana_furnace_t1",crusher.getBlockPos().below());world.bind(core,furnace);chest(furnace.getBlockPos().below());
        machine("solar_collector",base.north());machine("transfer_crystal_t1",base.east(3));machine("harvest_crystal_t1",base.south(3));
        InventoryPacking.insert(player,IndustrialContent.stack("mana_wand",1));InventoryPacking.insert(player,IndustrialContent.stack("energy_crystal_1",64));InventoryPacking.insert(player,IndustrialContent.stack("energy_crystal_2",64));InventoryPacking.insert(player,IndustrialContent.stack("industrial_upgrade_2",64));
        JsonObject intent=new JsonObject();intent.addProperty("industrialDemo",true);runtime.inventory.transact(player,"industrial-demo/"+UUID.randomUUID(),intent,()->{var extra=MenuHooks.extra(player);extra.bagSchema=3;extra.bagTier=16;extra.bagPage=6;extra.bag.setItem(400,new ItemStack(Items.DIAMOND,12));},Map.of());runtime.profile(player);world.opening.prepare(player,miner);
    }
}
