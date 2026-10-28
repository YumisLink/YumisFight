package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.AffixResolver;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.affixes.api.MagicEffects;

import cn.yumis.fight.modules.attributes.api.Stat;

import java.util.*;
import cn.yumis.fight.modules.affixes.api.*;
import cn.yumis.fight.modules.affixes.api.Affix;

import cn.yumis.fight.modules.attributes.api.Stats;

/** Four list-valued magic affixes. Identities, not translated text, determine duplicates. */
public final class MageBlood {
    public static final String TEMPLATE="mythic/mage_blood_legs",APPEARANCE="mage_blood_legs",NAME="法师之血-秘仪长裤";
    private MageBlood(){}
    public static boolean is(Gear gear){return TEMPLATE.equals(gear.template);}
    public static Gear create(Random random){
        Gear g=new Gear();g.template=TEMPLATE;g.appearance=APPEARANCE;g.slot=Slot.LEGS;g.armorType=ArmorType.CLOTH;g.rarity=Rarity.MYTHIC;g.level=60;
        reroll(g,random);return EquipmentResolution.initialize(g);
    }
    public static void reroll(Gear g,Random random){
        Set<String> existing=new HashSet<>();for(Affix a:g.affixes)if(a.operation.equals("MAGIC"))existing.add(a.family);
        var choices=MagicEffects.all().stream().filter(m->{var d=AffixDefinitions.current().affixes.get("magic/"+m.id().toLowerCase(Locale.ROOT));return d!=null&&(d.active()||existing.contains(m.id()));}).toList();
        if(choices.isEmpty())throw new IllegalArgumentException("没有可生成的魔法定义");
        g.affixes.removeIf(a->a.operation.equals("MAGIC")||a.operation.equals("MAGIC_AMPLIFICATION"));
        Affix amp=new Affix();amp.instanceId=UUID.randomUUID().toString();amp.id="magic_amplification";amp.operation="MAGIC_AMPLIFICATION";amp.value=.25+random.nextDouble()*.25;g.affixes.add(amp);
        for(int i=0;i<4;i++){Affix a=new Affix();a.instanceId=UUID.randomUUID().toString();a.id="magic";a.operation="MAGIC";a.family=choices.get(random.nextInt(choices.size())).id();g.affixes.add(a);}
    }
    public static int duplicates(Gear g){
        Map<String,Integer> counts=new HashMap<>();for(Affix a:g.affixes)if(a.operation.equals("MAGIC"))counts.merge(a.family,1,Integer::sum);
        int n=counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);return n>=2?n:0;
    }
    public static double amplification(Gear g){return g.affixes.stream().filter(a->a.operation.equals("MAGIC_AMPLIFICATION")).mapToDouble(a->a.value).findFirst().orElse(0);}
    public static double multiplier(Gear g){return 1+duplicates(g)*amplification(g);}
    private static void element(Stats s,Stat damage,Stat resistance,double value){s.extra(damage,value);s.extra(resistance,value);}
    public static void apply(Gear g,Stats s){
        double q=multiplier(g);Set<String> seen=new HashSet<>();
        var sink=new MagicEffects.Sink(){
            public void extra(Stat stat,double amount){s.extra(stat,amount);}
            public void mut(Stat stat,double amount){s.mut(stat,amount);}
            public void modifier(String name,double amount,boolean mut){if(mut)s.modifier(name).mut+=amount;else s.modifier(name).add+=amount;}
        };
        for(Affix a:g.affixes)if(a.operation.equals("MAGIC")&&seen.add(a.family)){
            var effect=MagicEffects.find(a.family);if(effect==null)continue;
            var definition=AffixDefinitions.current().affixes.get(AffixResolver.definitionId(a));
            double scale=definition==null||a.origin!=null&&definition.revision()<a.origin.revision?(a.origin==null?1:a.origin.definitionScale):definition.scale();
            effect.effect().accept(sink,q*scale);
        }
    }
}
