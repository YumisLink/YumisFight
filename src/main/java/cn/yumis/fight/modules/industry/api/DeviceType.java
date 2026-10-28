package cn.yumis.fight.modules.industry.api;

import java.util.*;

/** Stable device IDs composed from capabilities. Registration occurs before block registration. */
public final class DeviceType {
    public enum Capability {PROCESSING,GENERATION,LOGISTICS,HARVEST,STORAGE_NETWORK,STRUCTURE,SELECT_RECIPE,EXACT_RECIPE_TIER,SMELTING,FUEL,WIND,SOLAR,FLUID_PROCESSING,FLUID_FUEL,ROTOR}
    public enum Speed {TIER_EXPONENTIAL,FURNACE_TABLE,FIXED}
    public enum Output {RECIPE,CURRENCY}
    private static final Map<String,DeviceType> TYPES=new LinkedHashMap<>();
    public final String id;
    public final boolean graded;
    private final int inputs,minimumTier,maximumTier;
    private final Set<Capability> capabilities;
    private final Speed speed;
    private final Output output;
    public static final DeviceType CRUSHER=processor("mana_crusher",3,Speed.TIER_EXPONENTIAL,Output.RECIPE),
        FURNACE=processor("mana_furnace",3,Speed.FURNACE_TABLE,Output.RECIPE,Capability.SMELTING),
        MINER=processor("mineral_generator",0,Speed.TIER_EXPONENTIAL,Output.RECIPE,Capability.SELECT_RECIPE),
        CURRENCY=processor("currency_fabricator",0,Speed.FIXED,Output.CURRENCY,Capability.EXACT_RECIPE_TIER),
        FORGE=processor("mana_forge",4,Speed.TIER_EXPONENTIAL,Output.RECIPE,Capability.STRUCTURE),
        BEACON=register("mana_beacon",true,0,1,5,Set.of(Capability.STORAGE_NETWORK,Capability.STRUCTURE),Speed.FIXED,Output.RECIPE),
        COAL=generator("coal_generator",1,Capability.FUEL),SOLAR=generator("solar_collector",1,Capability.SOLAR),
        TURBINE=generator("turbine_generator",2,Capability.FUEL,Capability.FLUID_FUEL,Capability.ROTOR),WIND=generator("wind_generator",2,Capability.WIND),
        THERMAL=generator("thermal_generator",3,Capability.FUEL,Capability.FLUID_FUEL),NUCLEAR=generator("nuclear_reactor",4,Capability.FUEL,Capability.FLUID_FUEL),RTG=generator("rtg_generator",5,Capability.FUEL),
        MIXER=register("coolant_mixer",false,1,2,2,Set.of(Capability.PROCESSING,Capability.FLUID_PROCESSING),Speed.FIXED,Output.RECIPE),
        TRANSFER=register("transfer_crystal",false,0,1,3,Set.of(Capability.LOGISTICS),Speed.FIXED,Output.RECIPE),
        HARVEST=register("harvest_crystal",false,0,1,3,Set.of(Capability.HARVEST),Speed.FIXED,Output.RECIPE);
    private DeviceType(String id,boolean graded,int inputs,int minimumTier,int maximumTier,Set<Capability> capabilities,Speed speed,Output output){this.id=id;this.graded=graded;this.inputs=inputs;this.minimumTier=minimumTier;this.maximumTier=maximumTier;this.capabilities=Set.copyOf(capabilities);this.speed=speed;this.output=output;}
    public static synchronized DeviceType register(String id,boolean graded,int inputs,int minimumTier,int maximumTier,Set<Capability> capabilities,Speed speed,Output output){
        if(id==null||!id.matches("[a-z][a-z0-9_]*")||inputs<0||inputs>4||minimumTier<1||maximumTier>5||minimumTier>maximumTier)throw new IllegalArgumentException("Invalid device: "+id);
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
    public boolean functional(){return has(Capability.LOGISTICS)||has(Capability.HARVEST);}
    public boolean processing(){return has(Capability.PROCESSING);}
    public int inputs(){return inputs;}
    public int minimumTier(){return minimumTier;}
    public int maximumTier(){return maximumTier;}
    public boolean selectedRecipe(){return has(Capability.SELECT_RECIPE);}
    public boolean exactRecipeTier(){return has(Capability.EXACT_RECIPE_TIER);}
    public boolean needsStructure(){return has(Capability.STRUCTURE);}
    public boolean randomCurrency(){return output==Output.CURRENCY;}
    public int speed(int machineTier,int recipeTier,int[] furnaceSpeeds){return switch(speed){case FIXED->1;case FURNACE_TABLE->furnaceSpeeds[machineTier-1];case TIER_EXPONENTIAL->1<<(machineTier-recipeTier);};}
}
