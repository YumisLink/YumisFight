package cn.yumis.fight.modules.affixes.api;

import java.util.*;
import java.util.function.*;

/** Weighted content groups; the equipment module supplies eligibility and level curves only. */
public final class AffixGeneration {
    public interface Item {
        boolean weapon(); int level(); double budget(); List<Affix> affixes();
        List<AffixDefinition> candidates(boolean percent,boolean duplicates);
        double baseline(Affix affix);
    }
    public record Context(Item item,boolean duplicates){}
    @FunctionalInterface public interface Factory { Affix create(Context context,AffixDefinition definition,double roll,Random random); }
    public record Group(ToDoubleFunction<Context> weight,Function<Context,List<AffixDefinition>> candidates,Factory factory){}
    private static final Map<String,Group> GROUPS=new LinkedHashMap<>();
    public static void register(String id,Group group){if(GROUPS.putIfAbsent(id,Objects.requireNonNull(group))!=null)throw new IllegalArgumentException("Duplicate affix generation group: "+id);}
    static {
        register("flat",new Group(c->c.item.weapon()?51:70,c->c.item.candidates(false,c.duplicates),AffixGeneration::stat));
        register("percent",new Group(c->c.item.weapon()?21.25:30,c->c.item.candidates(true,c.duplicates),AffixGeneration::stat));
        register("extra",new Group(c->c.item.weapon()?12.75:0,c->{
            if(!c.item.weapon()||c.item.affixes().stream().filter(a->a.operation.equals("EXTRA")&&!a.fixed).count()>=2)return List.of();
            return AffixDefinitions.current().affixes.values().stream().filter(d->d.active()&&d.group().equals("random")&&d.operation().equals("EXTRA")&&(c.duplicates||c.item.affixes().stream().noneMatch(a->a.operation.equals("EXTRA")&&a.element==d.element()))).toList();
        },(c,d,roll,random)->{Affix a=instance(d);a.baseline=.1*c.item.budget();a.baselineMaximum=.2*c.item.budget();a.roll=roll;a.value=a.baseline*roll;a.maximum=a.baselineMaximum*roll;return a;}));
        register("skill_family",new Group(c->c.item.weapon()?15:0,c->{
            if(!c.item.weapon())return List.of();
            return AffixDefinitions.current().affixes.values().stream().filter(d->d.id().startsWith("family_")&&d.operation().equals("SKILL_FAMILY")&&d.active()&&(c.duplicates||c.item.affixes().stream().noneMatch(a->a.family.equals(d.id().substring(7).toUpperCase(Locale.ROOT))))).toList();
        },(c,d,roll,random)->{Affix a=instance(d);a.family=d.id().substring(7).toUpperCase(Locale.ROOT);a.rank=1+random.nextInt(3);a.fixed=true;return a;}));
    }
    private AffixGeneration(){}
    /** Enumerates actual eligible content without consuming random draws or constructing affix instances. */
    public static List<AffixDefinition> candidates(Item item,boolean duplicates){
        var result=new LinkedHashMap<String,AffixDefinition>();var context=new Context(item,duplicates);
        for(var group:GROUPS.values()){
            double weight=group.weight.applyAsDouble(context);if(!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("Invalid affix group weight");
            if(weight>0)for(var definition:group.candidates.apply(context))if(definition.active())result.putIfAbsent(definition.id(),definition);
        }
        return List.copyOf(result.values());
    }
    private static Affix instance(AffixDefinition definition){Affix a=new Affix();a.instanceId=UUID.randomUUID().toString();a.id=definition.id();a.stat=definition.stat()==null?"":definition.stat().name();a.operation=definition.operation();a.element=definition.element();return a;}
    private static Affix stat(Context context,AffixDefinition definition,double roll,Random random){
        Affix a=instance(definition);a.baseline=context.item.baseline(a);a.roll=roll;a.value=a.baseline*roll;
        if(a.stat.equals("ATTACK_SPEED")){a.value=AttackSpeedRoll.sample(context.item.level(),roll);a.baseline=AttackSpeedRoll.sample(context.item.level(),.8);a.baselineMaximum=AttackSpeedRoll.sample(context.item.level(),1.2);}return a;
    }
    public static Affix sample(Item item,boolean duplicates,Random random){
        Context context=new Context(item,duplicates);
        record Choice(Group group,List<AffixDefinition> definitions,double weight){}
        List<Choice> choices=new ArrayList<>();double total=0;
        for(Group group:GROUPS.values()){var candidates=group.candidates.apply(context);double weight=candidates.isEmpty()?0:group.weight.applyAsDouble(context);if(!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("Invalid affix group weight");if(weight>0){choices.add(new Choice(group,candidates,weight));total+=weight;}}
        if(total==0)throw new IllegalArgumentException("没有合格词条");
        double choice=random.nextDouble()*total;Choice selected=choices.getLast();
        for(Choice candidate:choices){if(choice<candidate.weight){selected=candidate;break;}choice-=candidate.weight;}
        double roll=.8+random.nextDouble()*.4;
        var definition=selected.definitions.get(random.nextInt(selected.definitions.size()));
        return selected.group.factory.create(context,definition,roll,random);
    }
}
