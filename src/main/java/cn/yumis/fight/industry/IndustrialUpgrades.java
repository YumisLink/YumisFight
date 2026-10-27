package cn.yumis.fight.industry;

import cn.yumis.fight.server.ServerRuntime;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class IndustrialUpgrades {
    private IndustrialUpgrades(){}
    public static String id(ItemStack stack){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();}
    public static boolean recognized(ItemStack stack){var key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());return key.getNamespace().equals(IndustrialContent.NS)&&key.getPath().matches("industrial_upgrade_[1-5]");}
    public static String required(DeviceKind kind,int next){return "industrial_upgrade_"+(kind.graded?next:next==2?2:kind==DeviceKind.TRANSFER?5:4);}
    public static void use(ServerPlayer player,InteractionHand hand,MachineBlockEntity machine){
        var runtime=ServerRuntime.get();ItemStack held=player.getItemInHand(hand);String expected=required(machine.kind(),machine.tier()+1);
        int max=machine.kind().graded?5:machine.kind().functional()?3:machine.tier();
        if(machine.tier()>=max)throw new IllegalArgumentException("此设备没有下一档升级");
        if(!held.is(IndustrialContent.ALL_ITEMS.get(expected).get()))throw new IllegalArgumentException("需要手持"+IndustrialContent.stack(expected,1).getHoverName().getString()+"逐级升级");
        JsonObject intent=new JsonObject();intent.addProperty("machine",machine.identity.toString());intent.addProperty("tier",machine.tier());intent.addProperty("item",id(held));
        runtime.inventory.transactMachine(player,"machine-upgrade/"+UUID.randomUUID(),intent,machine,()->{
            if(!machine.stillValid(player)||player.level().getBlockEntity(machine.getBlockPos())!=machine)throw new IllegalArgumentException("机器已失效");
            player.getItemInHand(hand).shrink(1);
            ItemStack crystal=machine.getItem(MachineBlockEntity.CRYSTAL);
            if(!crystal.isEmpty()&&!EnergyMath.accepts(machine.tier()+1,IndustrialContent.crystalTier(crystal))){runtime.inventory.requireSpace(player,crystal.copy());machine.setItem(MachineBlockEntity.CRYSTAL,ItemStack.EMPTY);}
            player.level().setBlock(machine.getBlockPos(),machine.getBlockState().setValue(MachineBlock.TIER,machine.tier()+1),3);
            machine.setChanged();IndustrialWorld.get(player.level()).changed(player.level(),machine.getBlockPos(),true);
        },Map.of());
        runtime.message(player,"升级完成："+machine.tier()+"档");
    }
}
