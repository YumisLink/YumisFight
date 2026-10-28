package cn.yumis.fight.modules.skills.api;

/** A complete dance keeps its configured duration, even when equipment adds slashes. */
public record SkillSchedule(int slashes,double intervalMillis,double durationMillis) {
    public static SkillSchedule dance(SkillParameters.Snapshot parameters){
        int count=Math.max(1,(int)parameters.get("slashes",10));
        double duration=Math.max(0,parameters.get("duration",1.25)*1000);
        double interval=Math.max(0,Math.min(parameters.get("interval",.125)*1000,duration/count));
        return new SkillSchedule(count,interval,duration);
    }
    public int due(long elapsedMillis){
        if(elapsedMillis<0)return 0;
        if(intervalMillis<=0||elapsedMillis>=durationMillis)return slashes;
        return Math.min(slashes,1+(int)Math.floor(elapsedMillis/intervalMillis));
    }
}
