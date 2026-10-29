package cn.yumis.fight.modules.economy.api;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.DROP_RATE_BASE;
import java.util.*;
/** One catalog for registration, rarity, processing and the natural currency pool. */
public final class CurrencyRules {
    public record AccountCurrency(String id,String title){}
    public static final AccountCurrency POINTS=new AccountCurrency("points","点券");
    public static long resetTicketPrice(boolean profession){return profession?100:50;}
    public record Currency(String id,Rarity rarity,boolean naturalDrop){}
    public record Tier(Rarity rarity,double baseWeight,List<Currency> currencies){}
    public static final double DROP_CHANCE=.05;
    public static final List<Currency> ALL=List.of(
        new Currency("transmutation_stone",Rarity.COMMON,true),
        new Currency("augmentation_stone",Rarity.RARE,true),
        new Currency("delezie_blood",Rarity.RARE,true),
        new Currency("regal_stone",Rarity.ARTIFACT,true),
        new Currency("exalted_stone",Rarity.LEGENDARY,true),
        new Currency("divine_stone",Rarity.EPIC,true),
        new Currency("chaos_stone",Rarity.ARTIFACT,true),
        new Currency("upgrade_stone",Rarity.ARTIFACT,true),
        new Currency("fracturing_orb",Rarity.EPIC,true),
        new Currency("socket_stone",Rarity.RARE,true),
        new Currency("set_extraction_stone",Rarity.LEGENDARY,true),
        new Currency("set_stone",Rarity.LEGENDARY,false),
        new Currency("affix_extraction_stone",Rarity.LEGENDARY,false),
        new Currency("affix_gem",Rarity.ARTIFACT,false),
        new Currency("epic_upgrade_stone",Rarity.EPIC,false),
        new Currency("mythic_upgrade_stone",Rarity.MYTHIC,false)
    );
    public static final List<Currency> DROP_POOL=ALL.stream().filter(Currency::naturalDrop).toList();
    public static final List<Tier> DROP_TIERS=List.of(tier(Rarity.COMMON,50),tier(Rarity.RARE,25),
        tier(Rarity.ARTIFACT,15),tier(Rarity.LEGENDARY,7),tier(Rarity.EPIC,3));
    private static final Map<String,Currency> BY_ID;
    static {
        Map<String,Currency> catalog=new LinkedHashMap<>();
        for(Currency c:ALL)if(catalog.put(c.id(),c)!=null)throw new IllegalStateException("重复通货："+c.id());
        BY_ID=Map.copyOf(catalog);
    }
    private CurrencyRules(){}
    public static long roundedReward(double value){
        finite(value);if(value<0)throw new IllegalArgumentException("奖励不可为负");
        return java.math.BigDecimal.valueOf(value).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();
    }
    public static long killGold(int level,boolean elite,boolean boss,Difficulty difficulty,double multiplier){
        if(level<1||level>80||multiplier<0)throw new IllegalArgumentException("奖励参数无效");
        return roundedReward(level*(1+level/50d)*(boss?10:elite?3:1)*difficulty.xp*multiplier);
    }
    public static long salePrice(cn.yumis.fight.modules.equipment.api.Gear gear){
        if(gear.level<1||gear.level>85)throw new IllegalArgumentException("装备等级无效");
        return Math.multiplyExact(gear.level,new long[]{1,2,5,10,25,50}[gear.rarity.ordinal()]);
    }
    private static Tier tier(Rarity rarity,double baseWeight){
        List<Currency> currencies=DROP_POOL.stream().filter(c->c.rarity()==rarity).toList();
        if(currencies.isEmpty())throw new IllegalStateException("通货稀有度奖池为空："+rarity);
        return new Tier(rarity,baseWeight,currencies);
    }
    public static Currency find(String id){return BY_ID.get(id);}
    public static int upgradeTier(String id){var currency=find(id);return currency!=null&&currency.naturalDrop()&&currency.rarity().ordinal()<Rarity.EPIC.ordinal()?currency.rarity().ordinal():-1;}
    public static List<Currency> upgradeResults(int tier){if(tier<0||tier>=Rarity.EPIC.ordinal())return List.of();return DROP_POOL.stream().filter(c->c.rarity().ordinal()==tier+1).toList();}
    public static long upgradeCost(int tier){return switch(tier){case 0->100;case 1->1000;case 2->10000;case 3->100000;default->throw new IllegalArgumentException("不可升级的通货稀有度");};}
    public static String salvageCurrency(Rarity rarity){return switch(rarity){
        case LEGENDARY->"upgrade_stone";case EPIC->"epic_upgrade_stone";case MYTHIC->"mythic_upgrade_stone";
        default->throw new IllegalArgumentException("只能分解传说、史诗或神话装备");
    };}
    /** Rarity is rolled before a uniform choice within that rarity's eligible pool. */
    public static double[] rarityProbabilities(double dropRate){
        finite(dropRate);
        double boost=Math.log1p(Math.max(0,dropRate)/DROP_RATE_BASE),largest=Double.NEGATIVE_INFINITY;
        double[] weights=new double[DROP_TIERS.size()];
        for(int i=0;i<weights.length;i++){
            Tier tier=DROP_TIERS.get(i);
            weights[i]=Math.log(tier.baseWeight())+tier.rarity().ordinal()*boost;
            largest=Math.max(largest,weights[i]);
        }
        double sum=0;
        for(int i=0;i<weights.length;i++)sum+=weights[i]=Math.exp(weights[i]-largest);
        for(int i=0;i<weights.length;i++)weights[i]/=sum;
        return weights;
    }
    /** Per-item probabilities conditional on a currency having dropped. */
    public static double[] probabilities(double dropRate){
        double[] tiers=rarityProbabilities(dropRate),items=new double[DROP_POOL.size()];
        for(int i=0;i<tiers.length;i++)for(Currency c:DROP_TIERS.get(i).currencies())
            items[DROP_POOL.indexOf(c)]=tiers[i]/DROP_TIERS.get(i).currencies().size();
        return items;
    }
    public static Currency roll(double dropRate,Random random){
        if(random.nextDouble()>=DROP_CHANCE)return null;
        return rollGuaranteed(dropRate,random);
    }
    /** Conditional draw after appearance was already guaranteed; industrial callers pass zero. */
    public static Currency rollGuaranteed(double dropRate,Random random){
        double[] weights=rarityProbabilities(dropRate);
        double pick=random.nextDouble();
        Tier selected=DROP_TIERS.getLast();
        for(int i=0;i<weights.length;i++){
            if(pick<weights[i]){selected=DROP_TIERS.get(i);break;}
            pick-=weights[i];
        }
        List<Currency> pool=selected.currencies();
        return pool.get(random.nextInt(pool.size()));
    }
    /** Editable factory pool, still centralized here; never includes an appearance roll. */
    public static double[] factoryProbabilities(Map<String,Double> rarityWeights,Map<String,Double> itemWeights){
        for(var e:itemWeights.entrySet())if(find(e.getKey())==null||!Double.isFinite(e.getValue())||e.getValue()<0)throw new IllegalArgumentException("无效工业通货权重："+e.getKey());
        List<Rarity> rarities=new ArrayList<>();List<Double> logs=new ArrayList<>();
        for(var e:rarityWeights.entrySet()){Rarity rarity=Rarity.valueOf(e.getKey());double weight=e.getValue();if(!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("无效通货档次权重");if(weight==0)continue;if(ALL.stream().noneMatch(c->c.rarity()==rarity&&itemWeights.getOrDefault(c.id(),0d)>0))throw new IllegalArgumentException("工业通货档次没有候选："+rarity);rarities.add(rarity);logs.add(Math.log(weight));}
        if(rarities.isEmpty())throw new IllegalArgumentException("工业通货池不能为空");double max=Collections.max(logs),sum=logs.stream().mapToDouble(v->Math.exp(v-max)).sum();double[] result=new double[ALL.size()];
        for(int i=0;i<rarities.size();i++){Rarity rarity=rarities.get(i);double itemMax=ALL.stream().filter(c->c.rarity()==rarity&&itemWeights.getOrDefault(c.id(),0d)>0).mapToDouble(c->Math.log(itemWeights.get(c.id()))).max().orElseThrow();double itemSum=ALL.stream().filter(c->c.rarity()==rarity&&itemWeights.getOrDefault(c.id(),0d)>0).mapToDouble(c->Math.exp(Math.log(itemWeights.get(c.id()))-itemMax)).sum();double tier=Math.exp(logs.get(i)-max)/sum;for(int j=0;j<ALL.size();j++){Currency c=ALL.get(j);double w=itemWeights.getOrDefault(c.id(),0d);if(c.rarity()==rarity&&w>0)result[j]=tier*Math.exp(Math.log(w)-itemMax)/itemSum;}}
        return result;
    }
    public static Currency rollGuaranteed(Random random,Map<String,Double> rarityWeights,Map<String,Double> itemWeights){
        return rollGuaranteed(random,factoryProbabilities(rarityWeights,itemWeights));
    }
    public static Currency rollGuaranteed(Random random,double[] probabilities){
        if(probabilities.length!=ALL.size())throw new IllegalArgumentException("工业通货池版本不匹配");double pick=random.nextDouble();Currency last=null;for(int i=0;i<probabilities.length;i++)if(probabilities[i]>0){last=ALL.get(i);if(pick<probabilities[i])return last;pick-=probabilities[i];}return Objects.requireNonNull(last);
    }
}
