package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Palace-owned frozen geometry and presentation timeline, never a health writer. */
public final class PalaceAttack {
    public String id=UUID.randomUUID().toString(),group="",instance="",kind="",shape="";
    public UUID source;
    public long started,windup,active;
    public double x,y,z,ex,ey,ez,radius=1,width=1,halfAngle=Math.PI,coefficient=1;
    public int element=-1;
    public boolean magic,remove,cancelled;
    public Vec3 origin(){return new Vec3(x,y,z);}
    public void origin(Vec3 p){x=p.x;y=p.y;z=p.z;}
    public Vec3 destination(){return new Vec3(ex,ey,ez);}
    public void destination(Vec3 p){ex=p.x;ey=p.y;ez=p.z;}
    public long release(){return started+windup;}
    public long end(){return release()+active;}
    public double progress(long now){return Math.clamp((now-release())/(double)Math.max(1,active),0,1);}
    public Vec3 position(long now){double t=progress(now);Vec3 p=origin().lerp(destination(),t);return shape.equals("BOMB")?p.add(0,3*Math.sin(Math.PI*t),0):p;}
}
