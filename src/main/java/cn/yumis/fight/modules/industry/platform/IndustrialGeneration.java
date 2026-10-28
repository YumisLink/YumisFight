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
        if(be.device().has(DeviceType.Capability.SOLAR)||be.device().has(DeviceType.Capability.WIND)){Environment env=environments.computeIfAbsent(level,l->new Environment(l.getDefaultClockTime(),l.isRaining(),l.isThundering(),l.dimensionType().hasSkyLight(),l.dimensionType().hasSkyLight()&&!l.dimensionType().hasFixedTime()&&l.dimensionType().defaultClock().isPresent()));if(be.device().has(DeviceType.Capability.WIND)&&!WindGeneratorPartBlock.complete(level,be.getBlockPos())&&!WindGeneratorPartBlock.ensure(level,be.getBlockPos())){be.status="风机需要上方两格空间";return 0;}boolean sky=skyVisible(level,be.device().has(DeviceType.Capability.WIND)?be.getBlockPos().above(2):be.getBlockPos());long power=0;if(be.device().has(DeviceType.Capability.WIND)){int y=be.getBlockPos().getY();if(sky&&env.skylight&&y>=rules.windBaseHeight)power=rules.windBasePower+(long)Math.floorDiv(y-rules.windBaseHeight,rules.windHeightStep)*rules.windPowerStep;}else{int state=EnergyMath.solar(env.time,env.rain,env.thunder,sky,env.solar,rules.solarPeriods);power=state==0?0:env.rain?rules.solarRainPower:state==2?rules.solarDayPower:rules.solarEdgePower;}be.currentPower=power;long whole=(power+be.powerRemainder)/20;be.powerRemainder=(int)((power+be.powerRemainder)%20);be.status=power>0?"发能 "+power+" 点/秒":"当前环境不满足发电条件";be.setChanged();return whole;}
        if(be.fuelRemaining<=0){
            var fuels=level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals("fuel_"+be.device().id)).toList();IndustrialRecipe fuel=null;for(var h:fuels)if(h.value().takePlan(List.of(be.getItem(MachineBlockEntity.FUEL)))!=null){fuel=h.value();break;}
            if(fuel==null){be.status="缺少燃料";return 0;}
            var g=rules.generators.get(be.device().id);if(g.fluidPerSecond()>0&&(!IndustrialFluids.matches(be.fluids.getResource(0),g.fluid())||be.fluids.getAmountAsInt(0)<(g.fluidPerSecond()+be.fluidRemainder)/20)){be.status="缺少必要流体";return 0;}
            int[] take=fuel.takePlan(List.of(be.getItem(MachineBlockEntity.FUEL)));be.removeItem(MachineBlockEntity.FUEL,take[0]);be.fuelTotal=be.fuelRemaining=fuel.ticks();be.fuelEnergy=fuel.energy();be.fuelFluid=g.fluid();be.fuelFluidRate=g.fluidPerSecond();
        }
        int fluid=(be.fuelFluidRate+be.fluidRemainder)/20;
        if(fluid>0){if(!IndustrialFluids.matches(be.fluids.getResource(0),be.fuelFluid)||be.fluids.getAmountAsInt(0)<fluid){be.status="缺少必要流体，燃料已暂停";return 0;}try(var tx=Transaction.openRoot()){if(be.fluids.extract(0,be.fluids.getResource(0),fluid,tx)!=fluid)return 0;tx.commit();}}
        be.fluidRemainder=(be.fuelFluidRate+be.fluidRemainder)%20;int done=be.fuelTotal-be.fuelRemaining;long generated=EnergyMath.ceilProduct(be.fuelEnergy,done+1,be.fuelTotal)-EnergyMath.ceilProduct(be.fuelEnergy,done,be.fuelTotal);be.fuelRemaining--;be.currentPower=be.fuelEnergy*20/be.fuelTotal;be.status="发能中";be.setChanged();return generated;
    }
}
