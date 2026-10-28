package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import java.util.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;

/** Original voxel meshes: every career registers its presentation beside the behavior content. */
final class CareerSkillVisuals {
    @FunctionalInterface private interface Drawing {void draw(SkillVfx.Track track,long age,long now,List<Quad> out);}
    private CareerSkillVisuals(){}
    private static void add(String id,Drawing draw){var content=CareerSkills.content(id);SkillVisualRegistry.register(id,new SkillVisualRegistry.Handler(){
        public boolean persistentEnd(SkillVisualEvent event){return CareerSkills.persistent(id)&&!event.sweep();}
        public int defaultColor(){return content.color();}
        public long defaultDuration(){return (long)(content.skill().parameters(1).getOrDefault("duration",1d)*1000);}
        public void render(SkillVfx.LevelRenderContext context,SkillVfx.Track track,long age,long now,List<Quad> out,Vec3 camera){draw.draw(track,age,now,out);}
        public void play(SkillVisualEvent e){if(e.phase()!=SkillVisualEvent.Phase.START&&e.phase()!=SkillVisualEvent.Phase.FINISH&&e.phase()!=SkillVisualEvent.Phase.RELEASE)return;var level=Minecraft.getInstance().level;if(level==null)return;Vec3 p=vector(e.position());var sound=e.phase()==SkillVisualEvent.Phase.FINISH?SoundEvents.GENERIC_EXPLODE.value():content.element()==3?SoundEvents.SOUL_ESCAPE.value():SoundEvents.PLAYER_ATTACK_SWEEP;level.playLocalSound(p.x,p.y,p.z,sound,SoundSource.PLAYERS,.65f,e.phase()==SkillVisualEvent.Phase.FINISH?.7f:1.1f,false);}
    });}
    static void install(){
        add(CareerSkills.FIELD,(t,a,n,q)->field(q,vector(t.start.origin()),Math.max(1,(int)Math.round(t.start.charge()*15)),n));
        add(CareerSkills.KEIGA,(t,a,n,q)->ghost(q,vector(t.start.origin()).add(0,.4,0),0x8855cc,120));
        add(CareerSkills.SOUL_SLASH,(t,a,n,q)->arc(q,vector(t.last.position()).add(0,1,0),vector(t.start.direction()),3,0xad67e0,170));
        add(CareerSkills.TOMBSTONE,(t,a,n,q)->{
            Vec3 p=vector(t.start.origin());ring(q,p.add(0,.04,0),4,.08,0x80629e,160);Random random=new Random(t.start.seed());
            for(int i=0;i<16;i++){double angle=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*3.8;double fall=((a+i*113)%600)/600d;Vec3 b=p.add(Math.cos(angle)*r,4.5*(1-fall),Math.sin(angle)*r);box(q,b,.28,.8,.18,0x4c405b,220);box(q,b.add(0,.57,-.02),.4,.10,.23,0x9f82bb,230);}
        });
        add(CareerSkills.PHANTOM,(t,a,n,q)->{Vec3 from=vector(t.start.origin()),end=vector(t.last.position());beam(q,from.add(0,.7,0),end.add(0,.7,0),.2,0x8c4bd0,170);for(int i=0;i<4;i++)ghost(q,from.lerp(end,i/4d),0x8e59c9,70+i*20);});
        add(CareerSkills.KALLA,(t,a,n,q)->{
            var player=Minecraft.getInstance().level.getPlayerByUUID(t.start.caster());Vec3 p=player==null?vector(t.start.origin()):player.position();
            if(t.start.sweep()){arc(q,p.add(vector(t.start.direction()).scale(2.4)).add(0,1.1,0),vector(t.start.direction()),3,0x9645d6,210);return;}
            ghost(q,p.add(0,1.1,0),0x472560,150);flame(q,p.add(.45,1.3,0),.3,0xa65bdf,200);
        });
        add(CareerSkills.BLACHE,(t,a,n,q)->{
            Vec3 p=vector(t.start.origin());ring(q,p.add(0,.04,0),8,.25,0x4e236d,170);disc(q,p.add(0,.03,0),7.8,0x160e22,150);
            double rise=Math.sin(Math.min(1,a/2000d)*Math.PI)*4;Vec3 head=p.add(vector(t.start.direction()).scale(2)).add(0,rise-1,0);
            box(q,head,2.5,1.9,2.7,0x231330,240);box(q,head.add(0,1.65,0),2.1,.55,2.4,0x402248,240);box(q,head.add(0,.2,-1.45),2.4,.55,.35,0x100c18,250);
            for(int i=0;i<8;i++)box(q,head.add(-1.9+i*.55,.65,-1.8),.12,.7,.12,0xd1c1e5,245);
            box(q,head.add(-1.15,1.25,-2.2),.4,.25,.12,0x9eeaf3,255);box(q,head.add(1.15,1.25,-2.2),.4,.25,.12,0x9eeaf3,255);
        });
        add(CareerSkills.BRANDS,(t,a,n,q)->ring(q,vector(t.start.origin()).add(0,1.4,0),1,.06,0xe8d391,190));
        add(CareerSkills.ORB,(t,a,n,q)->{Vec3 p=vector(t.last.position());if(t.last.phase()==SkillVisualEvent.Phase.FINISH){ring(q,p.add(0,-.6,0),Math.min(2,(n-t.last.at())/120d),.12,0xd4c0ed,160);return;}box(q,p,.4,.7,.4,0xa17dc8,100);ring(q,p.add(0,-.35,0),.75,.07,0xe6d3ff,210);ring(q,p.add(0,.35,0),.75,.07,0xbd8ce7,210);});
        add(CareerSkills.CRESCENT,(t,a,n,q)->{Vec3 p=vector(t.last.position());arc(q,p.add(0,1,0),vector(t.start.direction()),2,0xf2e9c0,210);});
        add(CareerSkills.ICE,(t,a,n,q)->{
            Vec3 p=vector(t.last.position()),d=vector(t.start.direction()),side=new Vec3(-d.z,0,d.x);if(t.last.phase()!=SkillVisualEvent.Phase.TRAIL)p=vector(t.start.origin());
            for(int i=0;i<7;i++){Vec3 v=p.add(d.scale(i*.35)).add(side.scale((i%3-1)*.6));box(q,v,.18,.45+(i%3)*.25,.18,0x5db7df,190);box(q,v.add(0,.55,0),.09,.28,.09,0xc3f9ff,230);}
        });
        add(CareerSkills.FIRE,(t,a,n,q)->{Vec3 p=vector(t.last.position()),d=vector(t.start.direction()),side=new Vec3(-d.z,0,d.x);for(int i=0;i<9;i++)flame(q,p.add(d.scale((i/3)*.6)).add(side.scale((i%3-1)*.8)),.35,0xfa7f29,210);});
        add(CareerSkills.AGNI,(t,a,n,q)->{
            Vec3 p=vector(t.start.origin());ring(q,p.add(0,.05,0),5,.15,0xe8bc59,170);
            for(int i=0;i<5;i++){double angle=i*Math.PI*2/5+a/180d;flame(q,p.add(Math.cos(angle)*3,1.1,Math.sin(angle)*3),.38,0xffb33d,240);}
            ring(q,p.add(0,1.1,0),3,.08,0xffcf70,120);
        });
        add(CareerSkills.EYE,(t,a,n,q)->{
            Vec3 p=vector(t.start.origin());var actor=Minecraft.getInstance().level.getPlayerByUUID(t.start.caster());if(!t.start.sweep()){if(actor!=null)ring(q,actor.position().add(0,.1,0),2,.08,0xb28ace,140);return;}
            box(q,p.add(0,2,0),2.5,.8,.18,0xe6d5ff,220);box(q,p.add(0,1.9,-.2),.45,1,.25,0x4b2569,240);box(q,p.add(0,2,-.48),.16,.65,.1,0xffd96d,255);ring(q,p.add(0,.1,0),Math.min(8,a/100d),.22,0xc9a5ef,170);
        });
        add(CareerSkills.FRENZY,(t,a,n,q)->ring(q,vector(t.start.origin()).add(0,.1,0),1.2,.12,0xe2324c,160));
        add(CareerSkills.CRASH,(t,a,n,q)->impact(q,vector(t.last.position()),3,a,0xd9473e));
        add(CareerSkills.HAND,(t,a,n,q)->{Vec3 p=vector(t.start.origin()).add(vector(t.start.direction()).scale(1.3)).add(0,1,0);box(q,p,.45,.35,.35,0xb02b47,210);for(int i=0;i<4;i++)box(q,p.add(-.3+i*.2,.2,.3),.07,.35,.08,0xff7583,230);});
        add(CareerSkills.RAGE,(t,a,n,q)->{Vec3 p=vector(t.start.origin());ring(q,p.add(0,.03,0),4,.13,0xd6334a,170);for(int i=0;i<10;i++){double v=i*Math.PI/5;flame(q,p.add(Math.cos(v)*2.4,0,Math.sin(v)*2.4),.36,0xd92c46,200);}});
        add(CareerSkills.BLADE,(t,a,n,q)->{Vec3 p=vector(t.start.origin()),d=vector(t.start.direction());beam(q,p.add(0,1,0),p.add(d.scale(5)).add(0,1,0),.25,0xec4b61,210);if(t.last.phase()==SkillVisualEvent.Phase.FINISH)impact(q,vector(t.last.position()),3,a,0xd92c46);});
        add(CareerSkills.ERUPTION,(t,a,n,q)->impact(q,vector(t.last.position()),6,a,0xf06c3c));
        add(CareerSkills.SWORD,(t,a,n,q)->{
            Vec3 p=vector(t.start.origin());if(!t.start.sweep())return;
            double drop=Math.max(0,4-a/100d);sword(q,p.add(0,drop,0),6,0xb6193b,245);ring(q,p.add(0,.07,0),6,.2,0xe64a52,190);for(int i=0;i<12;i++){double angle=i*Math.PI/6;flame(q,p.add(Math.cos(angle)*4,0,Math.sin(angle)*4),.55,0xd63745,210);}
        });
    }
    static void transformed(SkillVfx.Track track,long age,List<Quad> out){
        if(track.start.color()!=CareerRules.EYE_COLOR)return;
        Vec3 p=vector(track.last.position()).add(0,1.1,0),d=vector(track.start.direction()).multiply(1,0,1).normalize();
        if(track.start.skill().equals("ATTACK")){arc(out,p,d,2.6,0xe9dfff,190);arc(out,p.add(0,.25,0),d,2.2,CareerRules.EYE_COLOR,130);}
        else {ring(out,p.add(0,-.7,0),1.6,.1,CareerRules.EYE_COLOR,145);box(out,p,.8,.15,.12,0xf3e8ff,190);box(out,p.add(0,-.2,-.15),.12,.55,.10,0x522b7c,220);}
    }
    static void actors(List<Quad> out,long now){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        for(var actor:CareerClient.INSTANCE.view.actors){var p=mc.level.getPlayerByUUID(UUID.fromString(actor.owner));if(p==null||!p.isAlive())continue;Vec3 at=p.position();Vec3 d=p.getLookAngle().multiply(1,0,1).normalize(),side=new Vec3(-d.z,0,d.x);
            if(actor.keiga>0)ghost(out,at.subtract(d.scale(.45)).add(0,.25,0),0x8052ac,90);
            for(int i=0;i<actor.brands;i++){double angle=now/900d+i*Math.PI*2/5;Vec3 rune=at.add(Math.cos(angle)*1.1,1.6+.08*Math.sin(angle*2),Math.sin(angle)*1.1);box(out,rune,.08,.22,.08,0xf1d584,230);box(out,rune.add(0,.08,0),.18,.05,.05,0xffefbb,230);}
            if(actor.frenzy){sword(out,at.add(side.scale(-.42)).add(d.scale(.2)).add(0,.55,0),1.15,0xe63951,190);flame(out,at.add(side.scale(.4)).add(0,1,0),.12,0xe23250,120);}
            if(actor.kallaUntil>now)ghost(out,at.subtract(d.scale(.6)).add(0,1,0),0x4e2769,125);
            if(actor.eyeUntil>now)ring(out,at.add(0,.08,0),1.8,.06,0xbe9ce5,110);
            if(actor.bloodUntil>now)sword(out,at.subtract(d.scale(.5)).add(0,.7,0),2.5+actor.blood*.2,0xa92441,180);
        }
        CareerClient.INSTANCE.particles(out,now);
    }
    private static void field(List<Quad> q,Vec3 p,int rank,long now){
        int count=CareerSkills.ghosts(rank);int[] colors={0xd35c55,0x63b06d,0x7bd5ee,0xad6ad7};disc(q,p.add(0,.025,0),5,0x281936,35);
        for(int i=0;i<count;i++){ring(q,p.add(0,.04+i*.012,0),5-i*.13,.075,colors[i],175);double a=i*Math.PI/2+Math.PI/4;Vec3 place=p.add(Math.cos(a)*3.4,.2+.15*Math.sin(now/500d+i),Math.sin(a)*3.4);ghost(q,place,colors[i],150);beam(q,p.add(0,.06,0),place.add(0,-.15,0),.04,colors[i],80);}
        for(int i=0;i<12;i++){double a=i*Math.PI/6;Vec3 r=p.add(Math.cos(a)*4.45,.055,Math.sin(a)*4.45);box(q,r,.09,.035,.22,colors[0],130);}
    }
    private static void impact(List<Quad> q,Vec3 p,double radius,long age,int color){ring(q,p.add(0,.05,0),Math.min(radius,.5+age/130d),.15,color,180);for(int i=0;i<8;i++){double angle=i*Math.PI/4;Vec3 end=p.add(Math.cos(angle)*radius,.06,Math.sin(angle)*radius);beam(q,p.add(0,.06,0),end,.08,color,160);}for(int i=0;i<6;i++){double a=i*Math.PI/3;flame(q,p.add(Math.cos(a)*radius*.65,0,Math.sin(a)*radius*.65),.35,color,170);}}
    static void flame(List<Quad> q,Vec3 p,double radius,int color,int alpha){box(q,p,radius,radius*2,radius,color,alpha);box(q,p.add(radius*.2,radius*1.7,0),radius*.5,radius*1.6,radius*.5,0xffca8b,alpha/2);}
    private static void ghost(List<Quad> q,Vec3 p,int color,int alpha){box(q,p,.27,.8,.18,color,alpha);box(q,p.add(0,.8,0),.33,.48,.24,color,alpha);box(q,p.add(-.4,.4,0),.12,.6,.12,color,alpha/2);box(q,p.add(.4,.4,0),.12,.6,.12,color,alpha/2);box(q,p.add(-.13,1,-.26),.06,.05,.025,0xe7edf2,alpha);box(q,p.add(.13,1,-.26),.06,.05,.025,0xe7edf2,alpha);}
    private static void sword(List<Quad> q,Vec3 p,double height,int color,int alpha){box(q,p,.11,height,.07,color,alpha);box(q,p.add(0,height*.12,0),.42,.1,.12,0xf7a3a3,alpha);box(q,p.add(0,height*.87,0),.055,height*.15,.045,0xffb8b3,alpha);}
    private static void arc(List<Quad> q,Vec3 p,Vec3 d,double radius,int color,int alpha){Vec3 side=new Vec3(-d.z,0,d.x);for(int i=0;i<20;i++){double a=-Math.PI*.8+i*Math.PI*1.6/20,b=a+Math.PI*1.6/20;Vec3 x=p.add(d.scale(Math.cos(a)*radius)).add(0,Math.sin(a)*radius*.6,0),y=p.add(d.scale(Math.cos(b)*radius)).add(0,Math.sin(b)*radius*.6,0);beam(q,x,y,.15,color,alpha);}}
    private static void beam(List<Quad> q,Vec3 a,Vec3 b,double width,int color,int alpha){Vec3 direction=b.subtract(a),side=new Vec3(-direction.z,0,direction.x);if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);side=side.normalize().scale(width);q.add(new Quad(a.add(side),b.add(side),b.subtract(side),a.subtract(side),color,alpha));q.add(new Quad(a.add(0,width,0),b.add(0,width,0),b.add(0,-width,0),a.add(0,-width,0),color,alpha));}
    private static void disc(List<Quad> q,Vec3 p,double radius,int color,int alpha){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;Vec3 x=p.add(Math.cos(a)*radius,0,Math.sin(a)*radius),y=p.add(Math.cos(b)*radius,0,Math.sin(b)*radius);q.add(new Quad(p,x,y,p,color,alpha));}}
    private static void ring(List<Quad> q,Vec3 p,double radius,double width,int color,int alpha){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;Vec3 x=p.add(Math.cos(a)*radius,0,Math.sin(a)*radius),y=p.add(Math.cos(b)*radius,0,Math.sin(b)*radius),u=p.add(Math.cos(b)*(radius-width),0,Math.sin(b)*(radius-width)),v=p.add(Math.cos(a)*(radius-width),0,Math.sin(a)*(radius-width));q.add(new Quad(x,y,u,v,color,alpha));}}
    private static void box(List<Quad> q,Vec3 p,double w,double h,double d,int color,int alpha){Vec3 a=p.add(-w,0,-d),b=p.add(w,0,-d),c=p.add(w,0,d),e=p.add(-w,0,d),up=new Vec3(0,h,0);q.add(new Quad(a,b,b.add(up),a.add(up),color,alpha));q.add(new Quad(b,c,c.add(up),b.add(up),color,alpha));q.add(new Quad(c,e,e.add(up),c.add(up),color,alpha));q.add(new Quad(e,a,a.add(up),e.add(up),color,alpha));q.add(new Quad(a.add(up),b.add(up),c.add(up),e.add(up),color,alpha));q.add(new Quad(e,c,b,a,color,alpha));}
}
