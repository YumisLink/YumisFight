package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.platform.*;
import com.google.gson.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import java.util.*;

/** One physical stone. Rolled effects and reward values are immutable item snapshots. */
public final class Waystone {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    public static final String DATA="waystone_v1";
    public record Modifier(String id,String title,String effect,double value){}
    public UUID id=UUID.randomUUID();
    public int tier=1;
    public String dungeon="";
    public Rarity rarity=Rarity.COMMON;
    public List<Modifier> modifiers=new ArrayList<>();
    public double experienceMultiplier=1,goldMultiplier=1,quantityMultiplier=1,dropMultiplier=1;
    public static Waystone generate(int tier,int playerLevel,Rarity rarity,Random random){
        if(tier<1||rarity==null||rarity.ordinal()>Rarity.LEGENDARY.ordinal())throw new IllegalArgumentException("引路石阶数或品质无效");
        var rules=WaystoneRules.current();rules.monsterLevel(tier);rules.statMultiplier(tier);
        var pool=DungeonCatalog.current().dungeons.stream().filter(d->d.available()&&d.level()<=playerLevel).toList();
        if(pool.isEmpty())throw new IllegalArgumentException("没有已解锁的引路石副本");
        var stone=new Waystone();stone.tier=tier;stone.rarity=rarity;stone.dungeon=pool.get(random.nextInt(pool.size())).id();
        int[] range=rules.affixCounts[rarity.ordinal()];int count=random.nextInt(range[0],range[1]+1);
        var choices=new ArrayList<>(rules.affixes.stream().filter(WaystoneRules.Affix::enabled).toList());Collections.shuffle(choices,random);Set<String> groups=new HashSet<>();
        for(var a:choices){if(stone.modifiers.size()==count)break;if(!a.group().isEmpty()&&!groups.add(a.group()))continue;double v=a.effect().endsWith("resistance")?random.nextInt((int)a.minimum(),(int)a.maximum()+1):a.minimum()+random.nextDouble()*(a.maximum()-a.minimum());stone.modifiers.add(new Modifier(a.id(),a.title(),a.effect(),v));}
        if(stone.modifiers.size()!=count)throw new IllegalArgumentException("引路石词条池不足");
        double base=rules.rewardMultiplier(tier,count);
        stone.experienceMultiplier=rolledReward(base,rules.rewardVariance,random);stone.goldMultiplier=rolledReward(base,rules.rewardVariance,random);stone.quantityMultiplier=rolledReward(base,rules.rewardVariance,random);stone.dropMultiplier=rolledReward(base,rules.rewardVariance,random);
        return stone;
    }
    private static double rolledReward(double base,double variance,Random random){return 1+(base-1)*(1-variance+random.nextDouble()*2*variance);}
    public void validate(){
        if(id==null||tier<1||dungeon==null||dungeon.isEmpty()||rarity==null||rarity.ordinal()>3||modifiers==null||modifiers.size()>8)throw new IllegalArgumentException("引路石实例无效");
        Set<String> ids=new HashSet<>();for(var a:modifiers)if(a==null||a.id()==null||!ids.add(a.id())||a.title()==null||!WaystoneRules.EFFECTS.contains(a.effect())||!Double.isFinite(a.value())||a.value()<0||!a.effect().endsWith("resistance")&&a.value()>=1)throw new IllegalArgumentException("引路石词条实例无效");
        for(double v:new double[]{experienceMultiplier,goldMultiplier,quantityMultiplier,dropMultiplier})if(!Double.isFinite(v)||v<1)throw new IllegalArgumentException("引路石收益实例无效");
    }
    public static Waystone read(ItemStack stack){
        if(!ItemData.own(stack)||!ItemData.id(stack).equals("waystone"))return null;
        String raw=ItemData.string(stack,DATA);if(raw.isEmpty())return null;var result=JSON.fromJson(raw,Waystone.class);if(result==null)throw new IllegalArgumentException("引路石实例为空");result.validate();return result;
    }
    public ItemStack item(){validate();var stack=ItemRegistry.item("waystone",1);ItemData.string(stack,DATA,JSON.toJson(this));stack.set(DataComponents.CUSTOM_NAME,Component.literal("引路石 +"+tier).withStyle(s->s.withColor(rarity.color()).withItalic(false)));stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(List.of(),List.of(),List.of(rarity.name().toLowerCase(Locale.ROOT)),List.of()));return stack;}
    public double effect(String effect){return modifiers.stream().filter(m->m.effect().equals(effect)).mapToDouble(Modifier::value).sum();}
    public double effectiveDropRate(double original){return (1000+original)*dropMultiplier-1000;}
    public static String number(double value){return java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    public static String modifierText(Modifier m){String value=number(m.value()*100)+"%";return m.title()+"："+switch(m.effect()){
        case "monster_health"->"怪物生命提高"+value;case "monster_attack"->"怪物攻击提高"+value;case "monster_defense"->"怪物双防提高"+value;case "monster_resistance"->"怪物四抗各增加"+number(m.value())+"点";
        case "mana_regeneration"->"玩家回魔速度降低"+value;case "cooldown_recovery"->"玩家冷却恢复速度降低"+value;case "movement_speed"->"玩家移动速度降低"+value;case "player_defense"->"玩家双防降低"+value+"（精神额外魔防除外）";case "player_resistance"->"玩家四抗各降低"+number(m.value())+"点";case "cast_speed"->"玩家释放速度降低"+value;default->throw new IllegalArgumentException("未知引路石词条");};}
    public List<String> rewardText(){return List.of("经验提高 "+number((experienceMultiplier-1)*100)+"%","金币提高 "+number((goldMultiplier-1)*100)+"%","普通掉落数量提高 "+number((quantityMultiplier-1)*100)+"%","掉宝率基数提高 "+number((dropMultiplier-1)*100)+"%");}
}
