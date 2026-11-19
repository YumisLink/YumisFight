package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.api.IndustrialRecipe;

import cn.yumis.fight.modules.industry.api.EnergyMath;
import cn.yumis.fight.modules.industry.api.EnergyDisplay;
import cn.yumis.fight.modules.industry.api.Fraction;

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
    public static final long INFINITE_TOWER_GENERATION=100_000_000L,INFINITE_TOWER_CAPACITY=100_000_000L;
    private static final long HEAT_PRECISION=1_200_000_000L;
    private record Environment(long time,boolean rain,boolean thunder,boolean skylight,boolean solar){}
    private final Map<ServerLevel,Environment> environments=new HashMap<>();
    public void beginTick(){environments.clear();}
    public static boolean skyVisible(ServerLevel level,BlockPos machine){var chunk=level.getChunkSource().getChunkNow(machine.getX()>>4,machine.getZ()>>4);return chunk!=null&&machine.getY()+1>=chunk.getSkyLightSources().getLowestSourceY(machine.getX()&15,machine.getZ()&15);}
    public long generate(MachineBlockEntity be){
        be.currentPower=0;
        if(be.device()==DeviceType.BUDDY_SHELL){be.status="未装载";return 0;}
        if(be.device()==DeviceType.NUCLEAR&&be.coreHeat.compareTo(Fraction.of(1))>=0){overheat(be);return 0;}
        if(!be.running){be.status="已停止";return 0;}ServerLevel level=(ServerLevel)be.getLevel();IndustrialRules rules=IndustrialRules.current();
        if(be.device()==DeviceType.INFINITE_POWER){be.currentPower=INFINITE_TOWER_GENERATION*20;be.status="无限供能 · 每tick 1亿魔能";return INFINITE_TOWER_GENERATION;}
        if(be.device().needsStructure()){
            if(!be.structureLoaded){be.status="结构区块未加载，已暂停";return 0;}
            if(!be.structureValid){extinguish(be);be.status="结构不完整";return 0;}
        }
        if(be.device()==DeviceType.BUDDY_GENERATOR){
            be.currentPower=600;
            be.status=be.getBlockState().getValue(MachineBlock.CONNECTED)?"群友 · 600 魔能/秒":"群友 · 未连接信标";
            return be.currentPower/20;
        }
        if(be.device().has(DeviceType.Capability.IGNITION)&&!be.ignited){be.status="等待点火";return 0;}
        if(be.device().has(DeviceType.Capability.SOLAR)||be.device().has(DeviceType.Capability.WIND)){Environment env=environments.computeIfAbsent(level,l->new Environment(l.getDefaultClockTime(),l.isRaining(),l.isThundering(),l.dimensionType().hasSkyLight(),l.dimensionType().hasSkyLight()&&!l.dimensionType().hasFixedTime()&&l.dimensionType().defaultClock().isPresent()));if(be.device().has(DeviceType.Capability.WIND)&&!WindGeneratorPartBlock.complete(level,be.getBlockPos())&&!WindGeneratorPartBlock.ensure(level,be.getBlockPos())){be.status="风机需要上方两格空间";return 0;}boolean sky=skyVisible(level,be.device().has(DeviceType.Capability.WIND)?be.getBlockPos().above(2):be.getBlockPos());long power=0;if(be.device().has(DeviceType.Capability.WIND)){int y=be.getBlockPos().getY();if(sky&&env.skylight&&y>=rules.windBaseHeight)power=rules.windBasePower+(long)Math.floorDiv(y-rules.windBaseHeight,rules.windHeightStep)*rules.windPowerStep;}else{int state=EnergyMath.solar(env.time,env.rain,env.thunder,sky,env.solar,rules.solarPeriods);power=state==0?0:env.rain?rules.solarRainPower:state==2?rules.solarDayPower:rules.solarEdgePower;}be.currentPower=power;long whole=(power+be.powerRemainder)/20;be.powerRemainder=(int)((power+be.powerRemainder)%20);be.status=power>0?"发能 "+EnergyDisplay.format(power)+" 点/秒":"当前环境不满足发电条件";be.setChanged();return whole;}
        if(be.device().has(DeviceType.Capability.ENVIRONMENTAL_HEAT)){
            int quarters=0;
            for(Direction direction:Direction.values()){
                BlockPos pos=be.getBlockPos().relative(direction);if(!level.hasChunkAt(pos))continue;
                var state=level.getBlockState(pos);
                if(state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))quarters+=2;
                else if(state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.MAGMA_BLOCK)||state.getBlock() instanceof CampfireBlock&&state.getValue(CampfireBlock.LIT))quarters++;
            }
            long power=2700L*quarters/4;be.currentPower=power;
            long generated=(power+be.powerRemainder)/20;be.powerRemainder=(int)((power+be.powerRemainder)%20);
            be.status="热源效率 "+quarters*25+"% · "+EnergyDisplay.format(power)+" 魔能/秒";be.setChanged();return generated;
        }
        if(be.fuelRemaining<=0)try(var tx=Transaction.openRoot()){if(!loadFuel(be,tx)){extinguish(be);be.status="缺少燃料或必要流体";return 0;}tx.commit();}
        boolean nuclear=be.device()==DeviceType.NUCLEAR;
        long rate=nuclear?nuclearWaterRate(be):be.fuelFluidRate;
        int fluid=(int)((rate+be.fluidRemainder)/20);
        int availableFluid=IndustrialFluids.matches(be.fluids.getResource(0),be.fuelFluid)?be.fluids.getAmountAsInt(0):0;
        if(!nuclear&&fluid>availableFluid){extinguish(be);be.status="缺少必要流体，燃料已暂停";return 0;}
        boolean cooling=nuclear&&fluid>0&&be.coreHeat.positive()&&availableFluid>=fluid*2L;
        int usedFluid=nuclear?Math.min(cooling?fluid*2:fluid,availableFluid):fluid;
        int done=be.fuelTotal-be.fuelRemaining;long generated=EnergyMath.ceilProduct(be.fuelEnergy,done+1,be.fuelTotal)-EnergyMath.ceilProduct(be.fuelEnergy,done,be.fuelTotal);
        try(var tx=Transaction.openRoot()){
            be.recordGeneration(tx);
            if(usedFluid>0&&be.fluids.extract(0,be.fluids.getResource(0),usedFluid,tx)!=usedFluid)return 0;
            if(be.fuelRemaining==1)for(var output:be.fuelResults)if(be.lane(0).insertOutput(output.copy(),tx)!=output.getCount()){be.status="燃料产物空间不足，已暂停";return 0;}
            if(nuclear){
                if(cooling)be.coreHeat=be.coreHeat.subtract(Fraction.of(1).divide(rules.nuclearCoolingTicks));
                else if(fluid>usedFluid)be.coreHeat=be.coreHeat.add(Fraction.of(fluid-usedFluid).divide(fluid).divide(rules.nuclearHeatingTicks));
                if(!be.coreHeat.positive())be.coreHeat=Fraction.ZERO;
                be.coreHeat=be.coreHeat.min(Fraction.of(1));
                // Bound the temperature denominator when only part of the required water arrives.
                be.coreHeat=Fraction.of(be.coreHeat.multiply(HEAT_PRECISION).ceil()).divide(HEAT_PRECISION);
                Fraction exact=Fraction.of(generated).multiply(heatMultiplier(be)).add(be.heatEnergyRemainder);
                generated=exact.numerator().divide(exact.denominator()).longValueExact();be.heatEnergyRemainder=exact.subtract(Fraction.of(generated));
            }
            be.fluidRemainder=(int)((rate+be.fluidRemainder)%20);be.fuelRemaining--;if(be.fuelRemaining==0)be.fuelResults=new ArrayList<>();tx.commit();
        }
        if(nuclear&&be.coreHeat.compareTo(Fraction.of(1))>=0){
            be.setChanged();overheat(be);return 0;
        }
        be.currentPower=(long)(be.fuelEnergy*20.0/be.fuelTotal*(nuclear?heatMultiplier(be).value():1));be.status=nuclear?String.format(Locale.ROOT,"核心温度 %.2f%% · 产能×%.2f · %s",be.coreHeat.value()*100,heatMultiplier(be).value(),cooling?"降温":usedFluid<fluid?"缺水升温":"保温"):"发能中";be.setChanged();return generated;
    }
    public static Fraction heatMultiplier(MachineBlockEntity be){return Fraction.of(1).add(be.coreHeat.multiply(be.coreHeat).multiply(be.coreHeat).multiply(be.coreHeat).multiply(4));}
    public static long nuclearWaterRate(MachineBlockEntity be){long base=be.fuelRemaining>0?be.fuelFluidRate:IndustrialRules.current().generators.get(DeviceType.NUCLEAR.id).fluidPerSecond();return Fraction.of(base).multiply(heatMultiplier(be)).ceil();}
    private static void overheat(MachineBlockEntity be){
        be.status="核心温度100% · 即将爆炸";
        ServerLevel level=(ServerLevel)be.getLevel();BlockPos pos=be.getBlockPos();
        IndustrialWorld.get(level).defer(()->{if(be.isRemoved()||level.getBlockEntity(pos)!=be)return;level.removeBlock(pos,false);level.explode(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,(float)IndustrialRules.current().nuclearExplosionStrength,net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);});
    }
    public static final long IGNITION_ENERGY=100000000;
    private static void extinguish(MachineBlockEntity be){if(be.device().has(DeviceType.Capability.IGNITION)&&be.ignited){be.ignited=false;be.ignitionPaid=0;be.setChanged();}}
    private static boolean loadFuel(MachineBlockEntity be,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
        var level=(ServerLevel)be.getLevel();
        var fuels=level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals("fuel_"+be.device().id)).sorted(Comparator.comparing(h->h.id().identifier().toString())).toList();
        for(var holder:fuels){var fuel=holder.value();int[] take=fuel.takePlan(List.of(be.getItem(be.slots().fuel())));if(take==null)continue;
            var cooling=IndustrialRules.current().generators.get(be.device().id);
            if(be.device()!=DeviceType.NUCLEAR&&cooling.fluidPerSecond()>0&&(!IndustrialFluids.matches(be.fluids.getResource(0),cooling.fluid())||be.fluids.getAmountAsLong(0)<(cooling.fluidPerSecond()+be.fluidRemainder)/20))return false;
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
            }else be.status="点火充能中";
            tx.commit();
        }be.ignitionThisTick=be.ignited?0:accepted;be.consumed+=accepted;return accepted;
    }
}
