package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.*;
import java.util.*;

/** Asura content scalars are applied after the historical weight reference is resolved. */
public final class AsuraRules {
    public static final double DAMAGE_SCALE=.83,AURA_RATIO=.005,AURA_RADIUS=8,GEAR_RATIO=.02,GEAR_SPEED=3,GEAR_RANGE=10;
    public static final String GEAR_VISUAL="yumis_dungeon_and_fight:wave_eye_gear";
    public static final int GEAR_INDEX=32;
    private AsuraRules(){}
    static void install(){SkillVisualRules.register(GEAR_VISUAL,new SkillVisualRules.Policy(false,"","",false));}
    public static SkillParameters.Snapshot damage(SkillParameters.Snapshot source,Progress progress){
        if(!progress.hasJob(JobCatalog.ASURA))return source;
        var values=new LinkedHashMap<>(source.values());
        for(String key:source.skill().damageParameters())values.computeIfPresent(key,(k,v)->v*DAMAGE_SCALE);
        return new SkillParameters.Snapshot(source.skill(),source.rank(),values);
    }
}
