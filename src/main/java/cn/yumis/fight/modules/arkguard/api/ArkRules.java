package cn.yumis.fight.modules.arkguard.api;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Ark-owned content. Combat continues to use the existing attribute and damage rules. */
public final class ArkRules {
    private static final Gson JSON=new Gson();
    private static Supplier<ArkRules> source=ArkRules::defaults;
    private static final class Defaults {static final ArkRules VALUE=load();}
    public int version,cardsPerUnit,shopSlots,refreshCost,maximumLevel,maximumTraitLayers;
    public List<Unit> units;
    public List<Trait> traits;
    public int[][] shopProbabilities;
    public int[] populations,upgradeCosts;
    public Enemies enemies;
    public record Unit(String id,String name,String role,double health,double physicalDefense,double magicalDefense,
                       int tier,int cost,double attack,double attackRate,double range,boolean magic,int[] elements,
                       List<String> traits,String skin,Map<String,Double> parameters,
                       Map<String,List<Double>> stars,List<String> description) {
        public double p(String key){return parameters.getOrDefault(key,0d);}
        public double s(String key,int star){var values=stars.get(key);return values==null?0:values.get(star-1);}
        public double starScale(int star){return switch(star){case 1->1;case 2->1.8;case 3->3.24;default->throw new IllegalArgumentException("星级无效");};}
        public boolean has(String trait){return traits.contains(trait);}
        public String describe(String template){
            var matcher=Pattern.compile("\\{([A-Za-z0-9]+)(?::(percent|stars|starsPercent))?}").matcher(template);
            return matcher.replaceAll(match->{
                String key=match.group(1),style=match.group(2);
                if(stars.containsKey(key))return String.join("／",stars.get(key).stream().map(v->number(v*("starsPercent".equals(style)?100:1))).toList())+("starsPercent".equals(style)?"%":"");
                return number(p(key)*("percent".equals(style)?100:1))+("percent".equals(style)?"%":"");
            });
        }
    }
    public record Trait(String id,String name,int[] thresholds,List<String> description) {}
    public static final class Enemies {
        public int goblinFirstCount,goblinLastCount,goblinGuards,dragonGuards;
        public double dragonCountRatio,goblinHealth,dragonHealth,healthGrowth,goblinAttack,dragonAttack,attackGrowth;
        public double goblinPhysicalDefense,dragonPhysicalDefense,magicalDefense,defensePerWave;
        public double[] goblinResists,dragonResists;
        public double bossHealth,bossHealthGrowth,bossAttack,bossAttackGrowth;
        public int count(int wave,boolean dragon){
            int goblins=(int)Math.round(goblinFirstCount+(goblinLastCount-goblinFirstCount)*(wave-1)/19d);
            return dragon?(int)Math.round(goblins*dragonCountRatio):goblins;
        }
        public double health(int wave,boolean dragon){return (dragon?dragonHealth:goblinHealth)*Math.pow(healthGrowth,wave-1);}
        public double attack(int wave,boolean dragon){return (dragon?dragonAttack:goblinAttack)*Math.pow(attackGrowth,wave-1);}
        public double physicalDefense(int wave,boolean dragon){return (dragon?dragonPhysicalDefense:goblinPhysicalDefense)*(1+defensePerWave*(wave-1));}
        public double magicalDefense(int wave){return magicalDefense*(1+defensePerWave*(wave-1));}
        public double bossHealth(int wave){return bossHealth*Math.pow(bossHealthGrowth,wave-5);}
        public double bossAttack(int wave){return bossAttack*Math.pow(bossAttackGrowth,wave-5);}
        public double[] resistances(boolean dragon){return (dragon?dragonResists:goblinResists).clone();}
    }
    public static ArkRules current(){return source.get();}
    public static void source(Supplier<ArkRules> supplier){source=Objects.requireNonNull(supplier);}
    public static ArkRules defaults(){return Defaults.VALUE;}
    public static ArkRules parse(String json){
        ArkRules r=JSON.fromJson(json,ArkRules.class);
        if(r==null||r.version!=1||r.units==null||r.traits==null||r.enemies==null||r.cardsPerUnit<9||r.shopSlots!=5
            ||r.refreshCost<0||r.maximumLevel!=7||r.maximumTraitLayers!=2000||r.shopProbabilities==null
            ||r.shopProbabilities.length!=7||r.populations==null||r.populations.length!=7||r.upgradeCosts==null||r.upgradeCosts.length!=6)
            throw new IllegalArgumentException("方舟内容定义无效");
        var ids=new HashSet<String>();var traitIds=new HashSet<String>();
        for(var t:r.traits)if(t==null||!traitIds.add(t.id())||t.name()==null||t.thresholds()==null||t.thresholds().length==0||t.description()==null)
            throw new IllegalArgumentException("方舟羁绊定义无效");
        for(var u:r.units){
            if(u==null||!u.id().matches("ark_[a-z0-9_]+")||!ids.add(u.id())||u.name()==null||u.health()<=0||u.attack()<0
                ||u.attackRate()<0||u.range()<=0||u.physicalDefense()<0||u.magicalDefense()<0||u.tier()<1||u.tier()>6
                ||u.cost()<1||u.traits()==null||!traitIds.containsAll(u.traits())||u.elements()==null||u.parameters()==null||u.stars()==null||u.description()==null)
                throw new IllegalArgumentException("方舟角色定义无效");
            for(double v:new double[]{u.health(),u.attack(),u.attackRate(),u.range(),u.physicalDefense(),u.magicalDefense()})if(!Double.isFinite(v))throw new IllegalArgumentException("方舟属性必须有限");
            for(var list:u.stars().values())if(list.size()!=3||list.stream().anyMatch(v->!Double.isFinite(v)))throw new IllegalArgumentException("方舟星级参数无效");
            for(double v:u.parameters().values())if(!Double.isFinite(v))throw new IllegalArgumentException("方舟技能参数无效");
        }
        for(int i=0;i<7;i++)if(r.shopProbabilities[i].length!=6||Arrays.stream(r.shopProbabilities[i]).anyMatch(v->v<0)
            ||Arrays.stream(r.shopProbabilities[i]).sum()!=100||r.populations[i]<1)throw new IllegalArgumentException("方舟商店概率无效");
        if(r.enemies.goblinResists==null||r.enemies.dragonResists==null||r.enemies.goblinResists.length!=4||r.enemies.dragonResists.length!=4
            ||r.enemies.goblinFirstCount<1||r.enemies.goblinLastCount>30||r.enemies.goblinGuards+1>30||r.enemies.dragonGuards+1>30)
            throw new IllegalArgumentException("方舟军团参数无效");
        return r;
    }
    private static ArkRules load(){
        try(var input=ArkRules.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/ark_war.json")){
            if(input==null)throw new IllegalStateException("缺少方舟内容定义");
            return parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    public Unit unit(String id){return units.stream().filter(u->u.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知方舟角色："+id));}
    public Trait trait(String id){return traits.stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知羁绊："+id));}
    public static String number(double value){return java.math.BigDecimal.valueOf(value).setScale(3,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
}
