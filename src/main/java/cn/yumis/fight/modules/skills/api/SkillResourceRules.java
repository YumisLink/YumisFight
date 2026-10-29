package cn.yumis.fight.modules.skills.api;

/** Content timing shared by preview, client scheduling and server validation. */
public final class SkillResourceRules {
    public static final long ORB_INTERVAL=350,BLOOD_INTERVAL=500;
    public static final int BLOOD_CAP=20;
    private SkillResourceRules(){}
    public static int agniHits(int brands){return (int)Math.floor(3*Math.clamp(brands,1,5)/.85);}
    public static double agniInterval(int brands){return .85/Math.clamp(brands,1,5);}
}
