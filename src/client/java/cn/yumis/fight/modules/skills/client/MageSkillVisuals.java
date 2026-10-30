package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Native three-dimensional meshes; projectiles use the synchronized source trajectory. */
final class MageSkillVisuals {
    private MageSkillVisuals(){}
    static void install(){for(String id:List.of(MageSkills.STAR,MageSkills.FANG,MageSkills.JACK,MageSkills.HERDER))SkillVisualRegistry.register(id,new SkillVisualRegistry.Handler(){
        @Override public long defaultDuration(){return 1500;}
        @Override public void render(SkillVfx.LevelRenderContext context,SkillVfx.Track track,long age,long time,List<Quad> quads,Vec3 camera){draw(id,track,age,time,quads);}
        @Override public void animate(SkillVfx.Track track,net.minecraft.world.entity.player.Player player,net.minecraft.client.renderer.entity.state.EntityRenderState state,long time){if(!id.equals(MageSkills.HERDER)&&state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid)humanoid.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;}
    });}
    private static void draw(String id,SkillVfx.Track t,long age,long time,List<Quad> out){
        Vec3 p=SkillVfx.vector(t.last.position()),direction=SkillVfx.vector(t.last.direction()).normalize();boolean finished=t.last.phase()==SkillVisualEvent.Phase.FINISH;
        if(id.equals(MageSkills.STAR)){if(!finished)star(out,p,direction,.28,0xa7eaff,Math.PI/4,Math.max(0,time-t.start.startedAt())*Math.PI/250d);else cube(out,p,.15,0xe3ffff,150);}
        else if(id.equals(MageSkills.JACK)){
            if(finished){double r=Math.min(3,Math.max(.1,(time-t.last.at())/150d));for(int i=0;i<12;i++){double a=i*Math.PI/6;cube(out,p.add(Math.cos(a)*r,Math.sin(a*2)*.3,Math.sin(a)*r),.3,0xff8a32,Math.max(0,210-(int)((time-t.last.at())*.4)));}return;}
            cube(out,p,.56,0xe97823,255);cube(out,p.add(0,.31,0),.16,0x629440,255);Vec3 front=p.add(direction.scale(.294)),right=direction.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.1)right=new Vec3(1,0,0);cube(out,front.add(right.scale(.13)).add(0,.06,0),.085,0x231829,255);cube(out,front.add(right.scale(-.13)).add(0,.06,0),.085,0x231829,255);cube(out,front.add(0,-.13,0),.13,0xffdd72,255);for(int i=1;i<=3;i++)cube(out,p.subtract(direction.scale(.3+i*.14)).add(0,Math.sin(age*.02+i)*.04,0),.23-i*.04,i==1?0xffc55e:0xf76226,190-i*35);
        }else if(id.equals(MageSkills.HERDER)){if(!finished){cube(out,p,.22,0x979b99,255);cube(out,p.add(.045,.06,-.02),.15,0xc0c0b4,255);}}
        else {Vec3 origin=SkillVfx.vector(t.start.origin());for(int i=0;i<10;i++){double distance=i*.5;cube(out,origin.add(direction.scale(distance)),.18-i*.01,0xd9f4df,210-i*12);}star(out,origin.add(direction.scale(5)),direction,.18,0xf9edbf);}
    }
    private static void star(List<Quad> out,Vec3 center,Vec3 direction,double radius,int color){
        star(out,center,direction,radius,color,0,0);
    }
    private static void star(List<Quad> out,Vec3 center,Vec3 direction,double radius,int color,double tilt,double rotation){
        Vec3 right=direction.cross(new Vec3(0,1,0));if(right.lengthSqr()<.01)right=new Vec3(1,0,0);right=right.normalize();Vec3 normal=direction.scale(Math.cos(tilt)).add(right.cross(direction).normalize().scale(Math.sin(tilt))),up=right.cross(normal).normalize(),depth=normal.scale(.075);var points=new Vec3[10];for(int i=0;i<10;i++){double angle=Math.PI/2+i*Math.PI/5+rotation,r=i%2==0?radius:radius*.42;points[i]=center.add(right.scale(Math.cos(angle)*r)).add(up.scale(Math.sin(angle)*r));}
        for(int i=0;i<10;i++){Vec3 a=points[i],b=points[(i+1)%10];out.add(new Quad(center.add(depth),a.add(depth),b.add(depth),center.add(depth),0xe4fbff,255));out.add(new Quad(center.subtract(depth),b.subtract(depth),a.subtract(depth),center.subtract(depth),color,255));out.add(new Quad(a.add(depth),a.subtract(depth),b.subtract(depth),b.add(depth),color,255));}
    }
    private static void cube(List<Quad> out,Vec3 p,double size,int color,int alpha){if(alpha<=0)return;double r=size/2;Vec3[] v={p.add(-r,-r,-r),p.add(r,-r,-r),p.add(r,r,-r),p.add(-r,r,-r),p.add(-r,-r,r),p.add(r,-r,r),p.add(r,r,r),p.add(-r,r,r)};for(int[] f:new int[][]{{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}})out.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color,alpha));}
}
