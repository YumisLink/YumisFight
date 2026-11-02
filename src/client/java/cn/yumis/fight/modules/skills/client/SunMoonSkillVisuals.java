package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Continuous full-bright spheres, light ribbons and moonlight volumes; no gameplay is rendered here. */
final class SunMoonSkillVisuals {
    private SunMoonSkillVisuals(){}
    static void install(){for(String id:SunMoonSkills.ALL)SkillVisualRegistry.register(id,new SkillVisualRegistry.Handler(){
        @Override public long defaultDuration(){return 2600;}
        @Override public long finishedUntil(SkillVfx.Track track,SkillVisualEvent event){return event.at()+(id.equals(SunMoonSkills.MOON)?2000:id.equals(SunMoonSkills.STARFALL)?600:900);}
        @Override public void render(SkillVfx.LevelRenderContext context,SkillVfx.Track track,long age,long time,List<Quad> quads,Vec3 camera){draw(id,track,age,time,quads);}
        @Override public void play(SkillVisualEvent event){if(id.equals(SunMoonSkills.CORONA)&&event.phase()==SkillVisualEvent.Phase.FINISH)CoronaBlur.flash(SkillVfx.vector(event.position()));}
    });}
    static void actors(List<Quad> out,long time){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        for(var actor:CareerClient.INSTANCE.view.actors){if(actor.sunMoonHeart==0||actor.sunMoonStacks<=0)continue;var player=mc.level.getPlayerByUUID(UUID.fromString(actor.owner));if(player==null||!player.isAlive())continue;
            Vec3 center=PlayerVisualAnchor.position(player).add(0,player.getBbHeight()+.42,0);int n=Math.clamp(actor.sunMoonStacks,1,3),color=actor.sunMoonHeart==1?0xffb34b:0x9ddcff;
            for(int i=0;i<n;i++){double a=time*.0018+i*Math.PI*2/n;Vec3 p=center.add(Math.cos(a)*.48,Math.sin(a*1.7)*.09,Math.sin(a)*.48);sphere(out,p,.12,color,230,10,5);sphere(out,p,.17,color,45,10,5);}
        }
        var skill=ChantClient.skill();if(ChantClient.active()&&skill!=null&&skill.groundTargeted&&mc.player!=null){
            try{var aim=SkillTargeting.ground(mc.player,ChantClient.range(),e->e.isAlive()&&(e instanceof net.minecraft.world.entity.monster.Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon));ring(out,aim.point().add(0,.03,0),ChantClient.radius(),.045,skill.aspect==SkillDefinition.Aspect.SUN?0xffb967:0xb2ddff,150,time*.001);}
            catch(IllegalArgumentException ignored){}
        }
    }
    private static void draw(String id,SkillVfx.Track t,long age,long time,List<Quad> out){
        Vec3 point=SkillVfx.vector(t.last.position()),origin=SkillVfx.vector(t.start.position());boolean finished=t.last.phase()==SkillVisualEvent.Phase.FINISH;
        long after=finished?Math.max(0,time-t.last.at()):0;
        if(id.equals(SunMoonSkills.MOON)){
            if(!finished){sphere(out,point,.28,0xb8e8ff,230,16,8);ring(out,point,.42,.04,0xc7efff,160,time*.006);Vec3 direction=SkillVfx.vector(t.last.direction()).normalize();for(int i=1;i<7;i++)sphere(out,point.subtract(direction.scale(i*.13)),.13-i*.012,0x7cbdff,140-i*17,8,4);}
            else{double fade=Math.clamp(1-after/2000d,0,1),r=1.5*Math.clamp(after/180d,.15,1);sphere(out,point,r,0xbcdfff,(int)(210*fade),24,12);sphere(out,point.add(-.26*r,.2*r,-.45*r),r*.35,0x677fb7,(int)(90*fade),12,6);ring(out,point.add(0,-r*.8,0),3*Math.min(1,after/220d),.09,0xb9e9ff,(int)(180*fade),time*.001);}
            return;
        }
        if(id.equals(SunMoonSkills.REFLUX)){double fade=Math.clamp(1-age/800d,0,1);for(int i=0;i<3;i++){double a=i*Math.PI*2/3+age*.006;Vec3 p=origin.add(Math.cos(a)*(.5+age*.001),1+Math.sin(a)*.2,Math.sin(a)*(.5+age*.001));sphere(out,p,.16,0xc6eaff,(int)(230*fade),12,6);}ring(out,origin.add(0,.08,0),.8+age*.001,.07,0xb3d4ff,(int)(180*fade),0);return;}
        if(id.equals(SunMoonSkills.CORONA)){
            if(!finished){double u=Math.clamp(age/800d,0,1);Vec3 center=origin.add(0,1.5+.5*u,0);sphere(out,center,2,0xff8e32,220,28,14);sphere(out,center,1.86,0xffd96e,245,24,12);for(int i=0;i<3;i++)ring(out,center.add(0,(i-1)*.65,0),Math.sqrt(4-Math.pow((i-1)*.65,2)),.08,0xffe6ab,190,time*.002+i);ring(out,origin.add(0,.04,0),4,.05,0xffbe66,130,time*.0007);}
            else explosion(out,point,4,0xff9c42,after,900);
            return;
        }
        if(id.equals(SunMoonSkills.PARADOX)){
            if(!finished){double u=Math.clamp(age/600d,0,1);Vec3 base=t.last.phase()==SkillVisualEvent.Phase.TRAIL?point:origin;for(int side=0;side<2;side++){int color=side==0?0x87d8ff:0xff6579;for(int i=0;i<30;i++){double a=u-i*.009;if(a<0)continue;Vec3 a0=helix(base,a,side),a1=helix(base,Math.max(0,a-.009),side);ribbon(out,a0,a1,.035+i*.0004,color,Math.max(0,230-i*7));}Vec3 head=helix(base,u,side);sphere(out,head,.16,0xffffff,240,10,5);sphere(out,head,.24,color,135,12,6);}}
            else {explosion(out,point.add(0,.45,0),1,0x9cdfff,after,800);ring(out,point.add(0,.2,0),Math.min(1,after/130d),.12,0xff7490,(int)(210*Math.clamp(1-after/800d,0,1)),time*.005);}
            return;
        }
        if(id.equals(SunMoonSkills.STARFALL)){
            double fade=finished?Math.clamp(1-after/600d,0,1):1;Vec3 ground=origin.add(0,.03,0);double lower=age<250?8*(1-age/250d):0;
            column(out,ground.add(0,lower,0),.75,Math.max(.1,8-lower),0xc8e7ff,(int)(95*fade));column(out,ground.add(0,lower,0),.24,Math.max(.1,8-lower),0xf1faff,(int)(155*fade));ring(out,ground,4,.08,0xc8eaff,(int)(160*fade),time*.0007);
            for(int i=0;i<16;i++){double a=i*2.39996,r=Math.sqrt((i+.5)/16d)*3.5;double y=8-Math.floorMod(age+i*97,1250)/1250d*8;Vec3 star=ground.add(Math.cos(a)*r,y,Math.sin(a)*r);sphere(out,star,.09,0xe6f7ff,(int)(210*fade),8,4);ribbon(out,star,star.add(0,.55,0),.022,0xbce4ff,(int)(120*fade));}
            return;
        }
        if(id.equals(SunMoonSkills.SUN)&&!finished){double u=Math.clamp(age/350d,0,1);Vec3 from=origin.add(SkillVfx.vector(t.start.direction()));Vec3 p=from.lerp(origin,u).add(0,Math.sin(u*Math.PI)*1.3,0);sphere(out,p,.52,0xffb85a,230,18,9);sphere(out,p,.31,0xfff1bc,255,14,7);ring(out,origin.add(0,.03,0),3,.04,0xffb85a,100,0);return;}
        if(id.equals(SunMoonSkills.FLARE)&&!finished){ring(out,origin.add(0,.04,0),4,.09,0xffb457,180,time*.001);return;}
        if(finished)explosion(out,point,id.equals(SunMoonSkills.FLARE)?4:3,0xffa04f,after,900);
    }
    private static Vec3 helix(Vec3 base,double u,int side){double a=u*Math.PI*5+side*Math.PI,r=.75*(1-.5*u);return base.add(Math.cos(a)*r,3*(1-u)+.25,Math.sin(a)*r);}
    private static void explosion(List<Quad> out,Vec3 p,double radius,int color,long age,long duration){double fade=Math.clamp(1-age/(double)duration,0,1),r=radius*Math.clamp(age/180d,.06,1);ring(out,p.add(0,.05,0),r,.15,color,(int)(230*fade),age*.0008);sphere(out,p.add(0,.4,0),Math.max(.1,r*.6),color,(int)(90*fade),20,10);for(int i=0;i<14;i++){double a=i*Math.PI/7;Vec3 base=p.add(Math.cos(a)*r*.6,0,Math.sin(a)*r*.6);column(out,base,.13+fade*.14,(.7+radius*.6)*fade*(.6+.4*Math.sin(i*2.17)),color,(int)(180*fade));}}
    static void ring(List<Quad> out,Vec3 center,double radius,double width,int color,int alpha,double rotation){if(alpha<=0||radius<=0)return;for(int i=0;i<48;i++){double a=rotation+i*Math.PI/24,b=rotation+(i+1)*Math.PI/24;Vec3 p=center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),q=center.add(Math.cos(b)*radius,0,Math.sin(b)*radius),r=center.add(Math.cos(b)*(radius+width),0,Math.sin(b)*(radius+width)),s=center.add(Math.cos(a)*(radius+width),0,Math.sin(a)*(radius+width));out.add(new Quad(p,q,r,s,color,alpha));out.add(new Quad(s,r,q,p,color,alpha));}}
    private static void ribbon(List<Quad> out,Vec3 a,Vec3 b,double width,int color,int alpha){if(alpha<=0)return;Vec3 normal=b.subtract(a).cross(new Vec3(0,1,0));if(normal.lengthSqr()<.001)normal=new Vec3(1,0,0);normal=normal.normalize().scale(width);out.add(new Quad(a.subtract(normal),a.add(normal),b.add(normal),b.subtract(normal),color,alpha));out.add(new Quad(a.add(normal),a.subtract(normal),b.subtract(normal),b.add(normal),color,alpha));Vec3 vertical=new Vec3(0,width,0);out.add(new Quad(a.subtract(vertical),a.add(vertical),b.add(vertical),b.subtract(vertical),color,alpha));}
    private static void column(List<Quad> out,Vec3 center,double radius,double height,int color,int alpha){if(alpha<=0||height<=0)return;for(int i=0;i<20;i++){double a=i*Math.PI/10,b=(i+1)*Math.PI/10;Vec3 p=center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),q=center.add(Math.cos(b)*radius,0,Math.sin(b)*radius);out.add(new Quad(p,q,q.add(0,height,0),p.add(0,height,0),color,alpha));}}
    private static void sphere(List<Quad> out,Vec3 center,double radius,int color,int alpha,int slices,int bands){if(alpha<=0||radius<=0)return;for(int y=0;y<bands;y++){double a=-Math.PI/2+Math.PI*y/bands,b=-Math.PI/2+Math.PI*(y+1)/bands;for(int x=0;x<slices;x++){double c=Math.PI*2*x/slices,d=Math.PI*2*(x+1)/slices;out.add(new Quad(surface(center,radius,a,c),surface(center,radius,a,d),surface(center,radius,b,d),surface(center,radius,b,c),color,Math.clamp(alpha,0,255)));}}}
    private static Vec3 surface(Vec3 p,double r,double latitude,double longitude){return p.add(Math.cos(latitude)*Math.cos(longitude)*r,Math.sin(latitude)*r,Math.cos(latitude)*Math.sin(longitude)*r);}
}
