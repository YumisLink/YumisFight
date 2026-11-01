package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Per-move cuboid particles, casting runes, blades and projectile/impact geometry. */
final class SummonMoveVisuals {
    private SummonMoveVisuals(){}
    static boolean draw(SkillVfx.Track track,long time,List<Quad> out){
        String tag=track.summonMove;if(tag.isEmpty())return false;
        boolean flight=tag.endsWith("_flight"),impact=tag.endsWith("_impact");String id=tag.replace("_flight","").replace("_impact","");
        SummonMoves.Move move=SummonMoves.find(id);if(move==null)return false;Vec3 p=SkillVfx.vector(track.last.position()),dir=SkillVfx.vector(track.last.direction()).normalize();long elapsed=Math.max(0,time-track.start.startedAt());
        boolean ended=track.last.phase()==SkillVisualEvent.Phase.FINISH;int color=track.last.color();
        if(flight){
            if(ended){burst(out,p,Math.min(2.8,.15+(time-track.last.at())*.005),color,Math.max(0,220-(int)((time-track.last.at())*.5)));return true;}
            if(id.equals("water_bolt")){cube(out,p,.21,0x70cde8,225);cube(out,p.subtract(dir.scale(.13)),.12,0xd3faff,210);for(int i=2;i<5;i++)cube(out,p.subtract(dir.scale(i*.1)),.05,0x9cdeed,160-i*25);}
            else if(id.equals("sword_force")){blade(out,p,dir,.85,.075,0xe9dcb8,240);blade(out,p.subtract(dir.scale(.25)),dir,.6,.12,0x91c5e0,120);}
            else if(id.equals("king_wave")){crescent(out,p,dir,.55,0xa8eaff,230);crescent(out,p.subtract(dir.scale(.15)),dir,.4,0xe5faff,150);}
            else if(id.equals("ice_needle")){blade(out,p,dir,.65,.07,0x99efff,240);for(int i=1;i<5;i++)cube(out,p.subtract(dir.scale(i*.14)),.08,0xc8faff,150-i*25);}
            else if(id.equals("bomb")){cube(out,p,.24,0x37393a,255);cube(out,p.add(0,.16,0),.07,0xffe186,255);}
            else if(id.equals("stone")||id.equals("triple_throw")){cube(out,p,.18,0x929887,255);cube(out,p.add(.045,.04,-.045),.1,0xc3c1a2,255);}
            else {double r=id.equals("meteor")?.65:.28;cube(out,p,r,0xb65c31,255);cube(out,p.subtract(dir.scale(r*.2)),r*.65,0xffcf73,255);for(int i=1;i<=5;i++)cube(out,p.subtract(dir.scale(r*.5+i*.12)).add(Math.sin(i+elapsed*.02)*.06,0,0),r*(.7-i*.09),0xff7430,175-i*22);}
            return true;
        }
        if(impact){
            if(id.equals("king_lightning")){int alpha=Math.max(0,230-(int)(elapsed*.6));column(out,p.add(0,-.8,0),.07,3.5,0xc6efff,alpha);spark(out,p.add(0,2.7,0),p.add(0,-.4,0),.16,time,0x8bd5ff,alpha);burst(out,p,.45,0xc3eeff,alpha);}
            else if(id.equals("king_laser")){beam(out,p,dir,move.range(),Math.max(0,180-(int)(elapsed*.5)));}
            else if(id.equals("flame_double")){burst(out,p.add(dir.scale(.4)),.3,0xffb655,Math.max(0,190-(int)(elapsed*.5)));}
            else if(id.equals("shadow_claw")){claws(out,p.add(dir.scale(.5)),dir,Math.max(0,210-(int)(elapsed*.6)));}
            else if(id.equals("light_contact")){spark(out,p,p.add(dir.scale(.65)),.12,time,0xc8f6ff,Math.max(0,230-(int)(elapsed*.6)));}
            else if(id.equals("ice_touch")||id.equals("element_touch")){burst(out,p.add(dir.scale(.35)),.28,color,Math.max(0,190-(int)(elapsed*.5)));}
            else if(id.equals("ice_pillar")){double fade=Math.max(0,1-elapsed/500d);for(int i=0;i<5;i++){double x=(i-2)*.3;column(out,p.add(x,-.55,Math.sin(i)*.2),.18,Math.min(1.8,elapsed*.014)*fade,0x8de9ff,(int)(220*fade));}ring(out,p.add(0,-.5,0),1.1,color,(int)(170*fade));}
            else if(id.equals("falling_swords")){for(int i=0;i<5;i++)blade(out,p.add((i-2)*.4,Math.max(0,1-elapsed/300d)*3,0).add(dir.scale(3)),new Vec3(0,-1,0),1.5,.1,0xffe1a4,220);}
            else if(move.shape().equals("line")||move.shape().equals("dash")||move.shape().equals("melee")||move.shape().equals("cone"))slash(out,p,dir,Math.min(move.range(),3),color,Math.max(0,230-(int)(elapsed*.6)),elapsed/500d);
            else burst(out,p,Math.min(move.radius(),elapsed*.005),color,Math.max(0,200-(int)(elapsed*.4)));
            return true;
        }
        double u=Math.clamp(elapsed/(double)Math.max(1,track.start.duration()),0,1),contact=move.contacts()[0]/(double)move.duration();
        if(id.startsWith("ice")||id.equals("meteor")||id.startsWith("king")){
            ring(out,p.add(0,-1.2,0),.5+.12*Math.sin(u*Math.PI),color,150);
            if(u<contact){cube(out,p.add(0,.15,0),.06+.12*u,0xe2faff,220);for(int i=0;i<4;i++){double a=i*Math.PI/2+u*4;cube(out,p.add(Math.cos(a)*.3,.1,Math.sin(a)*.3),.055,color,150);}}
        }
        if(id.equals("king_laser")){if(u>=contact)beam(out,p,dir,move.range(),190);else {cube(out,p.add(dir.scale(.25)),.12+u*.3,0xbcecff,220);}}
        else if(id.equals("king_lightning")&&u>=contact){for(int i=0;i<3;i++)cube(out,p.add(Math.sin(i*2+time*.01)*.2,.3+Math.cos(i+time*.01)*.2,0),.04,0xcaf5ff,160);}
        else if(id.equals("flame_double")){for(int hit:move.contacts()){double d=Math.abs(u-hit/(double)move.duration());if(d<.13)burst(out,p.add(dir.scale(.3)),.15+(.13-d)*2,0xffb64e,170);}}
        else if(id.equals("shadow_claw")){for(int hit:move.contacts())if(Math.abs(u-hit/(double)move.duration())<.12)claws(out,p.add(dir.scale(.4)),dir,180);}
        else if(id.equals("light_contact")&&Math.abs(u-contact)<.2)spark(out,p,p.add(dir.scale(.65)),.1,time,0xd4f9ff,210);
        else if(id.equals("falling_swords")){for(int i=0;i<5;i++)blade(out,p.add((i-2)*.4,(1-u)*3,0).add(dir.scale(3)),new Vec3(0,-1,0),1.5,.08,color,200);}
        else if(id.equals("ghost_dance")){for(int i=0;i<3;i++)slash(out,p.add(dir.scale(i*.7)),dir,1.2+i*.3,color,(int)(170*Math.sin(u*Math.PI)),(u*4+i*.2)%1);}
        else if(u>contact-.12&&u<contact+.24&&(move.shape().equals("line")||move.shape().equals("dash")||move.shape().equals("melee")||move.shape().equals("cone")))slash(out,p,dir,Math.min(2.5,move.range()),color,190,(u-contact+.12)/.36);
        return true;
    }
    private static void slash(List<Quad> out,Vec3 p,Vec3 dir,double radius,int color,int alpha,double progress){
        Vec3 forward=dir.multiply(1,0,1).normalize();if(forward.lengthSqr()<.1)forward=new Vec3(0,0,1);Vec3 right=forward.cross(new Vec3(0,1,0));
        for(int i=0;i<16;i++){double a=-1.25+i*.15,b=a+.15;Vec3 aa=p.add(forward.scale(Math.cos(a)*radius)).add(right.scale(Math.sin(a)*radius)),bb=p.add(forward.scale(Math.cos(b)*radius)).add(right.scale(Math.sin(b)*radius));out.add(new Quad(aa,bb,bb.add(0,.06+progress*.06,0),aa.add(0,.06+progress*.06,0),color,Math.max(0,alpha-i*4)));}
    }
    private static void crescent(List<Quad> out,Vec3 p,Vec3 dir,double radius,int color,int alpha){
        Vec3 side=dir.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize();Vec3 up=side.cross(dir).normalize();
        for(int i=0;i<18;i++){double a=-Math.PI*.7+i*Math.PI*1.4/18,b=a+Math.PI*1.4/18;Vec3 aa=side.scale(Math.cos(a)).add(up.scale(Math.sin(a))),bb=side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));out.add(new Quad(p.add(aa.scale(radius)),p.add(bb.scale(radius)),p.add(bb.scale(radius*.75)),p.add(aa.scale(radius*.75)),color,alpha));}
    }
    private static void beam(List<Quad> out,Vec3 p,Vec3 direction,double range,int alpha){
        Vec3 dir=direction.multiply(1,0,1).normalize();if(dir.lengthSqr()<.01)dir=new Vec3(0,0,1);var mc=net.minecraft.client.Minecraft.getInstance();Vec3 end=p.add(dir.scale(range));
        if(mc.level!=null&&mc.player!=null){var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(p,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=hit.getLocation();}
        Vec3 side=dir.cross(new Vec3(0,1,0)).scale(.14),up=new Vec3(0,.14,0);out.add(new Quad(p.add(side),p.subtract(side),end.subtract(side),end.add(side),0x73c3ff,alpha));out.add(new Quad(p.add(up),p.subtract(up),end.subtract(up),end.add(up),0xc2edff,alpha));
        side=side.scale(.3);out.add(new Quad(p.add(side),p.subtract(side),end.subtract(side),end.add(side),0xf0ffff,Math.min(255,alpha+35)));
    }
    private static void spark(List<Quad> out,Vec3 from,Vec3 to,double spread,long time,int color,int alpha){for(int i=0;i<10;i++){double t=i/9d;cube(out,from.lerp(to,t).add(Math.sin(i*3+time*.04)*spread,Math.cos(i*2+time*.04)*spread,0),.045,color,alpha);}}
    private static void claws(List<Quad> out,Vec3 p,Vec3 dir,int alpha){Vec3 side=dir.cross(new Vec3(0,1,0)).normalize();for(int i=-1;i<=1;i++)blade(out,p.add(side.scale(i*.13)).add(0,.22,0),new Vec3(side.x*.4,-.75,side.z*.4).normalize(),.6,.025,0xb48ae5,alpha);}
    private static void ring(List<Quad> out,Vec3 p,double radius,int color,int alpha){for(int i=0;i<24;i++){double a=i*Math.PI/12;cube(out,p.add(Math.cos(a)*radius,0,Math.sin(a)*radius),i%3==0?.07:.035,color,alpha);}}
    private static void burst(List<Quad> out,Vec3 p,double radius,int color,int alpha){ring(out,p.add(0,-.3,0),Math.max(.05,radius),color,alpha);for(int i=0;i<12;i++){double a=i*Math.PI/6;cube(out,p.add(Math.cos(a)*radius,Math.sin(i*2.2)*radius*.5,Math.sin(a)*radius),.06,color,alpha);}}
    private static void blade(List<Quad> out,Vec3 p,Vec3 direction,double length,double width,int color,int alpha){Vec3 side=direction.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize().scale(width);Vec3 end=p.add(direction.scale(length));out.add(new Quad(p.add(side),p.subtract(side),end.subtract(side.scale(.2)),end.add(side.scale(.2)),color,alpha));Vec3 other=direction.cross(side).normalize().scale(width);out.add(new Quad(p.add(other),p.subtract(other),end.subtract(other.scale(.2)),end.add(other.scale(.2)),0xfff5cf,alpha));}
    private static void column(List<Quad> out,Vec3 p,double width,double height,int color,int alpha){Vec3 a=p.add(-width,0,-width),b=p.add(width,0,-width),c=p.add(width,0,width),d=p.add(-width,0,width),up=new Vec3(0,height,0);out.add(new Quad(a,b,b.add(up),a.add(up),color,alpha));out.add(new Quad(b,c,c.add(up),b.add(up),color,alpha));out.add(new Quad(c,d,d.add(up),c.add(up),color,alpha));out.add(new Quad(d,a,a.add(up),d.add(up),color,alpha));out.add(new Quad(a.add(up),b.add(up),c.add(up),d.add(up),0xd4f8ff,alpha));}
    private static void cube(List<Quad> out,Vec3 p,double size,int color,int alpha){if(alpha<=0||size<=0)return;double r=size/2;Vec3[] v={p.add(-r,-r,-r),p.add(r,-r,-r),p.add(r,r,-r),p.add(-r,r,-r),p.add(-r,-r,r),p.add(r,-r,r),p.add(r,r,r),p.add(-r,r,r)};for(int[] f:new int[][]{{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}})out.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color,alpha));}
}
