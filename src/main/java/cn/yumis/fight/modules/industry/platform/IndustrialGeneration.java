package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.api.IndustrialRecipe;

import cn.yumis.fight.modules.industry.api.EnergyMath;

import cn.yumis.fight.modules.industry.api.DeviceType;

import com.google.gson.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;

import net.minecraft.server.level.*;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

import net.minecraft.world.level.saveddata.*;

import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Industrial module implementation; owns one scheduling responsibility. */
public final class IndustrialGeneration {
    private record Environment(long time,boolean rain,boolean thunder,boolean skylight,boolean solar){}
    private final Map<ServerLevel,Environment> environments=new HashMap<>();
    public void beginTick(){environments.clear();}
    public static boolean skyVisible(ServerLevel level,BlockPos machine){var chunk=level.getChunkSource().getChunkNow(machine.getX()>>4,machine.getZ()>>4);return chunk!=null&&machine.getY()+1>=chunk.getSkyLightSources().getLowestSourceY(machine.getX()&15,machine.getZ()&15);}
    public long generate(MachineBlockEntity be){
        be.currentPower=0;
        if(!be.running){be.status="已停止";return 0;}ServerLevel level=(ServerLevel)be.getLevel();IndustrialRules rules=IndustrialRules.current();
        if(be.device().needsStructure()){
            if(!be.structureLoaded){be.status="结构区块未加载，已暂停";return 0;}
            if(!be.structureValid){extinguish(be);be.status="结构不完整";return 0;}
        }
        if(be.device().has(DeviceType.Capability.IGNITION)&&!be.ignited){be.status="等待点火 · 已充能 "+be.ignitionPaid+" / "+IGNITION_ENERGY;return 0;}
        if(be.device().has(DeviceType.Capability.SOLAR)||be.device().has(DeviceType.Capability.WIND)){Environment env=environments.computeIfAbsent(level,l->new Environment(l.getDefaultClockTime(),l.isRaining(),l.isThundering(),l.dimensionType().hasSkyLight(),l.dimensionType().hasSkyLight()&&!l.dimensionType().hasFixedTime()&&l.dimensionType().defaultClock().isPresent()));if(be.device().has(DeviceType.Capability.WIND)&&!WindGeneratorPartBlock.complete(level,be.getBlockPos())&&!WindGeneratorPartBlock.ensure(level,be.getBlockPos())){be.status="风机需要上方两格空间";return 0;}boolean sky=skyVisible(level,be.device().has(DeviceType.Capability.WIND)?be.getBlockPos().above(2):be.getBlockPos());long power=0;if(be.device().has(DeviceType.Capability.WIND)){int y=be.getBlockPos().getY();if(sky&&env.skylight&&y>=rules.windBaseHeight)power=rules.windBasePower+(long)Math.floorDiv(y-rules.windBaseHeight,rules.windHeightStep)*rules.windPowerStep;}else{int state=EnergyMath.solar(env.time,env.rain,env.thunder,sky,env.solar,rules.solarPeriods);power=state==0?0:env.rain?rules.solarRainPower:state==2?rules.solarDayPower:rules.solarEdgePower;}be.currentPower=power;long whole=(power+be.powerRemainder)/20;be.powerRemainder=(int)((power+be.powerRemainder)%20);be.status=power>0?"发能 "+power+" 点/秒":"当前环境不满足发电条件";be.setChanged();return whole;}
        if(be.fuelRemaining<=0)try(var tx=Transaction.openRoot()){if(!loadFuel(be,tx)){extinguish(be);be.status="缺少燃料或必要流体";return 0;}tx.commit();}
        int fluid=(be.fuelFluidRate+be.fluidRemainder)/20;
        if(fluid>0&&(!IndustrialFluids.matches(be.fluids.getResource(0),be.fuelFluid)||be.fluids.getAmountAsInt(0)<fluid)){extinguish(be);be.status="缺少必要流体，燃料已暂停";return 0;}
        int done=be.fuelTotal-be.fuelRemaining;long generated=EnergyMath.ceilProduct(be.fuelEnergy,done+1,be.fuelTotal)-EnergyMath.ceilProduct(be.fuelEnergy,done,be.fuelTotal);
        try(var tx=Transaction.openRoot()){
            be.recordGeneration(tx);
            if(fluid>0&&be.fluids.extract(0,be.fluids.getResource(0),fluid,tx)!=fluid)return 0;
            if(be.fuelRemaining==1)for(var output:be.fuelResults)if(be.lane(0).insertOutput(output.copy(),tx)!=output.getCount()){be.status="燃料产物空间不足，已暂停";return 0;}
            be.fluidRemainder=(be.fuelFluidRate+be.fluidRemainder)%20;be.fuelRemaining--;if(be.fuelRemaining==0)be.fuelResults=new ArrayList<>();tx.commit();
        }
        be.currentPower=be.fuelEnergy*20/be.fuelTotal;be.status="发能中";be.setChanged();return generated;
    }
    public static final long IGNITION_ENERGY=100000000;
    private static void extinguish(MachineBlockEntity be){if(be.device().has(DeviceType.Capability.IGNITION)&&be.ignited){be.ignited=false;be.ignitionPaid=0;be.setChanged();}}
    private static boolean loadFuel(MachineBlockEntity be,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
        var level=(ServerLevel)be.getLevel();
        var fuels=level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals("fuel_"+be.device().id)).sorted(Comparator.comparing(h->h.id().identifier().toString())).toList();
        for(var holder:fuels){var fuel=holder.value();int[] take=fuel.takePlan(List.of(be.getItem(be.slots().fuel())));if(take==null)continue;
            var cooling=IndustrialRules.current().generators.get(be.device().id);
            if(cooling.fluidPerSecond()>0&&(!IndustrialFluids.matches(be.fluids.getResource(0),cooling.fluid())||be.fluids.getAmountAsLong(0)<(cooling.fluidPerSecond()+be.fluidRemainder)/20))return false;
            be.recordGeneration(tx);var resource=be.items.getResource(be.slots().fuel());if(be.items.extract(be.slots().fuel(),resource,take[0],tx)!=take[0])return false;
            be.fuelTotal=be.fuelRemaining=fuel.ticks();be.fuelEnergy=fuel.energy();be.fuelFluid=cooling.fluid();be.fuelFluidRate=cooling.fluidPerSecond();be.fuelResults=new ArrayList<>(fuel.results());return true;
        }return false;
    }
    /** Receives only surplus left after existing consumers; it cannot starve its own coolant supply. */
    public long ignite(MachineBlockEntity be,long available){
        if(!be.running||be.ignited||!be.device().has(DeviceType.Capability.IGNITION)||!be.structureLoaded||!be.structureValid)return 0;
        int slot=be.slots().inputStart(0);var core=be.getItem(slot);
        if(!core.is(IndustrialContent.ALL_ITEMS.get("fusion_ignition_core").get())){be.status="需要T6点火芯";return 0;}
        var cooling=be.fuelRemaining>0?new IndustrialRules.Generator(be.fuelFluidRate,be.fuelFluid):IndustrialRules.current().generators.get(be.device().id);
        if(!IndustrialFluids.matches(be.fluids.getResource(0),cooling.fluid())||be.fluids.getAmountAsLong(0)<(cooling.fluidPerSecond()+be.fluidRemainder)/20){be.status="点火前需要冷却液";return 0;}
        long accepted=Math.min(available,Math.max(0,IGNITION_ENERGY-be.ignitionPaid));
        try(var tx=Transaction.openRoot()){
            be.recordGeneration(tx);be.ignitionPaid+=accepted;
            if(be.ignitionPaid>=IGNITION_ENERGY){
                if(be.fuelRemaining<=0&&!loadFuel(be,tx)){be.status="点火前需要聚变燃料";return 0;}
                if(be.items.extract(slot,net.neoforged.neoforge.transfer.item.ItemResource.of(core),1,tx)!=1)return 0;
                be.ignited=true;be.ignitionPaid=0;be.status="点火完成，下一tick发电";
            }else be.status="点火充能 "+be.ignitionPaid+" / "+IGNITION_ENERGY;
            tx.commit();
        }be.consumed+=accepted;return accepted;
    }
}
