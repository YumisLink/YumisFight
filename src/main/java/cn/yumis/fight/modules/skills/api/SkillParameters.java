package cn.yumis.fight.modules.skills.api;

import java.util.*;
import cn.yumis.fight.modules.attributes.api.ParameterModifier;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
/** Resolved once at cast start; equipment integrations can supply additive and multiplicative changes. */
public final class SkillParameters {
    @FunctionalInterface public interface Variant {Snapshot apply(Snapshot input,cn.yumis.fight.modules.character.api.Progress progress,cn.yumis.fight.modules.attributes.api.Stats stats);}
    private static final Map<SkillDefinition,Variant> VARIANTS=new LinkedHashMap<>();
    public static synchronized void registerVariant(SkillDefinition skill,Variant variant){if(VARIANTS.putIfAbsent(skill,Objects.requireNonNull(variant))!=null)throw new IllegalArgumentException("Duplicate skill variant: "+skill);}
    public static Snapshot applyVariant(Snapshot input,cn.yumis.fight.modules.character.api.Progress progress,cn.yumis.fight.modules.attributes.api.Stats stats){
        var processor=VARIANTS.get(input.skill());
        return processor==null?input:processor.apply(input,progress,stats);
    }
    private static final Map<String,java.util.function.BiFunction<UUID,SkillDefinition,Collection<ParameterModifier>>> SOURCES=new LinkedHashMap<>();
    public static synchronized void register(String id,java.util.function.BiFunction<UUID,SkillDefinition,Collection<ParameterModifier>> provider){
        SOURCES.put(id,Objects.requireNonNull(provider));
    }
    public static synchronized void remove(String id){
        SOURCES.remove(id);
    }
    public static synchronized Snapshot forCast(UUID player,SkillDefinition skill,int rank,Collection<ParameterModifier> equipment){
        List<ParameterModifier> all=new ArrayList<>(equipment);
        for(var provider:SOURCES.values())all.addAll(provider.apply(player,skill));
        return resolve(skill,rank,all);
    }

