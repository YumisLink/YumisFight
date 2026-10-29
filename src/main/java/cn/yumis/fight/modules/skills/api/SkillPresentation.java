package cn.yumis.fight.modules.skills.api;

import java.util.*;
import java.util.function.ToLongFunction;

/** Content metadata shared by cast scheduling and visual lifetime validation. */
public final class SkillPresentation {
    public record Traits(boolean charging,boolean fixedActionTime,boolean fixedVisualTime,boolean invulnerable,long segmentDelay,long segmentWindow,int color,boolean overdriveColor,ToLongFunction<SkillCast> duration){}
    private static final Map<SkillDefinition,Traits> CONTENT=new LinkedHashMap<>();
    static {
        register(SkillCatalog.BLINK,new Traits(false,true,true,false,0,0,0xb4f2ff,false,c->100));
        register(SkillCatalog.GHOST,new Traits(true,false,false,false,0,0,0x8E56D9,false,c->3800));
        register(SkillCatalog.TRIPLE,new Traits(false,false,false,false,200,1200,0xB4F2FF,true,c->(long)(1200*c.parameters.get("slashes",3)+500)));
        register(SkillCatalog.WAVE,new Traits(false,false,false,false,0,0,0xB4F2FF,false,c->1800));
        register(SkillCatalog.MASTERY,new Traits(false,false,false,false,0,0,0xB4F2FF,false,c->1800));
        register(SkillCatalog.OVERDRIVE,new Traits(false,false,true,false,0,0,0xB4F2FF,false,c->30000));
        register(SkillCatalog.RISING,new Traits(false,false,false,false,0,0,0xB4F2FF,true,c->1800));
        register(SkillCatalog.DRAW,new Traits(false,false,false,false,0,0,0xB4F2FF,true,c->1800));
        register(SkillCatalog.DRAGON,new Traits(false,false,false,false,250,1050,0xB4F2FF,true,c->(long)(1050*c.parameters.get("slashes",4)+500)));
        register(SkillCatalog.DANCE,new Traits(false,false,false,false,0,0,0xB4F2FF,true,c->(long)(c.parameters.get("duration",1.25)*1000+600)));
        register(SkillCatalog.STORM,new Traits(false,true,true,true,0,0,0xB4F2FF,true,c->(long)(c.parameters.get("duration",8)*1000)));
    }
    private SkillPresentation(){}
    public static void register(SkillDefinition skill,Traits traits){if(CONTENT.putIfAbsent(skill,Objects.requireNonNull(traits))!=null)throw new IllegalArgumentException("Duplicate presentation: "+skill);}
    public static Traits require(SkillDefinition skill){var value=CONTENT.get(skill);if(value==null){var content=CareerSkills.content(skill.id);if(content!=null){boolean landing=skill.id.equals(CareerSkills.CRASH)||skill.id.equals(CareerSkills.ERUPTION);return new Traits(content.charging(),false,landing||CareerSkills.persistent(skill.id),content.invulnerable(),0,0,content.color(),false,c->landing?30000:(long)(c.parameters.get("actionDuration",c.parameters.get("duration",1))*1000));}}if(value==null)throw new IllegalArgumentException("Missing skill presentation: "+skill);return value;}
    public static boolean fixedActionTime(SkillDefinition skill){return require(skill).fixedActionTime();}
    public static boolean invulnerable(SkillDefinition skill){return require(skill).invulnerable();}
    public static boolean charging(SkillDefinition skill){return require(skill).charging();}
    public static boolean segmented(SkillDefinition skill){return require(skill).segmentWindow()>0;}
    public static long segmentDelay(SkillDefinition skill){return require(skill).segmentDelay();}
    public static long segmentWindow(SkillDefinition skill){return require(skill).segmentWindow();}
    public static long duration(SkillCast cast){
        // The synchronized actor draws the full 60-second preparation without extending visual transport clocks.
        if(cast.skill.id.equals(CareerSkills.SWORD)&&!cast.terminal)return 1000;
        if(CareerSkills.contains(cast.skill.id)&&cast.terminal)return (long)(cast.parameters.get("actionDuration",1)*1000/cast.speed);
        var traits=require(cast.skill);long value=traits.duration().applyAsLong(cast);return traits.fixedVisualTime()?value:(long)(value/cast.speed);
    }
    public static double visualSpeed(SkillCast cast){if(CareerSkills.contains(cast.skill.id)&&cast.terminal)return cast.speed;return require(cast.skill).fixedVisualTime()?1:cast.speed;}
    public static int color(SkillDefinition skill,boolean overdrive){var t=require(skill);return overdrive&&t.overdriveColor()?0xFFD368:t.color();}
}
