package cn.yumis.fight.modules.character.api;

/** Facts supplied by module ports; character owns the profile/ticket eligibility policy. */
public record ProfileConditions(boolean alive,boolean inDungeon,boolean inTraining,boolean casting,boolean cooling) {
    public void requireReady(){
        if(!alive)throw new IllegalArgumentException("死亡时不能操作职业配置或升级券");
        if(inDungeon)throw new IllegalArgumentException("请先退出副本");
        if(inTraining)throw new IllegalArgumentException("请先结束训练");
        if(casting)throw new IllegalArgumentException("请先结束施法或动作");
        if(cooling)throw new IllegalArgumentException("所有技能冷却结束后才能操作");
    }
}
