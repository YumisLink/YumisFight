package cn.yumis.fight.modules.economy.api;
import cn.yumis.fight.modules.character.api.CharacterItems;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.EnhancementRules;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.DROP_RATE_BASE;
import java.util.*;
/** One catalog for registration, rarity, processing and the natural currency pool. */
public final class CurrencyRules {
    public record AccountCurrency(String id,String title){}
    public static final AccountCurrency GOLD=new AccountCurrency("gold","金币");
    public static final AccountCurrency ARK_COIN=new AccountCurrency("ark_coin","方舟币");
    public static final AccountCurrency POINTS=new AccountCurrency("points","点券");
    public static final AccountCurrency COLORLESS_CRYSTAL=new AccountCurrency("colorless_crystal","无色小晶块");
    public static final long CONTRACT_PRICE=100000,CONTRACT_DURATION=604800000;
    public static long ticketPrice(String id){return switch(id){case CharacterItems.CAREER_UNLOCK->500;case CharacterItems.AWAKENING->1000;default->-1;};}
    public record Currency(String id,Rarity rarity,boolean naturalDrop){}
    public record Tier(Rarity rarity,double baseWeight,List<Currency> currencies){}
    public static final double DROP_CHANCE=.05;
    public static final List<Currency> ALL=catalog(List.of(
        new Currency("transmutation_stone",Rarity.COMMON,true),
        new Currency("augmentation_stone",Rarity.RARE,true),
        new Currency("delezie_blood",Rarity.RARE,true),
        new Currency(EnhancementRules.OTHERWORLD_AURA,Rarity.ARTIFACT,false),
        new Currency(EnhancementRules.INTENSE_AURA,Rarity.EPIC,false),
        new Currency(EnhancementRules.AURA_TRANSFORM_BOX,Rarity.MYTHIC,false),
        new Currency("regal_stone",Rarity.ARTIFACT,true),
        new Currency("exalted_stone",Rarity.LEGENDARY,true),
        new Currency(cn.yumis.fight.modules.equipment.api.EquipmentRules.MASTER_CRYSTAL,Rarity.LEGENDARY,false),
        new Currency("stripping_stone",Rarity.LEGENDARY,true),
        new Currency("divine_stone",Rarity.EPIC,true),
        new Currency("advanced_divine_stone",Rarity.EPIC,false),
        new Currency("perfect_divine_stone",Rarity.EPIC,false),
        new Currency("chaos_stone",Rarity.ARTIFACT,true),
        new Currency("upgrade_stone",Rarity.ARTIFACT,true),
        new Currency("fracturing_orb",Rarity.EPIC,true),
        new Currency("socket_stone",Rarity.RARE,true),
        new Currency("set_extraction_stone",Rarity.LEGENDARY,true),
        new Currency("set_stone",Rarity.LEGENDARY,false),
        new Currency("affix_extraction_stone",Rarity.LEGENDARY,false),
        new Currency("affix_gem",Rarity.ARTIFACT,false),
        new Currency("epic_upgrade_stone",Rarity.EPIC,false),
        new Currency("mythic_upgrade_stone",Rarity.MYTHIC,false),
        new Currency("altar_token_uncrowned_gardener",Rarity.ARTIFACT,false),
        new Currency("altar_token_off_duty_matron",Rarity.ARTIFACT,false),
        new Currency("altar_token_white_reliquary",Rarity.ARTIFACT,false),
        new Currency("altar_token_debt_collector",Rarity.ARTIFACT,false),
        new Currency("altar_token_fallen_scale_king",Rarity.ARTIFACT,false),
        new Currency("altar_token_thousandfold_confession",Rarity.ARTIFACT,false),
        new Currency("warm_dragon_scale",Rarity.ARTIFACT,false),
        new Currency("penitent_shard",Rarity.ARTIFACT,false),
        new Currency("lightmoss_scion",Rarity.LEGENDARY,false),
        new Currency("severance_spring",Rarity.LEGENDARY,false),
        new Currency("unfinished_chant_core",Rarity.LEGENDARY,false),
        new Currency("stopped_clock_gear",Rarity.LEGENDARY,false),
        new Currency("ember_scale",Rarity.LEGENDARY,false),
        new Currency("silent_chip",Rarity.LEGENDARY,false)
    ));
    private static List<Currency> catalog(List<Currency> base){
        var all=new ArrayList<>(base);
        for(var d:cn.yumis.fight.modules.equipment.api.CraftingEssences.all())all.add(new Currency(d.id(),Rarity.LEGENDARY,false));
        for(var d:cn.yumis.fight.modules.equipment.api.Gemstones.all())all.add(new Currency(d.id(),d.rarity(),false));
        return List.copyOf(all);
    }
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
    public static cn.yumis.fight.modules.equipment.api.Gemstones.Definition rollGem(boolean elite,boolean boss,double dropRate,Random random){
        if(random.nextDouble()>=(boss?.03:elite?.01:.002))return null;
        finite(dropRate);double boost=Math.log1p(Math.max(0,dropRate)/DROP_RATE_BASE);
        double[] base=boss?new double[]{50,40,10}:elite?new double[]{70,25,5}:new double[]{90,9,1};
        double[] weights=new double[3];double largest=Double.NEGATIVE_INFINITY;
        for(int i=0;i<3;i++){weights[i]=Math.log(base[i])+i*boost;largest=Math.max(largest,weights[i]);}
        double total=0;for(int i=0;i<3;i++)total+=weights[i]=Math.exp(weights[i]-largest);
        double pick=random.nextDouble()*total;int selected=2;
        for(int i=0;i<3;i++){if(pick<weights[i]){selected=i;break;}pick-=weights[i];}
        Rarity rarity=List.of(Rarity.RARE,Rarity.ARTIFACT,Rarity.LEGENDARY).get(selected);
        var pool=cn.yumis.fight.modules.equipment.api.Gemstones.all().stream().filter(d->d.rarity()==rarity).toList();
        return pool.get(random.nextInt(pool.size()));
    }
    public static long roundedReward(double value){
        finite(value);if(value<0)throw new IllegalArgumentException("奖励不可为负");
        return java.math.BigDecimal.valueOf(value).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();
    }
    public static long killGold(int level,boolean elite,boolean boss,Difficulty difficulty,double multiplier){
        if(level<1||level>80||multiplier<0)throw new IllegalArgumentException("奖励参数无效");
        return roundedReward(level*(1+level/50d)*(boss?10:elite?3:1)*difficulty.xp*multiplier);
    }
    private static long saleMultiplier(Rarity rarity){
        return switch(rarity){
            case COMMON->1L;
            case RARE->2L;
            case ARTIFACT->5L;
            case LEGENDARY->10L;
            case EPIC->25L;
            case MYTHIC->50L;
            case PRIMORDIAL->0L;
        };
    }
    public static long salePrice(cn.yumis.fight.modules.equipment.api.Gear gear){
        if(gear.level<1||gear.level>85)throw new IllegalArgumentException("装备等级无效");
        long multiplier=saleMultiplier(gear.rarity);
        if(multiplier==0L)throw new IllegalArgumentException("装备暂无出售定价");
        return Math.multiplyExact(gear.level,multiplier);
    }
    /** Returns the complete stack price when the stack can be sold. */
    public static OptionalLong saleQuote(net.minecraft.world.item.ItemStack stack){
        if(stack.isEmpty())return OptionalLong.empty();
        var gear=cn.yumis.fight.modules.equipment.api.EquipmentItems.gear(stack);
        long multiplier=gear==null||gear.rarity==null?1L:saleMultiplier(gear.rarity);
        if(multiplier==0L)return OptionalLong.empty();
        long unitPrice=gear!=null&&gear.rarity!=null&&gear.level>=1&&gear.level<=85?Math.multiplyExact(gear.level,multiplier):1L;
        return OptionalLong.of(Math.multiplyExact(unitPrice,stack.getCount()));
    }
    public static long salePrice(net.minecraft.world.item.ItemStack stack){
        return saleQuote(stack).orElseThrow(()->new IllegalArgumentException(stack.isEmpty()?"空槽不可出售":"装备暂无出售定价"));
    }
    private static Tier tier(Rarity rarity,double baseWeight){
        List<Currency> currencies=DROP_POOL.stream().filter(c->c.rarity()==rarity).toList();
        if(currencies.isEmpty())throw new IllegalStateException("通货稀有度奖池为空："+rarity);
        return new Tier(rarity,baseWeight,currencies);
    }
    public static Currency find(String id){return BY_ID.get(id);}
    public static List<String> standardStorageIds(){
        return List.of("transmutation_stone","augmentation_stone","regal_stone","exalted_stone",
            "stripping_stone","divine_stone","advanced_divine_stone","perfect_divine_stone","chaos_stone",
            "upgrade_stone","fracturing_orb","socket_stone","set_extraction_stone","affix_extraction_stone",
            "epic_upgrade_stone","mythic_upgrade_stone","delezie_blood",EnhancementRules.OTHERWORLD_AURA,
            EnhancementRules.INTENSE_AURA,EnhancementRules.AURA_TRANSFORM_BOX,cn.yumis.fight.modules.equipment.api.EquipmentRules.MASTER_CRYSTAL);
    }
    public static final long STORAGE_UNLOCK_PRICE=100_000;
    public static int upgradeTier(String id){var currency=find(id);return currency!=null&&currency.naturalDrop()&&currency.rarity().ordinal()<Rarity.EPIC.ordinal()?currency.rarity().ordinal():-1;}
    public static List<Currency> upgradeResults(int tier){if(tier<0||tier>=Rarity.EPIC.ordinal())return List.of();return DROP_POOL.stream().filter(c->c.rarity().ordinal()==tier+1).toList();}
    public static long upgradeCost(int tier){return switch(tier){case 0->100;case 1->1000;case 2->10000;case 3->100000;default->throw new IllegalArgumentException("不可升级的通货稀有度");};}
    public record DismantleResult(String item,int count){}
    public static List<String> dismantlePool(Rarity rarity){
        return switch(rarity){
            case COMMON->List.of(COLORLESS_CRYSTAL.id());
            case EPIC->List.of("epic_upgrade_stone");
            case MYTHIC->List.of("mythic_upgrade_stone");
            default->DROP_POOL.stream().filter(c->c.rarity().ordinal()==rarity.ordinal()-1).map(Currency::id).toList();
        };
    }
    /** One kind per item. Neither the quantity nor the uniform pool depends on drop-rate stats. */
    public static DismantleResult dismantle(Rarity rarity,Random random){
        var pool=dismantlePool(rarity);int count=rarity==Rarity.EPIC||rarity==Rarity.MYTHIC?1:1+random.nextInt(3);
        return new DismantleResult(pool.get(random.nextInt(pool.size())),count);
    }
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
