package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import java.util.*;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.BasicAttackProfile;
public final class WeaponSkills {
    private WeaponSkills(){}
    public static boolean mastery(Progress p,Stats s){return p.hasJob(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER)&&SkillLearning.effective(s,p,SkillCatalog.MASTERY)>0;}
    public static double interval(Progress p,Stats s){
        double interval=650/Math.max(.01,1+s.get(Stat.ATTACK_SPEED)/100);
        return Math.max(50,interval*(mastery(p,s)&&s.weaponType.equals("GREATSWORD")?2:1));
    }
    public static SkillParameters.Snapshot skill(SkillParameters.Snapshot input,Progress p,Stats s){
        Map<String,Double> values=new LinkedHashMap<>(input.values());
        values.put("meleeScale",WeaponTraits.sword(s.weaponType)&&input.skill()!=SkillCatalog.WAVE&&input.skill()!=SkillCatalog.STORM?1.5:1);
        if(mastery(p,s)){
            if(input.skill()==SkillCatalog.TRIPLE)values.merge("slashes",s.weaponType.equals("KATANA")||s.weaponType.equals("LIGHTSABER")?4d:2d,Double::sum);
            if(input.skill()==SkillCatalog.DRAGON&&s.weaponType.equals("LIGHTSABER"))values.merge("slashes",1d,Double::sum);
            if(input.skill()==SkillCatalog.DANCE&&(s.weaponType.equals("KATANA")||s.weaponType.equals("LIGHTSABER")))values.merge("slashes",10d,Double::sum);
        }
        return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
    }
    public static BasicAttackProfile basicAttack(Progress progress,Stats stats){return new BasicAttackProfile(interval(progress,stats),Math.max((int)(double)stats.modifiers.getOrDefault("basicSlashes",1d),mastery(progress,stats)&&stats.weaponType.equals("LIGHTSABER")?2:1));}
}
