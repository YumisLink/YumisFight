package cn.yumis.fight.modules.industry.api;

import java.util.*;

/** Stable device IDs composed from capabilities. Registration occurs before block registration. */
public final class DeviceType {
    public enum Capability {PROCESSING,PARALLEL,GENERATION,GENERATION_OUTPUT,IGNITION,LOGISTICS,FLUID_LOGISTICS,HARVEST,STORAGE_NETWORK,ENERGY_STORAGE,FLUID_STORAGE,PUMP,MINING,DUAL_FLUID,GRID_ONLY,STRUCTURE,SELECT_RECIPE,REQUIRE_RECIPE,EXACT_RECIPE_TIER,SMELTING,FUEL,WIND,SOLAR,FLUID_PROCESSING,FLUID_FUEL,ROTOR,CURRENCY_UPGRADE,FLUID_INPUT_ALL_SIDES,INFUSION,ENVIRONMENTAL_HEAT}
    public enum Speed {TIER_EXPONENTIAL,FURNACE_TABLE,MINING_LINEAR,FIXED}
    public enum Output {RECIPE,CURRENCY}
    private static final Map<String,DeviceType> TYPES=new LinkedHashMap<>();
    public final String id;
    public final boolean graded;
    private final int inputs,minimumTier,maximumTier;
    private final Set<Capability> capabilities;
    private final Speed speed;
    private final Output output;
    public static final DeviceType CRUSHER=processor("mana_crusher",3,Speed.TIER_EXPONENTIAL,Output.RECIPE,Capability.PARALLEL),
        FURNACE=processor("mana_furnace",3,Speed.FURNACE_TABLE,Output.RECIPE,Capability.SMELTING,Capability.PARALLEL),
        MINER=processor("mineral_generator",0,Speed.MINING_LINEAR,Output.RECIPE,Capability.SELECT_RECIPE,Capability.REQUIRE_RECIPE,Capability.MINING,Capability.FLUID_PROCESSING),
        CURRENCY=processor("currency_fabricator",0,Speed.TIER_EXPONENTIAL,Output.CURRENCY,Capability.EXACT_RECIPE_TIER,Capability.PARALLEL),
        FORGE=processor("mana_forge",4,Speed.TIER_EXPONENTIAL,Output.RECIPE,Capability.STRUCTURE,Capability.FLUID_PROCESSING,Capability.PARALLEL,Capability.SELECT_RECIPE),
        BEACON=register("mana_beacon",true,0,1,5,Set.of(Capability.STORAGE_NETWORK),Speed.FIXED,Output.RECIPE),
        COAL=generator("coal_generator",1,Capability.FUEL),SOLAR=generator("solar_collector",1,Capability.SOLAR),
        TURBINE=generator("turbine_generator",2,Capability.FUEL,Capability.FLUID_FUEL,Capability.ROTOR,Capability.FLUID_INPUT_ALL_SIDES),WIND=generator("wind_generator",2,Capability.WIND),
        THERMAL=generator("thermal_generator",3,Capability.FUEL,Capability.FLUID_FUEL),NUCLEAR=generator("nuclear_reactor",4,Capability.FUEL,Capability.FLUID_FUEL,Capability.GENERATION_OUTPUT,Capability.FLUID_INPUT_ALL_SIDES),RTG=generator("rtg_generator",5,Capability.FUEL),
        HEAT=generator("heat_generator",2,Capability.ENVIRONMENTAL_HEAT),
        CONCENTRATOR=register("mana_concentrator",false,0,2,2,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING),Speed.FIXED,Output.RECIPE),
        INFUSER=register("fluid_infuser",false,0,2,2,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING,Capability.INFUSION),Speed.FIXED,Output.RECIPE),
        MIXER=register("coolant_mixer",false,1,2,2,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING),Speed.FIXED,Output.RECIPE),
        TRANSFER=register("transfer_crystal",false,0,1,3,Set.of(Capability.LOGISTICS),Speed.FIXED,Output.RECIPE),
        HARVEST=register("harvest_crystal",false,0,1,3,Set.of(Capability.HARVEST),Speed.FIXED,Output.RECIPE),
        CURRENCY_UPGRADER=register("currency_upgrader",false,9,1,1,Set.of(Capability.CURRENCY_UPGRADE),Speed.FIXED,Output.RECIPE),
        REFINERY=register("mineral_refinery",false,4,2,2,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING,Capability.GRID_ONLY,Capability.SELECT_RECIPE),Speed.FIXED,Output.RECIPE),
        REACTOR=register("alchemical_reactor",false,4,3,3,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING,Capability.DUAL_FLUID,Capability.GRID_ONLY,Capability.SELECT_RECIPE),Speed.FIXED,Output.RECIPE),
        PUMP=register("water_pump",false,0,1,1,Set.of(Capability.PUMP,Capability.FLUID_PROCESSING,Capability.GRID_ONLY),Speed.FIXED,Output.RECIPE),
        FLUID_TRANSFER=register("fluid_crystal",false,0,1,3,Set.of(Capability.FLUID_LOGISTICS,Capability.GRID_ONLY),Speed.FIXED,Output.RECIPE),
        TANK=register("fluid_tank",true,0,1,3,Set.of(Capability.FLUID_STORAGE),Speed.FIXED,Output.RECIPE),
        ACCUMULATOR=register("mana_accumulator",true,0,1,4,Set.of(Capability.ENERGY_STORAGE),Speed.FIXED,Output.RECIPE),
        SEPARATOR=register("isotopic_separator",false,4,4,4,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING,Capability.GRID_ONLY,Capability.SELECT_RECIPE),Speed.FIXED,Output.RECIPE),
        ACTIVATOR=register("isotope_activator",false,4,5,5,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING,Capability.GRID_ONLY,Capability.SELECT_RECIPE),Speed.FIXED,Output.RECIPE),
        ASSEMBLER=register("forming_assembler",false,4,3,3,Set.of(Capability.PROCESSING,Capability.GRID_ONLY,Capability.SELECT_RECIPE),Speed.FIXED,Output.RECIPE),
        DISTILLER=register("thermal_distiller",false,4,3,3,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING,Capability.GRID_ONLY,Capability.SELECT_RECIPE),Speed.FIXED,Output.RECIPE),
        STAR=register("artificial_star",false,1,5,5,Set.of(Capability.GENERATION,Capability.FUEL,Capability.FLUID_FUEL,Capability.FLUID_INPUT_ALL_SIDES,Capability.GENERATION_OUTPUT,Capability.IGNITION,Capability.STRUCTURE),Speed.FIXED,Output.RECIPE);
    private DeviceType(String id,boolean graded,int inputs,int minimumTier,int maximumTier,Set<Capability> capabilities,Speed speed,Output output){this.id=id;this.graded=graded;this.inputs=inputs;this.minimumTier=minimumTier;this.maximumTier=maximumTier;this.capabilities=Set.copyOf(capabilities);this.speed=speed;this.output=output;}
    public static synchronized DeviceType register(String id,boolean graded,int inputs,int minimumTier,int maximumTier,Set<Capability> capabilities,Speed speed,Output output){
        if(id==null||!id.matches("[a-z][a-z0-9_]*")||inputs<0||inputs>9||minimumTier<1||maximumTier>5||minimumTier>maximumTier)throw new IllegalArgumentException("Invalid device: "+id);
        var value=new DeviceType(id,graded,inputs,minimumTier,maximumTier,capabilities,Objects.requireNonNull(speed),Objects.requireNonNull(output));
        if(TYPES.putIfAbsent(id,value)!=null)throw new IllegalArgumentException("Duplicate device: "+id);return value;
    }
    private static DeviceType processor(String id,int inputs,Speed speed,Output output,Capability... additional){var set=EnumSet.of(Capability.PROCESSING);Collections.addAll(set,additional);return register(id,true,inputs,1,5,set,speed,output);}
    private static DeviceType generator(String id,int tier,Capability... modes){var capabilities=EnumSet.of(Capability.GENERATION);Collections.addAll(capabilities,modes);return register(id,false,0,tier,tier,capabilities,Speed.FIXED,Output.RECIPE);}
    public static synchronized List<DeviceType> values(){return List.copyOf(TYPES.values());}
    public static DeviceType require(String id){var value=TYPES.get(id);if(value==null)throw new IllegalArgumentException("Unknown device: "+id);return value;}
    public boolean has(Capability capability){return capabilities.contains(capability);}
    public Set<Capability> capabilities(){return capabilities;}
    public boolean generator(){return has(Capability.GENERATION);}
    public boolean functional(){return has(Capability.LOGISTICS)||has(Capability.FLUID_LOGISTICS)||has(Capability.HARVEST)||has(Capability.PUMP);}
    public boolean processing(){return has(Capability.PROCESSING);}
    public boolean currencyUpgrader(){return has(Capability.CURRENCY_UPGRADE);}
    public boolean outputs(){return processing()||currencyUpgrader()||has(Capability.GENERATION_OUTPUT);}
    public int inputs(){return inputs;}
    public int minimumTier(){return minimumTier;}
    public int maximumTier(){return maximumTier;}
    public boolean selectedRecipe(){return has(Capability.SELECT_RECIPE);}
    public boolean exactRecipeTier(){return has(Capability.EXACT_RECIPE_TIER);}
    public boolean needsStructure(){return has(Capability.STRUCTURE);}
    public boolean randomCurrency(){return output==Output.CURRENCY;}
    public int fluidInputs(){return has(Capability.DUAL_FLUID)?2:1;}
    public int fluidOutput(){return has(Capability.FLUID_STORAGE)?0:fluidInputs();}
    public int fluidTanks(){return has(Capability.FLUID_STORAGE)?1:fluidInputs()+1;}
    public int lineCount(int tier){return has(Capability.PARALLEL)?IndustrialRules.current().processingLines[tier-1]:1;}
    public MachineSlots slots(int tier){return currencyUpgrader()?MachineSlots.currencyUpgrade():MachineSlots.ordinary(inputs,lineCount(tier),has(Capability.PARALLEL));}
    public int maximumLines(){return has(Capability.PARALLEL)?MachineSlots.MAXIMUM_LINES:1;}
    public int speed(int machineTier,int[] furnaceSpeeds){return switch(speed){case FIXED->1;case MINING_LINEAR->machineTier;case FURNACE_TABLE->furnaceSpeeds[machineTier-1];case TIER_EXPONENTIAL->1<<(machineTier-1);};}
}
