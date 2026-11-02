package cn.yumis.fight.modules.skills.api;

/** Connection/life-scoped heart resources, owned by the skills module. */
public final class SunMoonState {
    public int heart,stacks,harmony,paradox;
    public long revision;
    public SunMoonState copy(){var value=new SunMoonState();value.heart=heart;value.stacks=stacks;value.harmony=harmony;value.paradox=paradox;value.revision=revision;return value;}
}
