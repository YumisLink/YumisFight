package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.EnergyMath;
import cn.yumis.fight.modules.industry.api.DeviceType;

import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class IndustrialUpgrades {
    private IndustrialUpgrades(){}
    public static String id(ItemStack stack){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();}
    public static boolean recognized(ItemStack stack){var key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());return key.getNamespace().equals(IndustrialContent.NS)&&(key.getPath().matches("industrial_upgrade_[1-5]")||Set.of("vanadium_steel","mithril_niobium_alloy","reinforced_frame","heatproof_frame","precision_frame").contains(key.getPath()));}
    public static String required(DeviceType kind,int next){return IndustrialUpgradeCosts.held(kind,next);}
    public static void use(ServerPlayer player,InteractionHand hand,MachineBlockEntity machine){
        var inventory=IndustrialPorts.inventory();ItemStack held=player.getItemInHand(hand);String expected=required(machine.device(),machine.tier()+1);
        int max=machine.device().maximumTier();
        if(machine.tier()>=max)throw new IllegalArgumentException("此设备没有下一档升级");
        if(!held.is(IndustrialContent.ALL_ITEMS.get(expected).get()))throw new IllegalArgumentException("需要手持"+IndustrialContent.stack(expected,1).getHoverName().getString()+"逐级升级；还需工作台配方的其他材料");
        JsonObject intent=new JsonObject();intent.addProperty("machine",machine.identity.toString());intent.addProperty("tier",machine.tier());intent.addProperty("item",id(held));
        inventory.transactMachine(player,"machine-upgrade/"+UUID.randomUUID(),intent,machine,()->{
            if(!machine.stillValid(player)||player.level().getBlockEntity(machine.getBlockPos())!=machine)throw new IllegalArgumentException("机器已失效");
            var costs=IndustrialUpgradeCosts.costs(machine.device(),machine.tier()+1);
            for(var entry:costs.entrySet()){
                String key=entry.getKey().contains(":")?entry.getKey():IndustrialContent.NS+":"+entry.getKey();var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(key));int count=0;
                for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(item))count+=player.getInventory().getItem(i).getCount();if(player.getOffhandItem().is(item))count+=player.getOffhandItem().getCount();
                if(count<entry.getValue())throw new IllegalArgumentException("升级材料不足："+new ItemStack(item).getHoverName().getString()+" ×"+entry.getValue());
            }
            for(var entry:costs.entrySet()){
                String key=entry.getKey().contains(":")?entry.getKey():IndustrialContent.NS+":"+entry.getKey();var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(key));int left=entry.getValue();
                for(int i=0;i<36&&left>0;i++){var stack=player.getInventory().getItem(i);if(stack.is(item)){int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;}}if(left>0)player.getOffhandItem().shrink(left);
            }
            ItemStack crystal=machine.getItem(machine.slots().crystal());
            if(!crystal.isEmpty()&&!EnergyMath.accepts(machine.tier()+1,IndustrialContent.crystalTier(crystal))){inventory.requireSpace(player,crystal.copy());machine.setItem(machine.slots().crystal(),ItemStack.EMPTY);}
            player.level().setBlock(machine.getBlockPos(),machine.getBlockState().setValue(MachineBlock.TIER,machine.tier()+1),3);
            machine.setChanged();IndustrialWorld.get(player.level()).changed(player.level(),machine.getBlockPos());
        },Map.of());
        if(machine.device().has(DeviceType.Capability.STORAGE_NETWORK))IndustrialWorld.get(player.level()).refreshCoverage(machine);
        if(machine.device().has(DeviceType.Capability.MINING))IndustrialDeposits.get(player.level()).reveal(player,player.level(),machine.getBlockPos().getX()>>4,machine.getBlockPos().getZ()>>4,machine.tier());
        IndustrialPorts.message(player,"升级完成："+machine.tier()+"档");
    }
}
