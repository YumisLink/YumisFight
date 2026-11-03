package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import java.util.*;
import java.util.function.Supplier;

/** Skill content may replace the mana display without changing the underlying attribute formulas. */
public final class PrimaryResource {
    public record View(String title,double current,double maximum,int color,List<String> help) {
        public View {help=List.copyOf(help);}
    }
    private static final Map<String,Supplier<View>> VIEWS=new LinkedHashMap<>();
    private PrimaryResource(){}
    public static void register(String baseJob,Supplier<View> view){
        if(VIEWS.putIfAbsent(baseJob,view)!=null)throw new IllegalArgumentException("Duplicate primary resource: "+baseJob);
    }
    /** Null means the character uses the ordinary mana display. */
    public static View current(){
        var view=VIEWS.get(JobCatalog.base(SkillsClient.current().progress().jobId()));
        return view==null?null:view.get();
    }
}
