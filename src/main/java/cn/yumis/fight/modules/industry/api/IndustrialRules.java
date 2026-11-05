package cn.yumis.fight.modules.industry.api;
import cn.yumis.fight.modules.economy.api.CurrencyRules;

import java.util.*;

/** Reloadable production conditions. Crafting/processing ingredients live in recipes. */
public final class IndustrialRules {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public static final int VERSION=3;
    private static final IndustrialRules DEFAULT=defaults();
    private static volatile java.util.function.Supplier<IndustrialRules> source;
    public static IndustrialRules current(){var read=source;return read==null?DEFAULT:read.get();}
    public static void source(java.util.function.Supplier<IndustrialRules> value){source=java.util.Objects.requireNonNull(value);}
    public int version=VERSION;
    public long beaconCapacity=1000;
    public int[] beaconRadii={3,5,9,18,32};
    public int[] furnaceSpeeds={4,8,16,32,64};
    public int[] processingLines={1,1,2,8,16};
    public int windBaseHeight=60,windBasePower=50,windHeightStep=5,windPowerStep=1;
    public int solarDayPower=2,solarEdgePower=1,solarRainPower=1;
    public int[] solarPeriods={0,2000,10000,12000};
    public int[] transferIntervals={20,10,1},transferAmounts={8,64,64},transferRanges={24,128,Integer.MAX_VALUE};
    public long[] transferEnergy={5,25,100};
    public int[] harvestSizes={3,9,27};
    public int harvestPeriod=500;
    public long harvestEnergy=20;
    public List<String> harvestCrops=new ArrayList<>(List.of("minecraft:wheat","minecraft:carrots","minecraft:potatoes","minecraft:beetroots","minecraft:nether_wart"));
    public int fluidCapacity=20000;
    public int[] tankCapacities={64000,256000,1024000};
    public long[] accumulatorCapacities={100000,1000000,100000000,1000000000};
    public int scanTicks=40,scanCooldown=100,miningWaterRate=100,miningCoolantRate=40;
    public int pumpAmount=1000,pumpInterval=20;
    public long pumpEnergy=10;
    public int[] fluidTransferAmounts={1000,4000,16000};
    public long[] fluidTransferOperationEnergy={5,25,100};
    public Map<String,MineralCatalog.Mineral> minerals=MineralCatalog.defaults();
    public int magicGrassChance=16;
    public int crystalDropMin=2,crystalDropMax=8,highCrystalWeight=20;
    public boolean leadWorldgen=true,uraniumWorldgen=true,titaniumWorldgen=true,magicWorldgen=true;
    public Map<String,Generator> generators=new LinkedHashMap<>();
    public Map<String,Ore> ores=new LinkedHashMap<>();
    public Map<String,Double> currencyRarityWeights=new LinkedHashMap<>();
    public Map<String,Double> currencyItemWeights=new LinkedHashMap<>();
    private transient double[] currencyPool;
    public double[] currencyPool(){if(currencyPool==null)currencyPool=cn.yumis.fight.modules.economy.api.CurrencyRules.factoryProbabilities(currencyRarityWeights,currencyItemWeights);return currencyPool;}
    public IndustrialRules(){for(var t:cn.yumis.fight.modules.economy.api.CurrencyRules.DROP_TIERS)currencyRarityWeights.put(t.rarity().name(),t.baseWeight());for(var c:cn.yumis.fight.modules.economy.api.CurrencyRules.DROP_POOL)currencyItemWeights.put(c.id(),1d);}
    public record Generator(int fluidPerSecond,String fluid){}
    public record Ore(int minY,int maxY,int minSize,int maxSize,int chance,int attempts){}
    public static IndustrialRules defaults(){
        var r=new IndustrialRules();
        r.generators.put("coal_generator",new Generator(0,""));
        r.generators.put("turbine_generator",new Generator(1000,"minecraft:water"));
        r.generators.put("thermal_generator",new Generator(100,"yumis_dungeon_and_fight:coolant"));
        r.generators.put("nuclear_reactor",new Generator(100000,"minecraft:water"));
        r.generators.put("artificial_star",new Generator(100000,"yumis_dungeon_and_fight:coolant"));
        r.generators.put("rtg_generator",new Generator(0,""));
        r.ores.put("magic",new Ore(-32,32,3,6,1,1));r.ores.put("lead",new Ore(-32,48,4,8,1,1));r.ores.put("uranium",new Ore(-64,0,2,4,4,1));r.ores.put("titanium",new Ore(0,80,3,6,1,1));
        r.ores.put("titanium_overworld",new Ore(-48,16,3,6,2,1));
        r.ores.put("orichalcum",new Ore(-16,64,4,8,1,1));r.ores.put("vanadium",new Ore(-32,32,3,6,2,1));
        r.ores.put("molybdenum",new Ore(-48,16,3,5,3,1));r.ores.put("zirconium",new Ore(-32,32,3,6,3,1));
        r.ores.put("mithril",new Ore(-48,0,2,5,4,1));r.ores.put("niobium_tantalum",new Ore(-64,-16,2,4,5,1));
        r.ores.put("adamantite",new Ore(-64,-32,2,4,8,1));r.ores.put("iridium",new Ore(-64,-40,1,3,16,1));
        return r;
    }
    public static IndustrialRules parse(String json){
        IndustrialRules r=JSON.fromJson(json,IndustrialRules.class);
        if(r==null||r.version!=VERSION||r.beaconCapacity<1||r.beaconRadii.length!=5||r.furnaceSpeeds.length!=5||r.solarPeriods.length!=4||r.transferIntervals.length!=3||r.transferAmounts.length!=3||r.transferRanges.length!=3||r.transferEnergy.length!=3||r.harvestSizes.length!=3||r.harvestPeriod<1||r.harvestEnergy<0||r.fluidCapacity<1000||r.magicGrassChance<1||r.crystalDropMin<1||r.crystalDropMax<r.crystalDropMin||r.highCrystalWeight<0||r.highCrystalWeight>100||r.windHeightStep<1||r.windBasePower<0||r.windPowerStep<0||r.solarDayPower<0||r.solarEdgePower<0||r.solarRainPower<0)throw new IllegalArgumentException("无效工业规则");
        for(int v:r.beaconRadii)positive(v);for(int v:r.furnaceSpeeds){positive(v);if(v>128||128%v!=0)throw new IllegalArgumentException("熔炉速度必须整除128");}for(int v:r.transferIntervals)positive(v);for(int v:r.transferAmounts)positive(v);for(int v:r.harvestSizes)positive(v);
        for(long v:r.transferEnergy)if(v<0)throw new IllegalArgumentException("负搬运费用");
        if(r.processingLines.length!=5||r.tankCapacities.length!=3||r.accumulatorCapacities.length!=4||r.fluidTransferAmounts.length!=3||r.fluidTransferOperationEnergy.length!=3||r.scanTicks<1||r.scanCooldown<1||r.miningWaterRate<1||r.miningCoolantRate<1||r.pumpInterval<1||r.pumpAmount<1||r.pumpEnergy<0)throw new IllegalArgumentException("无效新增工业规则");
        int previous=1;for(int v:r.processingLines){if(v<previous||v>MachineSlots.MAXIMUM_LINES)throw new IllegalArgumentException("无效加工产线数");previous=v;}
        for(int v:r.tankCapacities)positive(v);for(long v:r.accumulatorCapacities)if(v<1)throw new IllegalArgumentException("无效蓄能容量");
        for(int v:r.fluidTransferAmounts)positive(v);for(long v:r.fluidTransferOperationEnergy)if(v<0)throw new IllegalArgumentException("负供液费用");
        for(String id:MineralCatalog.defaults().keySet()){var m=r.minerals.get(id);if(m==null||m.tier()<1||m.tier()>5||m.chance()<1||m.chance()>100||m.ticks()<1||m.energy()<1||m.count()<1)throw new IllegalArgumentException("无效矿藏规则 "+id);}
        for(var entry:defaults().generators.keySet()){
            Generator g=r.generators.get(entry);if(g==null||g.fluidPerSecond<0)throw new IllegalArgumentException("无效发电规则 "+entry);
        }
        for(String key:defaults().ores.keySet()){Ore o=r.ores.get(key);if(o==null||o.minY>o.maxY||o.minSize<1||o.maxSize<o.minSize||o.chance<1||o.attempts<1)throw new IllegalArgumentException("无效矿脉规则 "+key);}
        r.currencyPool=cn.yumis.fight.modules.economy.api.CurrencyRules.factoryProbabilities(r.currencyRarityWeights,r.currencyItemWeights);
        return r;
    }
    private static void positive(int i){if(i<1)throw new IllegalArgumentException("工业规则必须为正数");}
}
