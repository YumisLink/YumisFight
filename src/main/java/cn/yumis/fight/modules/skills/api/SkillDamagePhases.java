package cn.yumis.fight.modules.skills.api;

import java.util.*;

/** Content-declared contact types, independent of the skill's equipment/rank category. */
public final class SkillDamagePhases {
    public record Phase(boolean magic,boolean dot){}
    private static final Map<String,Phase> PHASES=new LinkedHashMap<>();
    private SkillDamagePhases(){}
    public static void register(String skill,String phase,boolean magic,boolean dot){if(PHASES.putIfAbsent(skill+"/"+phase,new Phase(magic,dot))!=null)throw new IllegalArgumentException("Duplicate damage phase");}
    public static Phase require(SkillDefinition skill,String phase){var value=PHASES.get(skill.id+"/"+phase);if(value==null)throw new IllegalArgumentException("未登记的技能伤害阶段");return value;}
}