    public record Snapshot(SkillDefinition skill,int rank,Map<String,Double> values){
        public Snapshot{
            values=Map.copyOf(values);
        }
        public double get(String key,double fallback){
            return values.getOrDefault(key,fallback);
        }
    }
    private SkillParameters(){
    }
    /** Form keys whose equipment changes must never disable a skill; values are hard floors applied after modifier resolution. */
    private static final Map<String,Double> COUNT_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("slashes",1d),java.util.Map.entry("auraPulses",1d),java.util.Map.entry("pierces",1d),
        java.util.Map.entry("orbBaseHits",1d),java.util.Map.entry("brandCap",1d),
        java.util.Map.entry("bloodCap",1d),java.util.Map.entry("swordRushes",1d),java.util.Map.entry("stoneVolley",1d),
        java.util.Map.entry("heavyEvery",1d),java.util.Map.entry("bombEvery",1d),java.util.Map.entry("replicaSteps",1d),
        java.util.Map.entry("stoneUnlockRank",1d),java.util.Map.entry("sunMoonStackCap",1d),
        java.util.Map.entry("orbCount",1d),java.util.Map.entry("pillarCount",1d),java.util.Map.entry("meteorCount",1d),
        java.util.Map.entry("wallSegments",4d),java.util.Map.entry("maxCharges",1d),
        java.util.Map.entry("maxChasers",1d),java.util.Map.entry("chaserGen",1d),java.util.Map.entry("consumeCount",1d),
        java.util.Map.entry("dragonChaserCount",1d),java.util.Map.entry("comboStageCap",1d),java.util.Map.entry("dashCount",1d),
        java.util.Map.entry("chaserEveryHits",1d),java.util.Map.entry("summonBaseCount",1d),
        java.util.Map.entry("geyserCount",1d),java.util.Map.entry("boltCount",1d),java.util.Map.entry("witchBounceCount",1d)));
    private static final Map<String,Double> TIME_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("interval",.05d),java.util.Map.entry("duration",.05d),java.util.Map.entry("chargeSeconds",.05d),
        java.util.Map.entry("dashSeconds",.05d),java.util.Map.entry("hopSeconds",.05d),
        java.util.Map.entry("fieldTickInterval",.05d),java.util.Map.entry("orbTickInterval",.05d),
        java.util.Map.entry("crescentChargeSeconds",.05d),java.util.Map.entry("preludeStepSeconds",.05d),java.util.Map.entry("comboStepSeconds",.05d),
        java.util.Map.entry("drawSeconds",.05d),java.util.Map.entry("stormInterval",.05d),java.util.Map.entry("stormDeploy",.05d),
        java.util.Map.entry("finishDelay",.05d),java.util.Map.entry("brandInterval",.05d),java.util.Map.entry("maxChargeSeconds",1d),java.util.Map.entry("transformSeconds",1d),
        java.util.Map.entry("fallDelay",.05d),java.util.Map.entry("impactDelay",.05d),java.util.Map.entry("emberInterval",.05d),
        java.util.Map.entry("emberDuration",.2d),java.util.Map.entry("lampDuration",1d),java.util.Map.entry("maxHoldSeconds",.2d),
        java.util.Map.entry("chaserDuration",1d),java.util.Map.entry("buffDuration",1d),java.util.Map.entry("autoChaserInterval",.25d),
        java.util.Map.entry("lockWindowSeconds",.2d),java.util.Map.entry("cancelWindowSeconds",.1d),java.util.Map.entry("comboHoldSeconds",1d),
        java.util.Map.entry("fusionBuffDuration",1d),java.util.Map.entry("holdSeconds",.2d),java.util.Map.entry("finisherDuration",.2d),
        java.util.Map.entry("minChannelSeconds",.1d),java.util.Map.entry("summonLifetime",.4d),java.util.Map.entry("boltInterval",.2d)));
    private static final Map<String,Double> DISTANCE_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("range",.5d),java.util.Map.entry("radius",.5d),java.util.Map.entry("width",.5d),
        java.util.Map.entry("dashDistance",.5d),java.util.Map.entry("hopDistance",.5d),java.util.Map.entry("finisherRange",.5d),
        java.util.Map.entry("beamWidth",.5d),java.util.Map.entry("danceRadius",.5d),java.util.Map.entry("auraRadius",.5d),
        java.util.Map.entry("fieldRadius",.5d),java.util.Map.entry("blastRadius",.5d),java.util.Map.entry("boltRange",.5d),
        java.util.Map.entry("gearRange",.5d),
        java.util.Map.entry("pullRadius",.5d),java.util.Map.entry("shockwaveRadius",.5d),java.util.Map.entry("meteorRadius",.5d),
        java.util.Map.entry("finisherRadius",.5d),java.util.Map.entry("wallHeight",.5d),
        java.util.Map.entry("knockbackDistance",.5d),java.util.Map.entry("centerOffset",.5d)));
    private static final Map<String,Double> SPEED_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("projectileSpeed",1d),java.util.Map.entry("fireSpeed",1d),java.util.Map.entry("gearSpeed",1d),
        java.util.Map.entry("huntSpeed",1d),java.util.Map.entry("chargeMultiplier",1d),java.util.Map.entry("cursorSpeed",1d)));
    private static final Set<String> OPTIONAL_ZERO_FLOORS=Set.of("chargeSeconds","pullRadius","emberDuration","range","duration","holdSeconds","knockbackDistance","transformSeconds");
    private static final Set<String> MORPH_ZEROABLE=Set.of("chargeSeconds","holdSeconds","knockbackDistance","transformSeconds");
    private static final Set<String> RATIO_KEYS=Set.of("swordFirstShare","finisherShare","elementSplit","beamDamageTaken",
        "minChargeRatio","earlyDetonateKeepRatio","homingBias","slowRatio","channelProtection",
        "iceDamageReduction","autoChaserChance","basicChaserChance","chargeMoveSpeedRatio","teanaShieldRatio","bitterKeepRatio","witchSoloEchoRatio");
    public static Snapshot resolve(SkillDefinition skill,int rank,Collection<ParameterModifier> changes){
        var rules=SkillBalanceRules.current();
        Map<String,Double> p=rules.parameters(skill,rank);
        Map<String,Double> rawBase=new LinkedHashMap<>(p);
        double damageScale=Math.pow(rules.rankDamageGrowth(skill),Math.max(0,rank-1))*rules.globalSkillDamageMultiplier;
        int morph=ElementalistMorphs.resolveMorphMode(skill.id,changes);
        Collection<ParameterModifier> effective=changes;
        if(morph>0){
            var bundle=ElementalistMorphs.modifierBundle(skill.id,morph);
            if(bundle.isEmpty())bundle=BattleMageMorphs.modifierBundle(skill.id,morph);
            if(!bundle.isEmpty()){
                var combined=new ArrayList<ParameterModifier>(changes.size()+bundle.size());
                combined.addAll(changes);
                combined.addAll(bundle);
                effective=combined;
            }
        }
        Map<String,Double> additions=new HashMap<>(),products=new HashMap<>();
        Set<String> seen=new HashSet<>();
        for(ParameterModifier c:effective)if(c.skill().equals(skill.id)){
            if(!seen.add(c.source()+"/"+c.parameter()))throw new IllegalArgumentException("Duplicate skill source");
            additions.merge(c.parameter(),c.additive(),Double::sum);
            products.merge(c.parameter(),c.multiplier(),(a,b)->a*b);
        }
        var damageKeys=skill.damageParameters();
        for(String k:new HashSet<>(p.keySet())){
            double add=additions.getOrDefault(k,0d);
            if(damageKeys.contains(k)&&add!=0)add*=damageScale;
            p.put(k,Math.max(0,(p.get(k)+add)*products.getOrDefault(k,1d)));
        }
        if(additions.containsKey("cooldownAdd")||products.containsKey("cooldownAdd"))
            p.put("cooldownAdd",additions.getOrDefault("cooldownAdd",0d)*products.getOrDefault("cooldownAdd",1d));
        if(additions.containsKey("cooldownMultiplier")||products.containsKey("cooldownMultiplier"))
            p.put("cooldownMultiplier",Math.max(0,(1d+additions.getOrDefault("cooldownMultiplier",0d))*products.getOrDefault("cooldownMultiplier",1d)));
        if(additions.containsKey("mpMultiplier")||products.containsKey("mpMultiplier"))
            p.put("mpMultiplier",Math.max(0,(1d+additions.getOrDefault("mpMultiplier",0d))*products.getOrDefault("mpMultiplier",1d)));
        p.put("slashes",Math.max(1,Math.floor(p.get("slashes"))));
        for(var floor:COUNT_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->Math.max(floor.getValue(),Math.floor(v)));
        for(var floor:TIME_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->(OPTIONAL_ZERO_FLOORS.contains(k)&&rawBase.getOrDefault(k,0d)<=0||(morph>0||products.getOrDefault(k,1d)==0d)&&MORPH_ZEROABLE.contains(k))&&v<=0?0d:Math.max(floor.getValue(),v));
        for(var floor:DISTANCE_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->(OPTIONAL_ZERO_FLOORS.contains(k)&&rawBase.getOrDefault(k,0d)<=0||(morph>0||products.getOrDefault(k,1d)==0d)&&MORPH_ZEROABLE.contains(k))&&v<=0?0d:Math.max(floor.getValue(),v));
        for(var floor:SPEED_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->Math.max(floor.getValue(),v));
        for(String k:RATIO_KEYS)p.computeIfPresent(k,(key,v)->Math.clamp(v,0,1));
        if(skill.projectile!=null)p.computeIfPresent("range",(key,value)->ProjectileSpawn.extendedRange(value));
        if(skill.id.equals(CareerSkills.EYE))p.computeIfPresent("gearRange",(key,value)->ProjectileSpawn.extendedRange(value));
        if(skill.id.equals(CareerSkills.KALLA))p.computeIfPresent("boltRange",(key,value)->ProjectileSpawn.extendedRange(value));
        return new Snapshot(skill,rank,p);
    }
}
