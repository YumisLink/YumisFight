package cn.yumis.fight.modules.skills.api;

/** Connection/life-scoped heart resources, owned by the skills module. */
public final class SunMoonState {
    public int heart,stacks,harmony,paradox;
    public long revision;
    public long eclipseUntil,moonHaloUntil,leylineUntil;
    public int sunCasts,sunfireSparks;
    public double leylineX,leylineY,leylineZ;
    public String leylineDimension="",leylineInstance="";
    public SunMoonState copy(){
        var value=new SunMoonState();
        value.heart=heart;value.stacks=stacks;value.harmony=harmony;value.paradox=paradox;value.revision=revision;
        value.eclipseUntil=eclipseUntil;value.moonHaloUntil=moonHaloUntil;value.leylineUntil=leylineUntil;
        value.sunCasts=sunCasts;value.sunfireSparks=sunfireSparks;
        value.leylineX=leylineX;value.leylineY=leylineY;value.leylineZ=leylineZ;
        value.leylineDimension=leylineDimension;value.leylineInstance=leylineInstance;
        return value;
    }
}
