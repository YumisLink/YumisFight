package cn.yumis.fight.modules.character.api;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.*;
/** Existing character growth applied after item sources. */
public final class CharacterAttributes {
    private CharacterAttributes(){}
    public static double baseMana(Progress p){
        var a=CharacterBalanceRules.current().baseAttributes;
        return a.mpBase()+a.mpPerLevel()*(p.level-1);
    }
    public static void apply(Progress p,Stats s){
        var a=CharacterBalanceRules.current().baseAttributes;
        double n=p.level-1;
        s.add(Stat.HEALTH,a.healthBase()+a.healthPerLevel()*n);
        s.add(Stat.MP,baseMana(p));
        s.add(Stat.STRENGTH,a.strengthBase()+p.strength);
        s.add(Stat.INTELLIGENCE,a.intelligenceBase()+p.intelligence);
        s.add(Stat.VITALITY,a.vitalityBase()+a.vitalityPerLevel()*n+p.vitality);
        s.add(Stat.SPIRIT,a.spiritBase()+a.spiritPerLevel()*n+p.spirit);
        s.add(Stat.PHYSICAL_ATTACK,a.physicalAttackBase()+a.physicalAttackPerLevel()*n);
        s.add(Stat.MAGICAL_ATTACK,a.magicalAttackBase()+a.magicalAttackPerLevel()*n);
        s.add(Stat.PHYSICAL_DEFENSE,a.physicalDefenseBase());
        s.add(Stat.MAGICAL_DEFENSE,a.magicalDefenseBase());
        s.add(Stat.STAMINA,a.staminaBase());s.add(Stat.STAMINA_RECOVERY,a.staminaRecoveryBase());s.add(Stat.SHIELD_COOLDOWN_RECOVERY,a.shieldCooldownRecoveryBase());s.add(Stat.SHIELD_REGENERATION,a.shieldRegenerationBase());
        s.add(Stat.MOVEMENT_SPEED,a.movementSpeedBase());s.add(Stat.CAST_SPEED,a.castSpeedBase());s.add(Stat.MANA_REGENERATION,a.manaRegenerationBase());s.add(Stat.COOLDOWN_RECOVERY,a.cooldownRecoveryBase());s.add(Stat.EXPERIENCE_GAIN,a.experienceGainBase());
    }
}
