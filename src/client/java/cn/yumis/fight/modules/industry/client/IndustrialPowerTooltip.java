package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.api.*;
import cn.yumis.fight.modules.industry.platform.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Only advertise a numeric rate when every applicable recipe has that same rate. */
final class IndustrialPowerTooltip {
    private IndustrialPowerTooltip(){}
    static void append(ItemTooltipEvent event){
        if(!(event.getItemStack().getItem() instanceof BlockItem item)||!(item.getBlock() instanceof MachineBlock block))return;
        var spec=block.initial;var kind=spec.device();var rules=IndustrialRules.current();
        Double consumption=null,production=null;
        if(kind.generator()){
            consumption=kind.has(DeviceType.Capability.IGNITION)?null:0d;
            if(kind==DeviceType.INFINITE_POWER)production=IndustrialGeneration.INFINITE_TOWER_GENERATION*20d;
            else if(!kind.has(DeviceType.Capability.SOLAR)&&!kind.has(DeviceType.Capability.WIND)&&!kind.has(DeviceType.Capability.ENVIRONMENTAL_HEAT)&&kind!=DeviceType.NUCLEAR)production=recipePower(spec,true);
        }else{
            production=0d;
            if(kind.has(DeviceType.Capability.PUMP))consumption=rules.pumpEnergy*20d/rules.pumpInterval;
            else if(kind.processing()&&!kind.has(DeviceType.Capability.MINING)&&!kind.has(DeviceType.Capability.SMELTING))consumption=recipePower(spec,false);
            else if(!kind.processing()&&!kind.functional()&&!kind.currencyUpgrader())consumption=0d;
        }
        if(consumption!=null)event.getToolTip().add(Component.literal("额定耗电量 · "+EnergyDisplay.format(consumption)+" 魔能/秒"));
        if(production!=null)event.getToolTip().add(Component.literal("额定产电量 · "+EnergyDisplay.format(production)+" 魔能/秒"));
        if(kind==DeviceType.CRYSTAL_BURNER){event.getToolTip().add(Component.literal("1—8阶魔能水晶燃料；一阶每秒燃烧10%。"));event.getToolTip().add(Component.literal("每升一阶速度降至25%；每颗总产能×5。"));}
        if(kind==DeviceType.MAGNETIC_CONFINEMENT)event.getToolTip().add(Component.literal("超导线圈形成强磁场，在约束容器内制造恒星点火芯。"));
        if(kind==DeviceType.NUCLEAR){event.getToolTip().add(Component.literal("缺水仍运行并升温；100%核心温度爆炸。").withColor(0xff8855));event.getToolTip().add(Component.literal("高温产能趋近5倍；保温水量随温度增长。"));event.getToolTip().add(Component.literal("双倍保温供水才降温；停机与卸载保留温度。"));}
    }
    private static Double recipePower(IndustrialContent.Spec spec,boolean generation){
        var kind=spec.device();String machine=(generation?"fuel_":"")+kind.id;
        Fraction rate=null;
        for(var holder:IndustrialRecipeCache.recipes.byType(IndustrialContent.RECIPE_TYPE.get())){
            var recipe=holder.value();if(!recipe.machine().equals(machine)||recipe.minimumTier()>IndustrialTechnology.processing(spec.id())||kind.exactRecipeTier()&&recipe.minimumTier()!=spec.tier())continue;
            Fraction next=Fraction.of(recipe.energy()).multiply(Fraction.of(20)).divide(Fraction.of(recipe.ticks()));
            if(!generation)next=next.multiply(Fraction.of((long)kind.speed(spec.tier(),IndustrialRules.current().furnaceSpeeds)*kind.lineCount(spec.tier())));
            if(rate!=null&&!rate.equals(next))return null;rate=next;
        }
        return rate==null?null:rate.value();
    }
}
