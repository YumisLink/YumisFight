package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.LightningBolt;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Per-move cuboid particles, casting runes, blades and projectile/impact geometry. */
final class SummonMoveVisuals {
    private SummonMoveVisuals(){}
    static boolean draw(SkillVfx.Track track,long time,List<Quad> out,List<SpriteQuad> sprites,Vec3 camera){
        String tag=track.summonMove;if(tag.isEmpty())return false;
        boolean flight=tag.endsWith("_flight"),impact=tag.endsWith("_impact");String id=tag.replace("_flight","").replace("_impact","");
        SummonMoves.Move move=SummonMoves.find(id);if(move==null)return false;Vec3 p=SkillVfx.vector(track.last.position()),dir=SkillVfx.vector(track.last.direction()).normalize();long elapsed=Math.max(0,time-track.start.startedAt());
        boolean ended=track.last.phase()==SkillVisualEvent.Phase.FINISH;int color=track.last.color();
        if(flight){
            if(ended){
                long after=Math.max(0,time-track.last.at());
                double u=Math.clamp(after/460d,0,0.999);
                double maxRad=Math.max(2.6,move.radius()>0?move.radius()*1.15:2.4);
                double rad=Math.min(maxRad,.25+after*(maxRad/360d));
                burst(out,p,rad,color,Math.max(0,225-(int)(after*.48)));
                EffectSprites.impactBurst(sprites,p,camera,Math.max(1.2,rad*1.15),u,color,240,id.equals("herder_cannon")||id.equals("meteor"));
                EffectSprites.shockwave(sprites,p.add(0,-.2,0),Math.max(.9,rad*1.25),u*1.2,color,(int)(220*(1-u)));
                return true;
            }
            if(id.equals("water_bolt")||id.equals("ice_burst")){
                double sz=id.equals("ice_burst")?.30:.21;
                cube(out,p,sz,0x70cde8,230);cube(out,p.subtract(dir.scale(.13)),sz*.6,0xd3faff,215);for(int i=2;i<5;i++)cube(out,p.subtract(dir.scale(i*.1)),sz*.28,0x9cdeed,165-i*25);
                RibbonTrail.push("sm:"+track.start.cast(),p,sz*.95,0x70cde8,215,230,time);
                EffectSprites.glow(sprites,p,camera,sz*2.8,0xb8f4ff,215);
            }else if(id.equals("dark_wave")){
                cube(out,p,.30,0x7f48d6,235);cube(out,p.subtract(dir.scale(.15)),.20,0xc69eff,210);
                RibbonTrail.push("sm:"+track.start.cast(),p,.26,0x9b64eb,220,250,time);
                EffectSprites.glow(sprites,p,camera,.85,0xc69eff,220);
            }else if(id.equals("herder_cannon")){
                cube(out,p,.44,0x2a2326,255);cube(out,p.subtract(dir.scale(.18)),.32,0xff8e36,245);cube(out,p.add(0,.22,0),.12,0xffe88c,255);
                RibbonTrail.push("sm:"+track.start.cast(),p,.34,0xff6a24,235,280,time);
                EffectSprites.glow(sprites,p,camera,1.35,0xffaa44,235);
            }else if(id.equals("fire_blast")||id.equals("fireball_volley")){
                double r=id.equals("fire_blast")?.32:.28;
                cube(out,p,r,0xd15226,255);cube(out,p.subtract(dir.scale(r*.2)),r*.68,0xffcf73,255);
                RibbonTrail.push("sm:"+track.start.cast(),p,r*.75,0xff7f2e,225,260,time);
                EffectSprites.glow(sprites,p,camera,r*2.4,0xffb852,230);
            }else if(id.equals("sword_force")){
                blade(out,p,dir,.85,.075,0xe9dcb8,240);blade(out,p.subtract(dir.scale(.25)),dir,.6,.12,0x91c5e0,120);
                RibbonTrail.push("sm:"+track.start.cast(),p,.22,0xe9dcb8,215,240,time);
                EffectSprites.slashArc(sprites,p,dir,0,1.25,.42,0xffe4a0,220);
            }else if(id.equals("king_wave")){
                crescent(out,p,dir,.55,0xa8eaff,230);crescent(out,p.subtract(dir.scale(.15)),dir,.4,0xe5faff,150);
                RibbonTrail.push("sm:"+track.start.cast(),p,.24,0x8ae0ff,215,240,time);
                EffectSprites.slashArc(sprites,p,dir,12,1.35,.45,0xa8eaff,230);
            }else if(id.equals("ice_needle")){
                blade(out,p,dir,.65,.07,0x99efff,240);for(int i=1;i<5;i++)cube(out,p.subtract(dir.scale(i*.14)),.08,0xc8faff,150-i*25);
                RibbonTrail.push("sm:"+track.start.cast(),p,.14,0x99efff,205,210,time);
                EffectSprites.glow(sprites,p,camera,.42,0xd0f8ff,200);
            }else if(id.equals("bomb")){
                cube(out,p,.24,0x37393a,255);cube(out,p.add(0,.16,0),.07,0xffe186,255);
                RibbonTrail.push("sm:"+track.start.cast(),p.add(0,.16,0),.12,0xffcf60,195,200,time);
                EffectSprites.glow(sprites,p.add(0,.16,0),camera,.36,0xffe186,210);
            }else if(id.equals("stone")||id.equals("triple_throw")){
                cube(out,p,.18,0x929887,255);cube(out,p.add(.045,.04,-.045),.1,0xc3c1a2,255);
                RibbonTrail.push("sm:"+track.start.cast(),p,.10,0xc3c1a2,155,170,time);
            }else{
                double r=id.equals("meteor")?.65:.28;cube(out,p,r,0xb65c31,255);cube(out,p.subtract(dir.scale(r*.2)),r*.65,0xffcf73,255);for(int i=1;i<=5;i++)cube(out,p.subtract(dir.scale(r*.5+i*.12)).add(Math.sin(i+elapsed*.02)*.06,0,0),r*(.7-i*.09),0xff7430,175-i*22);
                RibbonTrail.push("sm:"+track.start.cast(),p,r*.65,0xff7f2e,225,280,time);
                EffectSprites.glow(sprites,p,camera,r*2.0,0xffb852,225);
            }
            return true;
        }
        if(impact){
            double u=Math.clamp(elapsed/460d,0,0.999);
            if(id.equals("king_lightning")||id.equals("chain_light")){
                int alpha=Math.max(0,230-(int)(elapsed*.55));
                double rad=id.equals("chain_light")?2.5:1.8;
                column(out,p.add(0,-.8,0),.08,3.8,0xc6efff,alpha);spark(out,p.add(0,2.7,0),p.add(0,-.4,0),.18,time,0x8bd5ff,alpha);burst(out,p,.55,0xc3eeff,alpha);
                LightningBolt.bolt(sprites,p.add(0,8.0,0),p.add(0,-.5,0),camera,track.start.seed()^(elapsed/45L),10,.75,.12,0x8bd5ff,alpha,true);
                EffectSprites.impactBurst(sprites,p.add(0,-.35,0),camera,rad*1.1,u,0xa8eaff,235,true);
                EffectSprites.shockwave(sprites,p.add(0,-.4,0),rad*1.3*EffectSprites.easeOutCubic(u),u*1.2,0x8bd5ff,alpha);
            }else if(id.equals("king_laser")||id.equals("king_mega_laser")){
                boolean mega=id.equals("king_mega_laser");
                int alpha=Math.max(0,235-(int)(elapsed*(mega?.36:.5)));
                Vec3 end=beamEnd(p,dir,move.range(),mega);
                beam(out,p,dir,move.range(),Math.max(0,200-(int)(elapsed*.42)),mega?.42:.14,mega);
                EffectSprites.texturedBeam(sprites,p,end,camera,mega?1.15:.36,time,true,mega?0x8ae8ff:0x73c3ff,alpha);
                LightningBolt.helixArcs(sprites,p,end,camera,time,mega?1.05:.38,mega?4:2,mega?0xfff2a6:0xd4f8ff,(int)(alpha*.9));
                if(mega){
                    EffectSprites.glow(sprites,p,camera,2.2,0xdaf8ff,alpha);
                    EffectSprites.impactBurst(sprites,end,camera,2.8,u,0x8ae8ff,alpha,true);
                }
            }else if(id.equals("sacrifice_burst")){
                int alpha=Math.max(0,240-(int)(elapsed*.4));
                double r=4.5*EffectSprites.easeOutCubic(u);
                burst(out,p,r*.75,color,alpha);
                EffectSprites.sigil(sprites,p.add(0,-.35,0),r,time*.004,color,alpha,true);
                EffectSprites.shockwave(sprites,p.add(0,-.3,0),r*1.15,u*1.2,color,alpha);
                EffectSprites.impactBurst(sprites,p,camera,3.8,u,color,245,true);
            }else if(id.equals("sandor_warcry")){
                int alpha=Math.max(0,230-(int)(elapsed*.38));
                double r=8.0*EffectSprites.easeOutCubic(u);
                ring(out,p,r,0xffe494,alpha);
                EffectSprites.sigil(sprites,p,Math.max(2.0,r*.7),time*.003,0xffe494,alpha,true);
                EffectSprites.shockwave(sprites,p,r,u*1.2,0xffe494,alpha);
            }else if(id.equals("louise_magma")){
                int alpha=Math.max(0,180-(int)(elapsed*.04));
                EffectSprites.sigil(sprites,p.add(0,.05,0),5.6,time*.0015,0xff6b28,alpha,true);
                EffectSprites.shockwave(sprites,p.add(0,.08,0),6.0*EffectSprites.easeOutCubic(u),u,0xff9e3d,Math.max(0,200-(int)(elapsed*.35)));
            }else if(id.equals("louise_shatter")){
                int alpha=Math.max(0,235-(int)(elapsed*.55));
                burst(out,p,1.6*EffectSprites.easeOutCubic(u),0xa6f4ff,alpha);
                EffectSprites.impactBurst(sprites,p,camera,2.4,u,0x90ecff,240,true);
                EffectSprites.shockwave(sprites,p,2.5*EffectSprites.easeOutCubic(u),u*1.1,0xd8fbff,alpha);
            }else if(id.equals("flame_double")){
                int alpha=Math.max(0,190-(int)(elapsed*.5));
                burst(out,p.add(dir.scale(.4)),.3,0xffb655,alpha);
                EffectSprites.impactBurst(sprites,p.add(dir.scale(.4)),camera,1.4,u,0xff9636,225,false);
            }else if(id.equals("shadow_claw")){
                int alpha=Math.max(0,210-(int)(elapsed*.6));
                claws(out,p.add(dir.scale(.5)),dir,alpha);
                EffectSprites.slashArc(sprites,p.add(dir.scale(.5)),dir,30,1.6,u,0xb48ae5,225);
                EffectSprites.slashArc(sprites,p.add(dir.scale(.5)),dir,-30,1.5,u,0xdcc2ff,205);
            }else if(id.equals("light_contact")){
                int alpha=Math.max(0,230-(int)(elapsed*.6));
                spark(out,p,p.add(dir.scale(.65)),.12,time,0xc8f6ff,alpha);
                LightningBolt.bolt(sprites,p,p.add(dir.scale(1.1)),camera,track.start.seed()^(elapsed/40L),5,.25,.06,0xc8f6ff,alpha,false);
                EffectSprites.glow(sprites,p.add(dir.scale(.55)),camera,.75,0xe2faff,alpha);
            }else if(id.equals("ice_touch")||id.equals("element_touch")){
                int alpha=Math.max(0,190-(int)(elapsed*.5));
                burst(out,p.add(dir.scale(.35)),.28,color,alpha);
                EffectSprites.impactBurst(sprites,p.add(dir.scale(.35)),camera,1.35,u,color,220,false);
            }else if(id.equals("ice_pillar")||id.equals("ice_pillar_tri")){
                boolean tri=id.equals("ice_pillar_tri");
                double fade=Math.max(0,1-elapsed/500d);
                double scale=tri?1.8:1.0;
                for(int i=0;i<5;i++){double x=(i-2)*.3*scale;column(out,p.add(x,-.55,Math.sin(i)*.2*scale),.18*scale,Math.min(1.8*scale,elapsed*.016*scale)*fade,0x8de9ff,(int)(225*fade));}
                ring(out,p.add(0,-.5,0),1.1*scale,color,(int)(175*fade));
                EffectSprites.sigil(sprites,p.add(0,-.48,0),1.55*scale,time*.002,0x8de9ff,(int)(210*fade),true);
                EffectSprites.shockwave(sprites,p.add(0,-.45,0),1.8*scale*EffectSprites.easeOutCubic(u),u*1.1,0xc8f8ff,(int)(215*fade));
            }else if(id.equals("falling_swords")){
                for(int i=0;i<5;i++)blade(out,p.add((i-2)*.4,Math.max(0,1-elapsed/300d)*3,0).add(dir.scale(3)),new Vec3(0,-1,0),1.5,.1,0xffe1a4,220);
                Vec3 land=p.add(dir.scale(3));
                EffectSprites.sigil(sprites,land.add(0,-.4,0),2.2,time*.0025,0xffe1a4,(int)(205*(1-u*.7)),true);
                EffectSprites.impactBurst(sprites,land,camera,2.2,u,0xffe1a4,230,true);
            }else if(move.shape().equals("line")||move.shape().equals("dash")||move.shape().equals("melee")||move.shape().equals("cone")){
                slash(out,p,dir,Math.min(move.range(),3),color,Math.max(0,230-(int)(elapsed*.6)),elapsed/500d);
                EffectSprites.slashArc(sprites,p,dir,10,Math.min(move.range(),3.0),u,color,230);
            }else{
                double rad=Math.min(move.radius(),elapsed*.005);
                burst(out,p,rad,color,Math.max(0,200-(int)(elapsed*.4)));
                EffectSprites.shockwave(sprites,p.add(0,-.2,0),Math.max(.8,move.radius()*EffectSprites.easeOutCubic(u)),u*1.2,color,(int)(210*(1-u)));
                EffectSprites.impactBurst(sprites,p,camera,Math.max(1.2,move.radius()*.9),u,color,225,true);
            }
            return true;
        }
        double u=Math.clamp(elapsed/(double)Math.max(1,track.start.duration()),0,1),contact=move.contacts()[0]/(double)move.duration();
        if(id.startsWith("ice")||id.equals("meteor")||id.startsWith("king")){
            ring(out,p.add(0,-1.2,0),.5+.12*Math.sin(u*Math.PI),color,150);
            EffectSprites.sigil(sprites,p.add(0,-1.18,0),1.05+.25*Math.sin(u*Math.PI),time*.0024,color,175,false);
            if(u<contact){
                cube(out,p.add(0,.15,0),.06+.12*u,0xe2faff,220);for(int i=0;i<4;i++){double a=i*Math.PI/2+u*4;cube(out,p.add(Math.cos(a)*.3,.1,Math.sin(a)*.3),.055,color,150);}
                EffectSprites.glow(sprites,p.add(0,.15,0),camera,.45+.4*u,color,210);
            }
        }
        if(id.equals("king_laser")||id.equals("king_mega_laser")){
            boolean mega=id.equals("king_mega_laser");
            if(u>=contact){
                Vec3 end=beamEnd(p,dir,move.range(),mega);
                beam(out,p,dir,move.range(),mega?220:190,mega?.42:.14,mega);
                EffectSprites.texturedBeam(sprites,p,end,camera,mega?1.15:.36,time,true,mega?0x8ae8ff:0x73c3ff,mega?245:230);
                LightningBolt.helixArcs(sprites,p,end,camera,time,mega?1.05:.38,mega?4:2,mega?0xfff2a6:0xd4f8ff,mega?225:200);
            }else{
                cube(out,p.add(dir.scale(.25)),(mega?.24:.12)+u*(mega?.55:.3),0xbcecff,230);
                EffectSprites.glow(sprites,p.add(dir.scale(.25)),camera,(mega?1.2:.6)+u*(mega?1.1:.5),mega?0xfff2a6:0xbcecff,230);
            }
        }else if((id.equals("king_lightning")||id.equals("chain_light"))&&u>=contact){
            for(int i=0;i<3;i++)cube(out,p.add(Math.sin(i*2+time*.01)*.2,.3+Math.cos(i+time*.01)*.2,0),.04,0xcaf5ff,160);
            LightningBolt.bolt(sprites,p.add(0,3.2,0),p.add(dir.scale(1.8)),camera,track.start.seed()^(elapsed/45L),7,.45,.08,0x8bd5ff,215,true);
        }else if(id.equals("flame_double")){
            for(int hit:move.contacts()){double d=Math.abs(u-hit/(double)move.duration());if(d<.13){burst(out,p.add(dir.scale(.3)),.15+(.13-d)*2,0xffb64e,170);EffectSprites.impactBurst(sprites,p.add(dir.scale(.45)),camera,1.1,1-d/.13,0xff9636,215,false);}}
        }else if(id.equals("shadow_claw")){
            for(int hit:move.contacts())if(Math.abs(u-hit/(double)move.duration())<.12){claws(out,p.add(dir.scale(.4)),dir,180);EffectSprites.slashArc(sprites,p.add(dir.scale(.5)),dir,26,1.5,.5,0xb48ae5,220);}
        }else if(id.equals("light_contact")&&Math.abs(u-contact)<.2){
            spark(out,p,p.add(dir.scale(.65)),.1,time,0xd4f9ff,210);
            LightningBolt.bolt(sprites,p,p.add(dir.scale(1.0)),camera,track.start.seed()^(elapsed/40L),5,.22,.055,0xd4f9ff,215,false);
        }else if(id.equals("falling_swords")){
            for(int i=0;i<5;i++)blade(out,p.add((i-2)*.4,(1-u)*3,0).add(dir.scale(3)),new Vec3(0,-1,0),1.5,.08,color,200);
            EffectSprites.sigil(sprites,p.add(dir.scale(3)).add(0,-1.15,0),2.0,time*.0024,0xffe1a4,195,true);
        }else if(id.equals("ghost_dance")){
            EffectSprites.sigil(sprites,p.add(0,-1.15,0),2.6,time*.0032,color,185,true);
            for(int i=0;i<3;i++){
                double su=(u*4+i*.2)%1;
                slash(out,p.add(dir.scale(i*.7)),dir,1.2+i*.3,color,(int)(170*Math.sin(u*Math.PI)),su);
                EffectSprites.slashArc(sprites,p.add(dir.scale(i*.65)),dir,(i-1)*28,1.7+i*.3,su,color,(int)(225*Math.sin(u*Math.PI)));
            }
        }else if(u>contact-.12&&u<contact+.24&&(move.shape().equals("line")||move.shape().equals("dash")||move.shape().equals("melee")||move.shape().equals("cone"))){
            double su=Math.clamp((u-contact+.12)/.36,0,0.999);
            slash(out,p,dir,Math.min(2.5,move.range()),color,190,su);
            EffectSprites.slashArc(sprites,p,dir,-8,Math.min(2.6,move.range()),su,color,220);
        }
        return true;
    }
    private static Vec3 beamEnd(Vec3 p,Vec3 direction,double range,boolean full3d){
        Vec3 dir=full3d?direction:direction.multiply(1,0,1);dir=dir.lengthSqr()<.01?new Vec3(0,0,1):dir.normalize();var mc=net.minecraft.client.Minecraft.getInstance();Vec3 end=p.add(dir.scale(range));
        if(mc.level!=null&&mc.player!=null){var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(p,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=hit.getLocation();}
        return end;
    }
    private static void slash(List<Quad> out,Vec3 p,Vec3 dir,double radius,int color,int alpha,double progress){
        Vec3 forward=dir.multiply(1,0,1).normalize();if(forward.lengthSqr()<.1)forward=new Vec3(0,0,1);Vec3 right=forward.cross(new Vec3(0,1,0));
        for(int i=0;i<16;i++){double a=-1.25+i*.15,b=a+.15;Vec3 aa=p.add(forward.scale(Math.cos(a)*radius)).add(right.scale(Math.sin(a)*radius)),bb=p.add(forward.scale(Math.cos(b)*radius)).add(right.scale(Math.sin(b)*radius));out.add(new Quad(aa,bb,bb.add(0,.06+progress*.06,0),aa.add(0,.06+progress*.06,0),color,Math.max(0,alpha-i*4)));}
    }
    private static void crescent(List<Quad> out,Vec3 p,Vec3 dir,double radius,int color,int alpha){
        Vec3 side=dir.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize();Vec3 up=side.cross(dir).normalize();
        for(int i=0;i<18;i++){double a=-Math.PI*.7+i*Math.PI*1.4/18,b=a+Math.PI*1.4/18;Vec3 aa=side.scale(Math.cos(a)).add(up.scale(Math.sin(a))),bb=side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));out.add(new Quad(p.add(aa.scale(radius)),p.add(bb.scale(radius)),p.add(bb.scale(radius*.75)),p.add(aa.scale(radius*.75)),color,alpha));}
    }
    private static void beam(List<Quad> out,Vec3 p,Vec3 direction,double range,int alpha,double halfWidth,boolean full3d){
        Vec3 dir=full3d?direction:direction.multiply(1,0,1);dir=dir.lengthSqr()<.01?new Vec3(0,0,1):dir.normalize();var mc=net.minecraft.client.Minecraft.getInstance();Vec3 end=p.add(dir.scale(range));
        if(mc.level!=null&&mc.player!=null){var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(p,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=hit.getLocation();}
        Vec3 rawSide=dir.cross(new Vec3(0,1,0));if(rawSide.lengthSqr()<1e-4)rawSide=new Vec3(1,0,0);
        Vec3 side=rawSide.normalize().scale(halfWidth),up=rawSide.normalize().cross(dir).normalize().scale(halfWidth);out.add(new Quad(p.add(side),p.subtract(side),end.subtract(side),end.add(side),0x73c3ff,alpha));out.add(new Quad(p.add(up),p.subtract(up),end.subtract(up),end.add(up),0xc2edff,alpha));
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
