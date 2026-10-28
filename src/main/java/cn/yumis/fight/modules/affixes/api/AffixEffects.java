package cn.yumis.fight.modules.affixes.api;
import cn.yumis.fight.modules.attributes.api.Stat;

import java.util.*;
import java.util.function.*;

/** Effect registration belongs to affixes. Equipment only supplies accumulation ports. */
public final class AffixEffects {
    public interface ItemSink {void flat(Stat stat,double value);void percent(Stat stat,double value);}
    public interface CharacterSink {
        void extra(Affix affix);
        void family(String family,int rank);
        void percent(Stat stat,double value);
        void modifier(String id,double value,boolean product);
        void parameter(String source,String skill,String parameter,double add,double multiply);
    }
    @FunctionalInterface public interface CharacterEffect {void apply(Affix affix,String source,CharacterSink sink);}
    private static final Map<String,BiConsumer<Affix,ItemSink>> ITEMS=new LinkedHashMap<>();
    private static final Map<String,CharacterEffect> CHARACTERS=new LinkedHashMap<>();
    static {
        registerItem("FLAT",(a,s)->stat(a).ifPresent(k->s.flat(k,a.value)));
        registerItem("PERCENT",(a,s)->stat(a).ifPresent(k->s.percent(k,a.value)));
        registerItem("ALL_RESIST_FLAT",(a,s)->{for(Stat k:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST))s.flat(k,a.value);});
        registerCharacter("EXTRA",(a,id,s)->{if(a.element>=0&&a.element<4)s.extra(a);});
        registerCharacter("SKILL_FAMILY",(a,id,s)->s.family(a.family,a.rank));
        registerCharacter("CHARACTER_PERCENT",(a,id,s)->stat(a).ifPresent(k->s.percent(k,a.value)));
        registerCharacter("MODIFIER",(a,id,s)->s.modifier(a.id,a.value,false));
        registerCharacter("SKILL_ATTACK",(a,id,s)->s.modifier("skillProduct",1+a.value,true));
        registerCharacter("SKILL_PARAMETER",(a,id,s)->s.parameter(id+"/"+a.id,a.skill,a.parameter,a.value,a.roll));
    }
    private AffixEffects(){}
    public static Optional<Stat> stat(Affix a){try{return Optional.of(Stat.valueOf(a.stat));}catch(IllegalArgumentException|NullPointerException unknown){return Optional.empty();}}
    public static synchronized void registerItem(String operation,BiConsumer<Affix,ItemSink> effect){if(ITEMS.putIfAbsent(operation,Objects.requireNonNull(effect))!=null)throw new IllegalArgumentException("Duplicate item effect: "+operation);}
    public static synchronized void registerCharacter(String operation,CharacterEffect effect){if(CHARACTERS.putIfAbsent(operation,Objects.requireNonNull(effect))!=null)throw new IllegalArgumentException("Duplicate character effect: "+operation);}
    public static void item(Affix affix,ItemSink sink){var handler=ITEMS.get(affix.operation);if(handler!=null)handler.accept(affix,sink);}
    public static void character(Affix affix,String source,CharacterSink sink){var handler=CHARACTERS.get(affix.operation);if(handler!=null)handler.apply(affix,source,sink);}
}
