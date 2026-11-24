package cn.yumis.fight.modules.character.api;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.*;
/** Existing character growth applied after item sources. */
public final class CharacterAttributes {
    private CharacterAttributes(){}
    public static double baseMana(Progress p){return 80+10d*(p.level-1);}
    public static void apply(Progress p,Stats s){
        double n=p.level-1;
        s.add(Stat.HEALTH,100+20*n);
        s.add(Stat.MP,baseMana(p));
        s.add(Stat.STRENGTH,20+p.strength);
        s.add(Stat.INTELLIGENCE,15+p.intelligence);
        s.add(Stat.SPIRIT,10+p.spirit);
        s.add(Stat.PHYSICAL_ATTACK,5+n);
        s.add(Stat.MAGICAL_ATTACK,5+n);
        s.add(Stat.PHYSICAL_DEFENSE,20);
        s.add(Stat.MAGICAL_DEFENSE,20);
        s.add(Stat.STAMINA,100);s.add(Stat.STAMINA_RECOVERY,1);s.add(Stat.SHIELD_COOLDOWN_RECOVERY,1);s.add(Stat.SHIELD_REGENERATION,1);
        s.add(Stat.MOVEMENT_SPEED,1);s.add(Stat.CAST_SPEED,1);s.add(Stat.MANA_REGENERATION,1);s.add(Stat.COOLDOWN_RECOVERY,1);s.add(Stat.EXPERIENCE_GAIN,1);
    }
}
