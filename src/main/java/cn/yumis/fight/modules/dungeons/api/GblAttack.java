package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** GBL-owned world-space telegraph and trajectory; contains no damage calculation or health writer. */
public final class GblAttack {
    public String id=UUID.randomUUID().toString(),instance="",kind="",shape="";
    public UUID source;
    public long started,windup,active;
    public double x,y,z,ex,ey,ez,radius=1,width=.6,halfAngle=Math.PI,coefficient=1;
    public boolean magic,remove;
    public int element=-1;
    public Vec3 origin(){return new Vec3(x,y,z);}
    public void origin(Vec3 value){x=value.x;y=value.y;z=value.z;}
    public Vec3 destination(){return new Vec3(ex,ey,ez);}
    public long release(){return started+windup;}
    public long end(){return release()+active;}
    public double progress(long now){return Math.clamp((now-release())/(double)Math.max(1,active),0,1);}
    public Vec3 position(long now){
        double t=progress(now);Vec3 p=origin().lerp(destination(),t);
        return shape.equals("LOB")?p.add(0,4*Math.sin(Math.PI*t),0):p;
    }
    public double waveRadius(long now){return radius*progress(now);}
}
