package cn.yumis.fight.rules;
import java.util.*;
import static cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
public final class EquipmentRules {
    private EquipmentRules(){
    }
    public static Gear create(Slot slot,Weapon weapon,Rarity rarity,int level,Random random){
        if(level<slot.minimum()||level>80||rarity==Rarity.MYTHIC&&slot.armor())throw new IllegalArgumentException("装备模板未开放");
        Gear g=new Gear();
        g.slot=slot;
        g.weapon=weapon;
        g.rarity=rarity;
        g.level=level;
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
        return g;
    }
    private static void addBases(Gear g){
        double scale=g.rarity==Rarity.MYTHIC?1.9:g.rarity==Rarity.EPIC?(g.slot==Slot.WEAPON?1.6:1.5):1;
        for(Definitions.Curve curve:Definitions.CURRENT.equipment.get(g.slot).base())g.affixes.add(new Affix("base_"+curve.stat().name().toLowerCase(Locale.ROOT),curve.stat(),curve.at(g.slot,g.level)*scale,true));
    }
    public static double budget(int level){
        double a=20+5*(level-1);
        return a*(1+a/100);
    }
    private static void extra(Gear g,String id,int element,double min,double max,boolean fixed){
        Affix a=new Affix();
        a.id=id;
        a.operation="EXTRA";
        a.element=element;
        a.value=a.baseline=min;
        a.maximum=a.baselineMaximum=max;
        a.fixed=fixed;
        g.affixes.add(a);
    }
    public static Map<Stat,Double> contribution(Gear g){
        List<Affix> all=new ArrayList<>(g.affixes);
        for(Gem gem:g.gems)all.addAll(gem.affixes);
        Map<Stat,Double> flats=new EnumMap<>(Stat.class),percent=new EnumMap<>(Stat.class);
        for(Affix a:all){
            if(a.stat.isEmpty())continue;
            Stat s=Stat.valueOf(a.stat);
            if(a.operation.equals("FLAT"))flats.merge(s,a.value,Double::sum);
            if(a.operation.equals("PERCENT"))percent.merge(s,a.value,Double::sum);
        }
        for(Stat s:flats.keySet())flats.put(s,flats.get(s)*(1+percent.getOrDefault(s,0d)));
        return flats;
    }
    private static double reference(Gear g,Stat stat){
        int n=g.level-1;
        return switch(stat){
            case HEALTH,SHIELD->100+20*n;
            case MP->100+14*n;
            case STRENGTH->20+3*n;
            case INTELLIGENCE->15+2*n;
            case SPIRIT->10+2*n;
            case PHYSICAL_ATTACK,MAGICAL_ATTACK->15+4*n;
            case PHYSICAL_DEFENSE->g.slot.armor()?defenses(g.level,false)[g.slot.ordinal()-1]:(80+15*n)/4d;
            case MAGICAL_DEFENSE->g.slot.armor()?defenses(g.level,true)[g.slot.ordinal()-1]:(40+10*n)/4d;
            case DROP_RATE->2;
            default->3+.15*n;
        }
        ;
    }
    public static void appendRandom(Gear g,Random random){
        List<Stat> flat=new ArrayList<>(),percent=new ArrayList<>();
        for(Stat s:Stat.values()){
            if(s==Stat.SHIELD&&!g.slot.armor())continue;
            if(g.affixes.stream().noneMatch(a->!a.base&&!a.fixed&&a.stat.equals(s.name())&&a.operation.equals("FLAT")))flat.add(s);
            if(contribution(g).getOrDefault(s,0d)>0&&g.affixes.stream().noneMatch(a->!a.base&&a.stat.equals(s.name())&&a.operation.equals("PERCENT")))percent.add(s);
        }
        List<Integer> elements=new ArrayList<>();
        List<Family> families=new ArrayList<>();
        if(g.slot==Slot.WEAPON){
            if(g.affixes.stream().filter(a->a.operation.equals("EXTRA")&&!a.fixed).count()<2)for(int e=0;e<4;e++){
                int f=e;
                if(g.affixes.stream().noneMatch(a->a.operation.equals("EXTRA")&&a.element==f))elements.add(e);
            }
            for(Family f:Family.values())if(g.affixes.stream().noneMatch(a->a.family.equals(f.name())))families.add(f);
        }
        double[] weights=g.slot==Slot.WEAPON?new double[]{
            51,21.25,12.75,15
        }
        :new double[]{
            70,30,0,0
        }
        ;
        if(flat.isEmpty())weights[0]=0;
        if(percent.isEmpty())weights[1]=0;
        if(elements.isEmpty())weights[2]=0;
        if(families.isEmpty())weights[3]=0;
        double total=Arrays.stream(weights).sum();
        if(total==0)throw new IllegalArgumentException("没有合格词条");
        double p=random.nextDouble()*total;
        int group=0;
        while(p>=weights[group])p-=weights[group++];
        double roll=.8+random.nextDouble()*.4;
        if(group==3){
            Affix a=new Affix();
            a.id="family_"+families.get(random.nextInt(families.size())).name().toLowerCase(Locale.ROOT);
            a.family=a.id.substring(7).toUpperCase(Locale.ROOT);
            a.operation="SKILL_FAMILY";
            a.rank=1+random.nextInt(3);
            a.fixed=true;
            g.affixes.add(a);
            return;
        }
        if(group==2){
            int e=elements.get(random.nextInt(elements.size()));
            extra(g,"extra_"+e,e,.10*budget(g.level),.20*budget(g.level),false);
            Affix a=g.affixes.getLast();
            a.roll=roll;
            a.value=a.baseline*roll;
            a.maximum=a.baselineMaximum*roll;
            return;
        }
        Stat s=(group==0?flat:percent).get(random.nextInt(group==0?flat.size():percent.size()));
        Affix a=new Affix();
        a.stat=s.name();
        a.operation=group==0?"FLAT":"PERCENT";
        a.id=(group==0?"flat_":"percent_")+s.name().toLowerCase(Locale.ROOT);
        double base=group==1?.175:s==Stat.DROP_RATE?2:s.ordinal()>=Stat.LIGHT.ordinal()&&s.ordinal()<=Stat.DARK_RESIST.ordinal()?reference(g,s):reference(g,s)*(s==Stat.HEALTH||s==Stat.MP||s==Stat.SHIELD?.045:.075);
        a.baseline=base;
        a.roll=roll;
        a.value=base*roll;
        g.affixes.add(a);
    }
    public static Gear process(Gear original,String currency,Random random){
        Gear g=copy(original,Gear.class);
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
            case "divine_stone"->{
                boolean changed=false;
                if(g.rarity==Rarity.EPIC||g.rarity==Rarity.MYTHIC)throw new IllegalArgumentException("固定装备无法重洗");
                for(Affix a:g.affixes)if(!a.fixed&&!a.base){
                    a.roll=.8+random.nextDouble()*.4;
                    a.value=a.baseline*a.roll;
                    a.maximum=a.baselineMaximum*a.roll;
                    changed=true;
                }
                if(!changed)throw new IllegalArgumentException("没有可重洗数值");
            }
            case "socket_stone"->{
                if(g.sockets>=g.slot.maximumSockets())throw new IllegalArgumentException("宝石孔已满");
                g.sockets++;
            }
            case "epic_upgrade_stone","mythic_upgrade_stone"->{
                Rarity required=currency.startsWith("epic")?Rarity.EPIC:Rarity.MYTHIC;
                if(g.rarity!=required||g.level>=80)throw new IllegalArgumentException("品质不符或已满级");
                g.level=Math.min(80,g.level+5);
                g.affixes.removeIf(a->a.base);
                addBases(g);
            }
            default->throw new IllegalArgumentException("该加工尚未开放");
        }
        g.revision++;
        return g;
    }
    public static Gear socket(Gear original,Gem gem,int index){
        Gear g=copy(original,Gear.class);
        if(index<0||index>=g.sockets||g.level<gem.level||!(gem.group.equals("all")||gem.group.equals(g.slot.name())||gem.group.equals("armor")&&g.slot.armor()||gem.group.equals("jewelry")&&g.slot.jewelry()))throw new IllegalArgumentException("宝石部位、等级或孔位不符");
        Set<String> existing=new HashSet<>();
        for(Affix a:g.affixes)existing.add(a.id);
        for(int i=0;i<g.gems.size();i++)if(i!=index)for(Affix a:g.gems.get(i).affixes)existing.add(a.id);
        for(Affix a:gem.affixes)if(existing.contains(a.id))throw new IllegalArgumentException("同件装备不可重复镶嵌相同效果");
        if(index>g.gems.size())throw new IllegalArgumentException("请先镶嵌前面的空孔");
        if(index==g.gems.size())g.gems.add(copy(gem,Gem.class));
        else g.gems.set(index,copy(gem,Gem.class));
        g.revision++;
        return g;
    }
    public static Stats stats(Progress p,List<Gear> equipped){
        return stats(p,equipped,new cn.yumis.fight.api.AttributeSources(),ignored->{
        }
        );
    }
    public static Stats stats(Progress p,List<Gear> equipped,cn.yumis.fight.api.AttributeSources sources,java.util.function.Consumer<Stats> nativeItems){
        Stats s=new Stats();
        Map<String,Integer> sets=new HashMap<>();
        Map<Stat,Double> ratios=new EnumMap<>(Stat.class);
        for(Gear g:equipped){
            if(!g.valid(p))continue;
            contribution(g).forEach(s::add);
            List<Affix> all=new ArrayList<>(g.affixes);
            for(Gem gem:g.gems)all.addAll(gem.affixes);
            for(Affix a:all){
                switch(a.operation){
                    case "EXTRA"->s.extras.add(a);
                    case "SKILL_FAMILY"->s.ranks.merge(Family.valueOf(a.family),a.rank,Integer::sum);
                    case "CHARACTER_PERCENT"->ratios.merge(Stat.valueOf(a.stat),a.value,Double::sum);
                    case "MODIFIER"->s.modifiers.merge(a.id,a.value,Double::sum);
                    case "SKILL_ATTACK"->s.modifiers.merge("skillProduct",1+a.value,(x,y)->x*y);
                    case "SKILL_PARAMETER"->s.parameters.add(new cn.yumis.fight.api.SkillParameters.Change(g.instance+"/"+a.id,Skill.valueOf(a.skill),a.parameter,a.value,a.roll));
                    default->{
                    }
                }
            }
            if(g.slot==Slot.WEAPON){
                s.weaponValid=true;
                s.weaponInstance=g.instance.toString();
                s.weaponType=g.weapon.name();
            }
            Definitions.SetDefinition set=Definitions.CURRENT.sets.get(g.set);
            if(set!=null&&(set.group().equals("armor")&&g.slot.armor()||set.group().equals("jewelry")&&g.slot.jewelry()))sets.merge(g.set,1,Integer::sum);
        }
        s.armorSet=sets.getOrDefault("first_sword",0);
        s.jewelrySet=sets.getOrDefault("four_elements",0);
        for(var entry:sets.entrySet())for(var effect:Definitions.CURRENT.sets.get(entry.getKey()).effects())if(entry.getValue()>=effect.pieces()){
            if(effect.stat()!=null)s.add(effect.stat(),effect.value());
            else if(effect.modifier()!=null&&!effect.modifier().isEmpty())s.modifiers.merge(effect.modifier(),effect.value(),Double::sum);
        }
        nativeItems.accept(s);
        var items=sources.apply(s.values,cn.yumis.fight.api.AttributeSources.Stage.ITEM);
        s.values.clear();
        s.values.putAll(items);
        double n=p.level-1;
        s.add(Stat.HEALTH,100+20*n);
        s.add(Stat.MP,80+10*n);
        s.add(Stat.STRENGTH,20+3*n+p.strength);
        s.add(Stat.INTELLIGENCE,15+2*n+p.intelligence);
        s.add(Stat.SPIRIT,10+2*n+p.spirit);
        s.add(Stat.PHYSICAL_ATTACK,5+n);
        s.add(Stat.MAGICAL_ATTACK,5+n);
        s.add(Stat.PHYSICAL_DEFENSE,20);
        s.add(Stat.MAGICAL_DEFENSE,20);
        for(var ratio:ratios.entrySet())s.values.put(ratio.getKey(),s.get(ratio.getKey())*(1+ratio.getValue()));
        for(var pair:Map.of(Stat.STRENGTH,"strengthRatio",Stat.INTELLIGENCE,"intelligenceRatio",Stat.PHYSICAL_ATTACK,"physicalAttackRatio",Stat.MAGICAL_ATTACK,"magicalAttackRatio").entrySet())s.values.put(pair.getKey(),s.get(pair.getKey())*(1+s.modifiers.getOrDefault(pair.getValue(),0d)));
        s.modifiers.computeIfPresent("crit",(k,v)->Math.clamp(v,0,1));
        var character=sources.apply(s.values,cn.yumis.fight.api.AttributeSources.Stage.CHARACTER);
        s.values.clear();
        s.values.putAll(character);
        s.add(Stat.MP,2*s.get(Stat.SPIRIT));
        s.add(Stat.MAGICAL_DEFENSE,2*s.get(Stat.SPIRIT));
        return s;
    }
}
