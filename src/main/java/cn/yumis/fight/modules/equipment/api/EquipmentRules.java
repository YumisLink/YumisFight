package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.affixes.api.AffixResolver;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.affixes.api.AttackSpeedRoll;

import java.util.*;

import cn.yumis.fight.modules.affixes.api.Affix;

import static cn.yumis.fight.modules.equipment.api.EquipmentJson.copy;
public final class EquipmentRules {
    private EquipmentRules(){
    }
    public static Gear create(Slot slot,Weapon weapon,Rarity rarity,int level,Random random){
        return create(slot,weapon,rarity,level,ArmorType.UNTYPED,JewelryType.UNTYPED,random);
    }
    public static Gear create(Slot slot,Weapon weapon,Rarity rarity,int level,ArmorType armorType,JewelryType jewelryType,Random random){
        if(!Weapon.standard().contains(weapon)&&slot==Slot.WEAPON)throw new IllegalArgumentException("请使用已设计的具名武器模板");
        if(level<slot.minimum()||level>80||rarity==Rarity.MYTHIC&&slot.armor())throw new IllegalArgumentException("装备模板未开放");
        Gear g=new Gear();
        g.slot=slot;
        g.weapon=weapon;
        g.rarity=rarity;
        g.level=level;
        if(slot==Slot.WEAPON){if(rarity==Rarity.EPIC||rarity==Rarity.MYTHIC)g.appearance=weapon.name().toLowerCase(Locale.ROOT)+"_"+appearanceBand(level)+"_"+rarity.name().toLowerCase(Locale.ROOT);}
        g.armorType=armorType==null?ArmorType.UNTYPED:armorType;
        g.jewelryType=jewelryType==null?JewelryType.UNTYPED:jewelryType;
        EquipmentTemplates.validate(g);
        g.appearance=EquipmentTemplates.appearance(g);
        EquipmentGrowth.capture(g);
        addBases(g);
        int n=level-1;
        double budget=budget(level);
        if(rarity==Rarity.EPIC||rarity==Rarity.MYTHIC){
            if(slot==Slot.WEAPON){
                if(rarity==Rarity.EPIC)extra(g,"epic_dark",3,.12*budget,.24*budget,true);
                else for(int e=0;e<4;e++)extra(g,"mythic_four_"+e,e,.08*budget,.16*budget,true);
            }
            if(slot.armor()){
                Affix shield=new Affix("epic_shield",Stat.SHIELD,(100+20*n)*.04,false);
                shield.fixed=true;
                g.affixes.add(shield);
                g.set="first_sword";
            }
            if(slot.jewelry()&&level>=50)g.set="four_elements";
        }
        for(int i=0;i<rarity.randomCount();i++)appendRandom(g,random);
        return EquipmentResolution.initialize(g);
    }
    public static void rebuildBases(Gear g){g.affixes.removeIf(a->a.base);addBases(g);}
    private static void addBases(Gear g){
        for(var entry:g.baseGrowth.entrySet())g.affixes.add(new Affix("base_"+entry.getKey().name().toLowerCase(Locale.ROOT),entry.getKey(),growthValue(entry.getValue(),g.level),true));
    }
    private static double growthValue(double[] values,int level){if(level<=values.length)return values[level-1];if(values.length<2)throw new IllegalArgumentException("成长快照不完整");return values[values.length-1]+(values[values.length-1]-values[values.length-2])*(level-values.length);}
    public static int appearanceBand(int level){return level<20?10:level<30?20:level<40?30:level<50?40:50;}
    public static double budget(int level){
        double a=20+5*(level-1);
        return a*(1+a/100);
    }
    private static void extra(Gear g,String id,int element,double min,double max,boolean fixed){
        Affix a=new Affix();a.instanceId=UUID.randomUUID().toString();
        a.id=id;
        a.operation="EXTRA";
        a.element=element;
        a.value=a.baseline=min;
        a.maximum=a.baselineMaximum=max;
        a.fixed=fixed;
        g.affixes.add(a);
    }
    public static Map<Stat,Double> contribution(Gear input){
        Gear g=cn.yumis.fight.modules.equipment.api.EquipmentResolution.resolve(input);
        List<Affix> all=EquipmentEffects.effectiveAffixes(g);
        Map<Stat,Double> flats=new EnumMap<>(Stat.class),percent=new EnumMap<>(Stat.class),finalAdds=new EnumMap<>(Stat.class);
        var sink=new cn.yumis.fight.modules.affixes.api.AffixEffects.ItemSink(){
            public void flat(Stat stat,double value){flats.merge(stat,value,Double::sum);}
            public void percent(Stat stat,double value){percent.merge(stat,value,Double::sum);}
            public void finalAdd(Stat stat,double value){finalAdds.merge(stat,value,Double::sum);}
        };
        for(Affix a:all)cn.yumis.fight.modules.affixes.api.AffixEffects.item(a,sink);
        for(Stat s:flats.keySet())flats.put(s,flats.get(s)*(1+percent.getOrDefault(s,0d)));
        // This is the item's final addition. Its complete contribution subsequently enters player BASE.
        finalAdds.forEach((stat,value)->flats.merge(stat,value,Double::sum));
        return flats;
    }
    public static void appendRandom(Gear g,Random random){
        appendRandom(g,random,false);
    }
    private static void appendRandom(Gear g,Random random,boolean allowDuplicates){
        g.affixes.add(cn.yumis.fight.modules.affixes.api.AffixGeneration.sample(generationItem(g),allowDuplicates,random));
    }
    static cn.yumis.fight.modules.affixes.api.AffixGeneration.Item generationItem(Gear g){
        return new cn.yumis.fight.modules.affixes.api.AffixGeneration.Item(){
            public boolean weapon(){return g.slot==Slot.WEAPON;}
            public int level(){return g.level;}
            public double budget(){return EquipmentRules.budget(g.level);}
            public List<Affix> affixes(){return g.affixes;}
            public List<cn.yumis.fight.modules.affixes.api.AffixDefinition> candidates(boolean percent,boolean duplicates){return EquipmentAffixes.candidates(g,percent,duplicates);}
            public double baseline(Affix affix){return EquipmentAffixes.baseline(g,affix);}
        };
    }
    public static boolean replaceable(Affix a){
        return !a.base&&(!a.fixed||a.operation.equals("SKILL_FAMILY"))&&!a.locked;
    }
    public static Gear process(Gear original,String currency,Random random){
        Gear g=cn.yumis.fight.modules.equipment.api.EquipmentResolution.resolve(original);
        if(g.corrupted)throw new IllegalArgumentException("腐化装备仅允许继续镶嵌宝石");
        switch(currency){
            case "transmutation_stone"->{
                if(g.rarity!=Rarity.COMMON)throw new IllegalArgumentException("仅普通装备可蜕变");
                g.rarity=Rarity.RARE;
                appendRandom(g,random);
            }
            case "augmentation_stone"->{
                if(g.rarity!=Rarity.RARE)throw new IllegalArgumentException("仅稀有装备可增幅");
                g.rarity=Rarity.ARTIFACT;
                appendRandom(g,random);
            }
            case "regal_stone"->{
                if(g.rarity!=Rarity.ARTIFACT)throw new IllegalArgumentException("仅神器装备可富豪加工");
                g.rarity=Rarity.LEGENDARY;
                appendRandom(g,random);
            }
            case "exalted_stone"->{
                if(g.rarity!=Rarity.LEGENDARY||g.affixes.stream().filter(a->!a.base).count()>=6)throw new IllegalArgumentException("仅未满六条的传说装备可加工");
                appendRandom(g,random);
            }
            case "chaos_stone"->{
                if(g.rarity==Rarity.EPIC||g.rarity==Rarity.MYTHIC)throw new IllegalArgumentException("固定装备无法替换词条");
                List<Affix> eligible=g.affixes.stream().filter(EquipmentRules::replaceable).toList();
                if(eligible.isEmpty())throw new IllegalArgumentException("没有可删除替换的词条");
                Affix removed=eligible.get(random.nextInt(eligible.size()));
                int index=g.affixes.indexOf(removed);
                g.affixes.remove(index);
                appendRandom(g,random,false);
                Affix replacement=g.affixes.removeLast();
                g.affixes.add(index,replacement);
            }
            case "fracturing_orb"->{
                if(g.rarity!=Rarity.LEGENDARY)throw new IllegalArgumentException("仅传说装备可锁定词条");
                List<Affix> eligible=g.affixes.stream().filter(EquipmentRules::replaceable).toList();
                if(eligible.isEmpty())throw new IllegalArgumentException("没有尚未锁定的可删除词条");
                eligible.get(random.nextInt(eligible.size())).locked=true;
            }
            case "delezie_blood"->{
                boolean level=random.nextDouble()<.2,refresh=random.nextDouble()<.2,socket=random.nextDouble()<.2;
                if(level){g.level=Math.max(1,Math.min(85,g.level+random.nextInt(11)-5));rebuildBases(g);if(g.rarity!=Rarity.EPIC&&g.rarity!=Rarity.MYTHIC)EquipmentAffixes.grow(g);}
                if(level)g=EquipmentResolution.initialize(g);
                if(refresh)reroll(g,random,true,false);
                if(socket)g.sockets++;
                g.corrupted=true;
            }
            case "divine_stone"->{reroll(g,random,false,true);}
            case "socket_stone"->{
                if(g.sockets>=g.slot.maximumSockets())throw new IllegalArgumentException("宝石孔已满");
                g.sockets++;
            }
            case "upgrade_stone"->{
                if(g.rarity==Rarity.EPIC||g.rarity==Rarity.MYTHIC||g.level>=80)throw new IllegalArgumentException("仅未满80级的普通至传说装备可升级");
                g.level=Math.min(80,g.level+5);
                g.affixes.removeIf(a->a.base);
                addBases(g);
                EquipmentAffixes.grow(g);
            }
            case "epic_upgrade_stone","mythic_upgrade_stone"->{
                Rarity required=currency.startsWith("epic")?Rarity.EPIC:Rarity.MYTHIC;
                if(g.rarity!=required||g.level>=80)throw new IllegalArgumentException("品质不符或已满级");
                if(g.slot==Slot.WEAPON&&g.appearance.isEmpty())g.appearance=g.weapon.name().toLowerCase(Locale.ROOT)+"_"+appearanceBand(g.level)+"_"+g.rarity.name().toLowerCase(Locale.ROOT);
                g.level=Math.min(80,g.level+5);
                g.affixes.removeIf(a->a.base);
                addBases(g);
            }
            default->throw new IllegalArgumentException("该加工尚未开放");
        }
        g.revision++;
        return EquipmentResolution.initialize(g);
    }
    public static boolean washable(Affix a){
        if(a.base||a.operation.equals("SKILL_FAMILY")||a.operation.equals("SKILL_PARAMETER"))return false;
        var d=AffixDefinitions.current().affixes.get(a.id);
        return !a.fixed||d!=null&&d.minimum()!=null&&d.maximum()>d.minimum();
    }
    public static void reroll(Gear g,Random random,boolean corrupt,boolean requireChange){
        if(MageBlood.is(g)){MageBlood.reroll(g,random);if(corrupt)for(Affix a:g.affixes)if(a.operation.equals("MAGIC_AMPLIFICATION")){a.corruptionMultiplier=.8+random.nextDouble()*.6;a.value*=a.corruptionMultiplier;}return;}
        boolean changed=false;
        for(Affix a:g.affixes)if(washable(a)){
            if(a.origin!=null&&a.origin.quality==null&&!a.fixed){a.syncStatus="unresolved_snapshot";continue;}
            a.corruptionMultiplier=1;a.roll=.8+random.nextDouble()*.4;double q=(a.roll-.8)/.4;
            a.value=a.baseline*a.roll;a.maximum=a.baselineMaximum*a.roll;
            if(a.stat.equals("ATTACK_SPEED"))a.value=AttackSpeedRoll.sample(g.level,a.roll);
            var d=AffixDefinitions.current().affixes.get(a.id);
            if(d!=null&&d.minimum()!=null){a.value=(d.minimum()+(d.maximum()-d.minimum())*q)*d.scale();if(d.upperMinimum()!=null)a.maximum=(d.upperMinimum()+(d.upperMaximum()-d.upperMinimum())*q)*d.scale();}
            boolean unresolved=a.origin!=null&&a.origin.quality==null&&!a.fixed;
            if(!unresolved)AffixResolver.anchor(a,g.instance+"/"+a.instanceId,d,d==null?0:d.revision(),d==null?1:d.effectiveScale(g.level));
            if(corrupt){a.corruptionMultiplier=.8+random.nextDouble()*.6;a.value*=a.corruptionMultiplier;a.maximum*=a.corruptionMultiplier;}
            changed=true;
        }
        if(!changed&&requireChange)throw new IllegalArgumentException("没有可重洗数值");
    }
    private static String effectKey(Affix a){var definition=AffixDefinitions.current().affixes.get(a.id);return definition==null?a.id:definition.effectKey();}
    public static Gear socket(Gear original,Gem gem,int index){
        Gear g=cn.yumis.fight.modules.equipment.api.EquipmentResolution.resolve(original);
        if(index<0||index>=g.sockets||g.level<gem.level||!(gem.group.equals("all")||gem.group.equals(g.slot.name())||gem.group.equals("armor")&&g.slot.armor()||gem.group.equals("jewelry")&&g.slot.jewelry()))throw new IllegalArgumentException("宝石部位、等级或孔位不符");
        Set<String> existing=new HashSet<>();
        for(int i=0;i<g.gems.size();i++)if(i!=index)for(Affix a:g.gems.get(i).affixes)existing.add(effectKey(a));
        for(Affix a:gem.affixes)if(existing.contains(effectKey(a)))throw new IllegalArgumentException("同件装备不可重复镶嵌相同效果");
        if(index>g.gems.size())throw new IllegalArgumentException("请先镶嵌前面的空孔");
        if(index==g.gems.size())g.gems.add(copy(gem,Gem.class));
        else g.gems.set(index,copy(gem,Gem.class));
        g.revision++;
        return EquipmentResolution.initialize(g);
    }
}
