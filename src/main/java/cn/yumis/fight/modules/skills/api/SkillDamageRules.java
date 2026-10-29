package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.attributes.api.Stats;
public final class SkillDamageRules {
    private SkillDamageRules(){}
    public record SkillDamage(double total,double main,double[] components){
        public SkillDamage {components=components.clone();}
        @Override public double[] components(){return components.clone();}
    }
    /** Shared post-hit bonuses, preserving combat's calculation order and main/extra distinction. */
    public static SkillDamage skillDamage(CombatRules.Damage parts,Stats stats,boolean basicSkill,double mastery,boolean basic,boolean passive,double levelMultiplier){
        double damage=parts.total()*levelMultiplier,main=parts.main()*levelMultiplier;
        double[] components=parts.components();for(int i=0;i<components.length;i++)components[i]*=levelMultiplier;
        if(basicSkill){
            double bonus=main*stats.modifiers.getOrDefault("basicSkillMain",0d);
            damage+=bonus;main+=bonus;
            components[parts.element()+1]+=bonus;
        }
        if(!basic&&!passive&&stats.weaponType.equals("GREATSWORD")){
            double multiplier=1+.2*mastery;
            damage*=multiplier;main*=multiplier;
            for(int i=0;i<components.length;i++)components[i]*=multiplier;
        }
        return new SkillDamage(damage,main,components);
    }
}
