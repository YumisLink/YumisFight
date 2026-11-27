package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import java.util.function.Supplier;
import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/** Presentation of the eight classic content sets. All contacts belong to EncounterClientContacts. */
public final class ClassicVisuals {
    private record Frame(List<Quad> quads,List<SpriteQuad> sprites,boolean fade){}
    private static final class Track {
        PalaceAttack attack;double arc;boolean finished;long impact;
        Vec3 position(long now){Vec3 p=attack.position(now);return arc>0?p.add(0,arc*Math.sin(Math.PI*attack.progress(now)),0):p;}
    }
    private static final Gson JSON=new Gson();
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","classic_dungeon_effects"));
    private static final Map<String,Track> tracks=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new HashMap<>();
    private static Supplier<String> instance=()->"";
    private static Object world;
    private static String room="";
    private static final int SILVER=0xd9dfdf,GOLD=0xffd473,WOOD=0x735034,LEAF=0x76ad50;
    public static void register(Supplier<String> current){instance=current;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClassicVisuals::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClassicVisuals::submit);}
    private static void state(){var next=Minecraft.getInstance().level;String here=instance.get();if(world!=next||!room.equals(here)){world=next;room=here;tracks.clear();closed.clear();}}
    static void receive(JsonObject packet){
        state();var a=JSON.fromJson(packet,PalaceAttack.class);if(ClassicMove.find(a.kind)==null||!a.instance.equals(room))return;
        long now=ClientUi.current().serverNow();
        if(a.remove){
            if(a.cancelled)tracks.remove(a.id);
            else {Track t=tracks.computeIfAbsent(a.id,id->new Track());t.attack=a;t.arc=packet.has("arc")?packet.get("arc").getAsDouble():t.arc;t.finished=true;t.impact=packet.has("contactUntil")?packet.get("contactUntil").getAsLong():now;}
            closed.put(a.id,now+10000);return;
        }
        if(closed.containsKey(a.id))return;Track t=tracks.computeIfAbsent(a.id,id->new Track());t.attack=a;t.arc=packet.has("arc")?packet.get("arc").getAsDouble():0;
    }
    private static void extract(ExtractLevelRenderStateEvent event){
        state();var mc=Minecraft.getInstance();List<Quad> q=new ArrayList<>();List<SpriteQuad> sp=new ArrayList<>();long now=ClientUi.current().serverNow();
        Vec3 camera=event.getRenderState().cameraRenderState==null?Vec3.ZERO:event.getRenderState().cameraRenderState.pos;
        closed.values().removeIf(until->now>until);tracks.values().removeIf(t->!t.attack.instance.equals(room)||now>(t.finished?t.impact:t.attack.end())+260);
        if(mc.level!=null&&mc.player!=null&&ClientUi.current().ready()){
            for(var t:tracks.values())if(now>=t.attack.started)draw(q,sp,camera,t,now);
            for(var raw:mc.level.entitiesForRendering())if(raw instanceof DungeonEnemy e&&ClassicEnemyKind.of(e)!=null&&e.isAlive()&&e.distanceToSqr(mc.player)<6400){
                if(e.move().equals("wood_cage")){
                    Vec3 c=e.position();box(q,c.add(0,1.1,0),.24,1.1,.24,WOOD,245);
                    for(int i=0;i<3;i++){Vec3 from=c.add(0,.55+i*.65,0),to=from.add(i%2==0?.65:-.65,.5,.2);line(q,from,to,.10,WOOD,245);box(q,to,.18,.13,.18,LEAF,225);}
                    ring(q,c.add(0,.055,0),.6,.055,LEAF,150);
                }
                if(e.guarded()){ring(q,e.position().add(0,.06,0),Math.max(.6,e.getBbWidth()*.75),.055,GOLD,160);}
            }
        }else tracks.clear();
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),List.copyOf(sp),NearEffectRendering.enabled(event.getCamera())));
    }
    private static int color(PalaceAttack a,ClassicMove move){
        return switch(a.element){case 0->0xffef89;case 1->0xf68b42;case 2->0x8ed9ff;case 3->0xb28be2;default->move.actor().dungeon.equals("central_park")?LEAF:move.actor().dungeon.equals("hamelin")?0xa8c582:SILVER;};
    }
    private static void draw(List<Quad> q,List<SpriteQuad> sp,Vec3 camera,Track t,long now){
        PalaceAttack a=t.attack;ClassicMove move=ClassicMove.find(a.kind);int color=color(a,move);String clip=move.clip();
        boolean warning=now<a.release()&&!t.finished;double progress=a.progress(now);
        double warnProg=Math.clamp((now-a.started)/(double)Math.max(1L,a.windup),0,1);
        Vec3 c=new Vec3(a.x,80.055,a.z),end=new Vec3(a.ex,80.055,a.ez),d=end.subtract(c).multiply(1,0,1).normalize();if(d.lengthSqr()<.001)d=new Vec3(0,0,1);
        boolean ended=t.finished||now>a.end();long impact=t.finished?t.impact:a.end();
        if(ended){
            if(a.shape.equals("NONE")||a.shape.equals("DART"))return;
            double p=Math.clamp((now-impact)/260d,0,0.999);Vec3 at=a.shape.equals("BOMB")?end:a.shape.equals("DASH")?new Vec3(a.ex,80.055,a.ez):c;
            double rad=Math.max(.35,a.shape.equals("BOMB")?a.radius:1.3);
            EffectSprites.shockwave(sp,at.add(0,.04,0),rad*(.4+.7*EffectSprites.easeOutCubic(p)),p*1.2,color,(int)(210*(1-p)));
            EffectSprites.impactBurst(sp,at.add(0,.15,0),camera,rad,p,color,225,true);
            return;
        }
        switch(a.shape){
            case "NONE" -> {
                if(Set.of("summon","puppet","rat_tower","support_call","time_call","instrument","teleport","possession","guard","powered_guard","heal","repair").contains(clip)){
                    EffectSprites.sigil(sp,c.add(0,.02,0),1.45,now/900d,color,warning?150:210,true);
                    if(Set.of("heal","repair").contains(clip)){line(q,c.add(-.28,1.2,0),c.add(.28,1.2,0),.06,LEAF,190);line(q,c.add(0,.9,0),c.add(0,1.5,0),.06,LEAF,190);}
                }
            }
            case "FAN" -> {
                fan(q,c,d,a.radius,a.halfAngle,color,warning?28:75);
                if(!warning){
                    EffectSprites.slashArc(sp,c.add(d.scale(a.radius*.35)).add(0,.95,0),d,0,a.radius*1.05,Math.clamp(progress,0,0.999),color,235);
                }
            }
            case "RECT" -> {
                rect(q,c,c.add(d.scale(a.radius)),a.width,color,warning?32:95);
                if(!warning){
                    if(Set.of("thorn_line","wood_spikes","whip_combo").contains(clip))thorns(q,c,d,a.radius,progress);
                    else if(clip.equals("breath"))EffectSprites.texturedBeam(sp,c.add(0,.75,0),c.add(d.scale(a.radius)).add(0,.75,0),camera,a.width*.35,now,true,color,225);
                    else EffectSprites.texturedBeam(sp,c.add(0,.8,0),c.add(d.scale(a.radius)).add(0,.8,0),camera,Math.max(.18,a.width*.22),now,false,color,220);
                }
            }
            case "DART" -> {if(warning)rect(q,c,end,a.width*2,color,22);else {Vec3 pos=t.position(now);projectile(q,pos,d,move,color,now);EffectSprites.glow(sp,pos,camera,.48,color,195);RibbonTrail.push("classic:"+a.id,pos,.16,color,195,200,ClientUi.now());}}
            case "BOMB" -> {
                EffectSprites.warningCircle(sp,end.add(0,.02,0),a.radius,warning?warnProg:1.0,color,warning?185:215);
                if(warning){Vec3 previous=a.origin();for(int i=1;i<=12;i++){Vec3 next=a.position(a.release()+a.active*i/12);line(q,previous,next,.012,color,55);previous=next;}}
                else {Vec3 pos=t.position(now);projectile(q,pos,d,move,color,now);EffectSprites.glow(sp,pos,camera,.65,color,210);RibbonTrail.push("classic:"+a.id,pos,.22,color,210,240,ClientUi.now());}
            }
            case "DASH" -> {rect(q,c,end,a.width,color,warning?30:20);if(!warning){Vec3 at=t.position(now);RibbonTrail.push("classic:"+a.id,at.add(0,.55,0),a.width*.28,color,215,240,ClientUi.now());EffectSprites.shockwave(sp,new Vec3(at.x,80.065,at.z),a.width*.7,now/500d,color,185);}}
            case "WAVE" -> {
                if(warning)EffectSprites.warningCircle(sp,c.add(0,.02,0),a.radius,warnProg,color,195);
                else {double radius=Math.max(.25,a.radius*progress);EffectSprites.shockwave(sp,c.add(0,.08,0),radius,progress*1.3,color,(int)(235*(1-progress*.6)));}
            }
            case "CIRCLE" -> {
                if(warning)EffectSprites.warningCircle(sp,c.add(0,.02,0),a.radius,warnProg,color,200);
                else {
                    EffectSprites.sigil(sp,c.add(0,.03,0),a.radius,now/950d,color,180,true);
                    if(Set.of("ice","thunder","ore_burst","roots").contains(clip))pillar(q,sp,camera,c,clip,color,progress,now,a.id.hashCode());
                    else if(clip.equals("fire")||clip.equals("explode")){EffectSprites.impactBurst(sp,c.add(0,.2,0),camera,a.radius*1.1,Math.clamp(progress,0,0.999),color,235,true);}
                    else if(clip.equals("tree_cage")){for(int i=0;i<8;i++){Vec3 at=c.add(Math.cos(i*Math.PI/4)*a.radius,.2,Math.sin(i*Math.PI/4)*a.radius);line(q,at,at.add(0,.5,0),.055,LEAF,210);}}
                    else if(clip.equals("music")||clip.equals("ghost_array")){for(int i=0;i<6;i++){double angle=i*Math.PI/3+now/1600d;note(q,c.add(Math.cos(angle)*a.radius*.65,.6,Math.sin(angle)*a.radius*.65),color,170);}}
                    else if(clip.equals("inhale"))for(int i=0;i<6;i++){double angle=i*Math.PI/3+now/250d;line(q,c.add(Math.cos(angle)*a.radius,.45,Math.sin(angle)*a.radius),c.add(Math.cos(angle+.7)*a.radius*.4,.8,Math.sin(angle+.7)*a.radius*.4),.04,color,140);}
                    else EffectSprites.shockwave(sp,c.add(0,.08,0),Math.max(.3,a.radius*progress),progress*1.2,color,210);
                }
            }
            default -> { }
        }
    }
    private static void projectile(List<Quad> q,Vec3 c,Vec3 d,ClassicMove move,int color,long now){
        String clip=move.clip(),model=move.actor().model;
        if(clip.equals("throw_net")){
            Vec3 side=new Vec3(d.z,0,-d.x);for(int i=-2;i<=2;i++){line(q,c.add(side.scale(-.6)).add(0,i*.25,0),c.add(side.scale(.6)).add(0,i*.25,0),.018,0xc6b589,210);line(q,c.add(side.scale(i*.25)).add(0,-.6,0),c.add(side.scale(i*.25)).add(0,.6,0),.018,0xc6b589,210);}return;
        }
        if(clip.equals("rat_wave")){
            box(q,c,.25,.16,.24,0x777168,245);for(int side:new int[]{-1,1})box(q,c.add(side*.16,.15,.05),.075,.075,.055,0xbc9a9d,240);line(q,c.subtract(d.scale(.2)),c.subtract(d.scale(.7)).add(0,.05,0),.025,0xbc9a9d,220);return;
        }
        if(clip.equals("flute")||clip.equals("music")){note(q,c,color,230);return;}
        if(clip.equals("missile")||clip.equals("bombard")||clip.equals("cannon")){
            line(q,c.subtract(d.scale(.35)),c.add(d.scale(.25)),.15,0x666d76,245);box(q,c.add(d.scale(.25)),.12,.12,.12,SILVER,245);line(q,c.subtract(d.scale(.35)),c.subtract(d.scale(.75)),.07,0xffb44c,220);return;
        }
        if(clip.equals("throw")&&model.equals("blue_witch")){box(q,c,.17,.22,.17,WOOD,245);box(q,c.add(.07,.22,0),.16,.07,.1,LEAF,240);return;}
        if(clip.equals("throw")&&(model.contains("thief")||model.contains("bandit")||model.equals("croc_spearman"))){
            double length=model.equals("croc_spearman")?1.2:.55;line(q,c.subtract(d.scale(length*.7)),c.add(d.scale(length*.1)),.035,WOOD,245);line(q,c,c.add(d.scale(length*.4)),.065,SILVER,250);return;
        }
        if(clip.equals("poison")||clip.equals("flash")){box(q,c,.14,.18,.14,clip.equals("poison")?0xa1c950:GOLD,220);box(q,c.add(0,.23,0),.065,.065,.065,0x695a3f,245);return;}
        if(clip.equals("shoot")){line(q,c.subtract(d.scale(.4)),c,.025,GOLD,245);return;}
        if(clip.equals("ice")){box(q,c,.16,.24,.16,0xd2f1ff,215);line(q,c.subtract(d.scale(.6)),c,.04,color,110);return;}
        box(q,c,.15,.15,.15,color,225);ring(q,c.add(0,-.1,0),.24,.035,color,140);
    }
    private static void note(List<Quad> q,Vec3 c,int color,int alpha){box(q,c,.10,.07,.045,color,alpha);line(q,c.add(.08,0,0),c.add(.08,.43,0),.025,color,alpha);line(q,c.add(.08,.43,0),c.add(.27,.37,0),.035,color,alpha);}
    private static void pillar(List<Quad> q,List<SpriteQuad> sp,Vec3 camera,Vec3 c,String clip,int color,double progress,long now,long seed){
        if(clip.equals("thunder")){
            LightningBolt.bolt(sp,c.add(0,11,0),c,camera,(now/50L)^seed,10,.65,.10,color,245,true);
            EffectSprites.impactBurst(sp,c.add(0,.12,0),camera,2.2,Math.clamp(progress,0,0.999),color,235,true);
        }else if(clip.equals("roots"))thorns(q,c,new Vec3(0,0,1),3,progress);
        else for(int i=0;i<5;i++){double angle=i*Math.PI*2/5,height=(.7+(i%3)*.45)*Math.sin(Math.PI*Math.clamp(progress,.05,.95));Vec3 at=c.add(Math.cos(angle)*1.2,height/2,Math.sin(angle)*1.2);box(q,at,.25,height/2,.25,color,185);box(q,at.add(0,height/2,0),.15,.16,.15,0xe4f7ff,210);}
    }
    private static void thorns(List<Quad> q,Vec3 c,Vec3 d,double length,double progress){
        double height=.4+.9*Math.sin(Math.PI*progress);for(int i=0;i<6;i++){Vec3 at=c.add(d.scale(length*(i+.5)/6));line(q,at,at.add(i%2==0?.2:-.2,height,0),.12,WOOD,235);line(q,at.add(0,height*.6,0),at.add(.4,height*.8,.2),.055,LEAF,225);}
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame frame=event.getLevelRenderState().getRenderData(FRAME);if(frame!=null){WorldQuads.submit(event,frame.quads,frame.fade);EffectSprites.submit(event,frame.sprites,frame.fade);}}
    private ClassicVisuals(){}
}
