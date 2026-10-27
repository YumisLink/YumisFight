package cn.yumis.fight.rules;

import java.util.*;
import cn.yumis.fight.api.SkillParameters;
import static cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;

/** Shared weapon balance, attack clock and fixed mastery effects. */
public final class WeaponRules {
    private WeaponRules(){}
    public static double attackScale(Weapon weapon,Stat stat){
        boolean physical=stat==Stat.PHYSICAL_ATTACK;
        return switch(weapon){case KATANA->physical?.8:1;case LIGHTSABER->physical?1:.8;case SHORTSWORD->physical?.8:1.4;case BLUNT->1.2;case GREATSWORD->physical?1.4:.8;};
    }
    public static double speed(String weapon){return switch(weapon){case "KATANA"->10;case "LIGHTSABER"->20;case "GREATSWORD"->-10;default->0;};}
    public static boolean sword(String weapon){return Set.of("KATANA","LIGHTSABER","SHORTSWORD","GREATSWORD").contains(weapon);}
    public static boolean mastery(Progress p,Stats s){return p.swordmaster&&s.effective(p,Skill.MASTERY)>0;}
    public static double interval(Progress p,Stats s){
        double interval=650/Math.max(.01,1+s.get(Stat.ATTACK_SPEED)/100);
        return Math.max(50,interval*(mastery(p,s)&&s.weaponType.equals("GREATSWORD")?2:1));
    }
    public static double speedAffix(int level,double roll){
        double t=Math.clamp((level-1)/79d,0,1),q=Math.clamp((roll-.8)/.4,0,1);
        return 3+7*t+(2+3*t)*q;
    }
    public static SkillParameters.Snapshot skill(SkillParameters.Snapshot input,Progress p,Stats s){
        Map<String,Double> values=new LinkedHashMap<>(input.values());
        values.put("meleeScale",sword(s.weaponType)&&input.skill()!=Skill.WAVE&&input.skill()!=Skill.STORM?1.5:1);
        if(mastery(p,s)){
            if(input.skill()==Skill.TRIPLE)values.merge("slashes",s.weaponType.equals("KATANA")||s.weaponType.equals("LIGHTSABER")?4d:2d,Double::sum);
            if(input.skill()==Skill.DRAGON&&s.weaponType.equals("LIGHTSABER"))values.merge("slashes",1d,Double::sum);
            if(input.skill()==Skill.DANCE&&(s.weaponType.equals("KATANA")||s.weaponType.equals("LIGHTSABER")))values.merge("slashes",10d,Double::sum);
        }
        return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
    }
}
