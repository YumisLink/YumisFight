package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Frozen, dungeon-owned attack geometry. Rendering and server contacts use this same contract. */
public final class SkasaAttack {
    public String id=UUID.randomUUID().toString(),instance="",kind="";
    public UUID source;
    public int cell,area,wave,side=-1;
    public long started;
    public double x,y,z,dx,dz=-1;
    public boolean remove;
    public SkasaMoves move(){return SkasaMoves.valueOf(kind);}
    public Vec3 origin(){return new Vec3(x,y,z);}
    public Vec3 direction(){return new Vec3(dx,0,dz).normalize();}
    public long release(){return started+move().windup;}
    public long end(){return release()+move().active;}
    public long contactAt(int pulse){return release()+pulse*move().interval;}
    public int pulse(long now){return move().hits==1?0:(int)Math.clamp((now-release())/move().interval,0,move().hits-1);}
    public boolean contacting(long now){return now>=release()&&now<=end()&&(move().travels()||now-contactAt(pulse(now))<=move().contactWindow());}
    public int side(int pulse){return move()==SkasaMoves.TRIPLE&&pulse%2==1?-side:side;}
    public Vec3 position(long now){return origin().add(direction().scale(move().range*Math.clamp((now-release())/(double)move().active,0,1)));}
    public boolean safeBreath(Vec3 p){double u=Math.abs(p.x-cell*512);return u>=2.3&&u<=6&&p.z>=146&&p.z<=151;}
    public boolean inArena(Vec3 p){return Math.abs(p.x-cell*512)<=19.3&&p.z>=120.7&&p.z<=151.3;}
    public boolean contains(Vec3 p,int pulse,long previous,long now){
        Vec3 offset=p.subtract(origin());
        if(Math.abs(offset.y)>2.6)return false;
        return switch(move()){
            case SLAM,TRIPLE -> inArena(p)&&(p.x-cell*512)*side(pulse)>=-.3;
            case CLAW -> fan(p,new Vec3(side*.70710678,0,-.70710678),move().range,Math.PI/4+.04);
            case ROAR -> fan(p,direction(),move().range,Math.PI/3);
            case BREATH -> !safeBreath(p)&&fan(p,direction(),move().range,Math.toRadians(75));
            case ROUTE_BREATH -> strip(p,3,move().range);
            case NASRA_CYCLONE,VORTEX -> offset.horizontalDistanceSqr()<=Math.pow(move().range+.3,2);
            case NASRA_KICK,HATCHLING_CHARGE,ICE_LINE -> {
                Vec3 from=position(Math.max(release(),previous)),to=position(now),delta=to.subtract(from);
                double t=delta.lengthSqr()<.00001?0:Math.clamp(p.subtract(from).dot(delta)/delta.lengthSqr(),0,1);
                yield p.subtract(from.add(delta.scale(t))).horizontalDistanceSqr()<=Math.pow(move()==SkasaMoves.ICE_LINE?.75:1.35,2);
            }
            default -> fan(p,direction(),move().range,Math.PI/3);
        };
    }
    private boolean fan(Vec3 p,Vec3 direction,double radius,double half){
        Vec3 offset=p.subtract(origin()).multiply(1,0,1);double distance=offset.length();
        return distance<=radius+.3&&(distance<.3||offset.normalize().dot(direction)>=Math.cos(half));
    }
    private boolean strip(Vec3 p,double halfWidth,double length){
        Vec3 offset=p.subtract(origin()).multiply(1,0,1),direction=direction();
        double forward=offset.dot(direction),cross=offset.x*direction.z-offset.z*direction.x;
        return forward>=-.3&&forward<=length+.3&&Math.abs(cross)<=halfWidth+.3;
    }
}
