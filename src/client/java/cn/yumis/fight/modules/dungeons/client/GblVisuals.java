package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import java.util.function.Supplier;

/** Content-owned telegraphs and actual world-space projectiles share the server trajectory snapshot. */
public final class GblVisuals {
    private record Track(GblAttack attack,long removed){}
    private record Frame(List<Quad> quads,List<EffectSprites.SpriteQuad> sprites,boolean nearFade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","gbl_effects"));
    private static final Map<String,Track> tracks=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new LinkedHashMap<>();
    private static final Set<String> shook=new HashSet<>();
    private static Supplier<String> instance=()->"";
    private static Object world;
    public static void register(Supplier<String> currentInstance){instance=currentInstance;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GblVisuals::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GblVisuals::submit);}
    public static void receive(JsonObject packet){
        var mc=Minecraft.getInstance();if(world!=mc.level){tracks.clear();closed.clear();shook.clear();world=mc.level;}if(mc.level==null)return;
        GblAttack a=new Gson().fromJson(packet,GblAttack.class);long now=ClientUi.current().serverNow();
        if(a.remove){closed.put(a.id,now+10000);if(!a.shape.equals("LINE"))tracks.put(a.id,new Track(a,now));else tracks.remove(a.id);}
        else if(!closed.containsKey(a.id))tracks.put(a.id,new Track(a,0));
        while(tracks.size()>512)tracks.remove(tracks.keySet().iterator().next());while(closed.size()>1024)closed.remove(closed.keySet().iterator().next());
        if(shook.size()>512)shook.clear();
    }
    private static void extract(ExtractLevelRenderStateEvent event){
        var mc=Minecraft.getInstance();if(world!=mc.level){tracks.clear();closed.clear();shook.clear();world=mc.level;}
        List<Quad> q=new ArrayList<>();List<EffectSprites.SpriteQuad> s=new ArrayList<>();
        Vec3 cam=event.getCamera().position();
        if(mc.player!=null&&ClientUi.current().ready()){
            long now=ClientUi.current().serverNow();String here=instance.get();closed.values().removeIf(until->until<now);
            tracks.values().removeIf(t->!t.attack.instance.equals(here)||now>t.attack.end()+700||t.removed>0&&now>t.removed+300);
            for(var t:tracks.values())draw(q,s,cam,t,now);
            for(var e:mc.level.entitiesForRendering())if(e instanceof DungeonEnemy mob&&GblEnemyKind.of(mob)!=null&&mob.guarded()&&mob.distanceToSqr(mc.player)<4096){
                Vec3 p=new Vec3(mob.getX(),80.08,mob.getZ());
                ring(q,p,mob.getBbWidth()+.35,.07,0xf3d37b,190);
                EffectSprites.sigil(s,p,mob.getBbWidth()+.48,now*0.0014,0xf3d37b,160,false);
            }
        }else tracks.clear();
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),List.copyOf(s),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void draw(List<Quad> q,List<EffectSprites.SpriteQuad> s,Vec3 cam,Track track,long now){
        GblAttack a=track.attack;boolean warning=now<a.release();int color=a.element==0?0xf2ce70:a.kind.contains("PRIEST")?0xe0a08e:0xf07862;
        Vec3 ground=new Vec3(a.x,80.045,a.z),target=new Vec3(a.ex,80.045,a.ez);
        double warnP=a.release()<=a.started?1:Math.clamp((now-a.started)/(double)(a.release()-a.started),0,1);
        double activeP=a.end()<=a.release()?1:Math.clamp((now-a.release())/(double)(a.end()-a.release()),0,1);
        if(track.removed>0){
            if(a.shape.equals("LOB")||a.shape.equals("SPEAR")||a.shape.equals("IMPACT")){
                double p=Math.clamp((now-track.removed)/300d,0,1);Vec3 hitPos=a.shape.equals("LOB")?target:ground;
                ring(q,hitPos,a.radius*(.5+p),.2,color,(int)(220*(1-p)));
                EffectSprites.shockwave(s,hitPos.add(0,.02,0),a.radius*(.45+.75*p),p*1.1,color,(int)(225*(1-p)));
                EffectSprites.impactBurst(s,hitPos.add(0,.05,0),cam,a.radius*.9,p,color,230,true);
                EffectSprites.glow(s,hitPos.add(0,.35,0),cam,a.radius*.5*(1-p),0xfff1c1,(int)(210*(1-p)));
                if(shook.add(a.id+"#hit"))CameraShake.impulse(hitPos,Math.max(10,a.radius*4),0.22f,70,160);
            }
            return;
        }
        if(a.shape.equals("LINE")){
            Vec3 direction=target.subtract(ground).normalize(),side=new Vec3(direction.z,0,-direction.x).scale(Math.max(.18,a.width));
            if(warning){q.add(new Quad(ground.add(side),target.add(side),target.subtract(side),ground.subtract(side),color,75));line(q,ground,target,.035,color,190);}
            else{
                Vec3 p=a.position(now),tail=p.subtract(a.destination().subtract(a.origin()).normalize().scale(a.kind.equals("CANNONBALL")?.5:.7));
                int projColor=a.kind.equals("CANNONBALL")?0xf5ab56:a.kind.contains("NEEDLE")?0xf5dba0:0xe0e8ed;
                if(a.kind.equals("CANNONBALL"))box(q,p,.17,.17,.17,0xf5ab56,245);
                else{line(q,tail,p,.07,projColor,245);line(q,tail.add(0,.05,0),p.add(0,.05,0),.04,0xffefc3,220);}
                RibbonTrail.push("gbl:"+a.id,p,a.kind.equals("CANNONBALL")?.22:.12,projColor,205,220,ClientUi.now());
                EffectSprites.glow(s,p,cam,a.kind.equals("CANNONBALL")?.52:.34,0xffefc3,205);
            }
        }else if(a.shape.equals("LOB")){
            disc(q,target,a.radius,color,warning?45:25);ring(q,target,a.radius,.09,color,200);
            if(warning)EffectSprites.warningCircle(s,target.add(0,.015,0),a.radius,warnP,color,175);
            else{
                Vec3 p=a.position(now);box(q,p,.18,.18,.18,0xf4a752,245);
                RibbonTrail.push("gbl:"+a.id,p,.22,0xf4a752,205,260,ClientUi.now());
                EffectSprites.glow(s,p,cam,.5,0xffdf98,215);
            }
        }else if(a.shape.equals("FAN")){
            Vec3 direction=a.destination().subtract(a.origin()).multiply(1,0,1).normalize();double heading=Math.atan2(direction.z,direction.x);
            for(int i=0;i<24;i++){double x=heading-a.halfAngle+2*a.halfAngle*i/24,y=heading-a.halfAngle+2*a.halfAngle*(i+1)/24;Vec3 p=ground.add(Math.cos(x)*a.radius,0,Math.sin(x)*a.radius),r=ground.add(Math.cos(y)*a.radius,0,Math.sin(y)*a.radius);q.add(new Quad(ground,p,r,ground,color,warning?65:125));line(q,p,r,.05,color,210);}
            if(!warning&&activeP<0.98)EffectSprites.slashArc(s,ground.add(0,.85,0),direction,6,a.radius,activeP,color,210);
        }else if(a.shape.equals("WAVE")){
            if(warning){ring(q,ground,a.radius,.07,color,150);disc(q,ground,a.radius,color,20);EffectSprites.warningCircle(s,ground.add(0,.015,0),a.radius,warnP,color,165);}
            else{
                double radius=Math.max(.15,a.waveRadius(now));
                ring(q,ground,radius,a.width,color,180);ring(q,ground.add(0,.15,0),radius,.08,0xffdf98,230);
                EffectSprites.shockwave(s,ground.add(0,.03,0),radius,now*0.002,color,205);
            }
        }else{
            disc(q,ground,a.radius,color,warning?55:135);ring(q,ground,a.radius,.085,color,210);
            if(warning)EffectSprites.warningCircle(s,ground.add(0,.015,0),a.radius,warnP,color,180);
            else if(activeP<0.98){
                EffectSprites.shockwave(s,ground.add(0,.03,0),a.radius*(.35+.75*activeP),activeP*1.2,color,(int)(210*(1-activeP)));
                EffectSprites.impactBurst(s,ground.add(0,.05,0),cam,a.radius*.85,activeP,color,215,true);
            }
            if(a.shape.equals("SPEAR")&&now>=a.release()-300){
                double fall=Math.clamp((a.release()-now)/300d,0,1);Vec3 tip=ground.add(0,8*fall,0);
                line(q,tip,tip.add(0,3,0),.08,0xffe294,245);box(q,tip,.16,.35,.16,0xfff1c1,245);
                EffectSprites.texturedBeam(s,tip,tip.add(0,3.4,0),cam,.22,now,false,0xffe294,215);
                EffectSprites.glow(s,tip,cam,.65,0xfff1c1,225);
                if(fall<=0.05&&shook.add(a.id+"#spear"))CameraShake.impulse(ground,14,0.22f,70,150);
            }
        }
    }
    private static void disc(List<Quad> q,Vec3 c,double radius,int color,int alpha){for(int i=0;i<36;i++){double a=i*Math.PI/18,b=(i+1)*Math.PI/18;q.add(new Quad(c,c.add(Math.cos(a)*radius,0,Math.sin(a)*radius),c.add(Math.cos(b)*radius,0,Math.sin(b)*radius),c,color,alpha));}}
    private static void ring(List<Quad> q,Vec3 c,double radius,double width,int color,int alpha){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24,inner=Math.max(0,radius-width*.5),outer=radius+width*.5;q.add(new Quad(c.add(Math.cos(a)*outer,0,Math.sin(a)*outer),c.add(Math.cos(b)*outer,0,Math.sin(b)*outer),c.add(Math.cos(b)*inner,0,Math.sin(b)*inner),c.add(Math.cos(a)*inner,0,Math.sin(a)*inner),color,alpha));}}
    private static void line(List<Quad> q,Vec3 a,Vec3 b,double width,int color,int alpha){Vec3 d=b.subtract(a),side=d.cross(new Vec3(0,1,0));if(side.lengthSqr()<.001)side=new Vec3(1,0,0);side=side.normalize().scale(width);q.add(new Quad(a.add(side),b.add(side),b.subtract(side),a.subtract(side),color,alpha));Vec3 second=d.cross(side).normalize().scale(width);q.add(new Quad(a.add(second),b.add(second),b.subtract(second),a.subtract(second),color,alpha));}
    private static void box(List<Quad> q,Vec3 p,double x,double y,double z,int color,int alpha){Vec3[] v={p.add(-x,-y,-z),p.add(x,-y,-z),p.add(x,y,-z),p.add(-x,y,-z),p.add(-x,-y,z),p.add(x,-y,z),p.add(x,y,z),p.add(-x,y,z)};for(int[] f:new int[][]{{4,5,6,7},{1,0,3,2},{5,1,2,6},{0,4,7,3},{3,7,6,2},{0,1,5,4}})q.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color,alpha));}
    private static void submit(SubmitCustomGeometryEvent event){Frame frame=event.getLevelRenderState().getRenderData(FRAME);if(frame!=null){WorldQuads.submit(event,frame.quads,frame.nearFade);EffectSprites.submit(event,frame.sprites,frame.nearFade);}}
    private GblVisuals(){}
}
