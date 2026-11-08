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
import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/** Presentation of the eight classic content sets. All contacts belong to EncounterClientContacts. */
public final class ClassicVisuals {
    private record Frame(List<Quad> quads,boolean fade){}
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
        state();var mc=Minecraft.getInstance();List<Quad> q=new ArrayList<>();long now=ClientUi.current().serverNow();
        closed.values().removeIf(until->now>until);tracks.values().removeIf(t->!t.attack.instance.equals(room)||now>(t.finished?t.impact:t.attack.end())+260);
        if(mc.level!=null&&mc.player!=null&&ClientUi.current().ready()){
            for(var t:tracks.values())if(now>=t.attack.started)draw(q,t,now);
            for(var raw:mc.level.entitiesForRendering())if(raw instanceof DungeonEnemy e&&ClassicEnemyKind.of(e)!=null&&e.isAlive()&&e.distanceToSqr(mc.player)<6400){
                if(e.move().equals("wood_cage")){
                    Vec3 c=e.position();box(q,c.add(0,1.1,0),.24,1.1,.24,WOOD,245);
                    for(int i=0;i<3;i++){Vec3 from=c.add(0,.55+i*.65,0),to=from.add(i%2==0?.65:-.65,.5,.2);line(q,from,to,.10,WOOD,245);box(q,to,.18,.13,.18,LEAF,225);}
                    ring(q,c.add(0,.055,0),.6,.055,LEAF,150);
                }
                if(e.guarded()){ring(q,e.position().add(0,.06,0),Math.max(.6,e.getBbWidth()*.75),.055,GOLD,160);}
            }
        }else tracks.clear();
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),NearEffectRendering.enabled(event.getCamera())));
    }
    private static int color(PalaceAttack a,ClassicMove move){
        return switch(a.element){case 0->0xffef89;case 1->0xf68b42;case 2->0x8ed9ff;case 3->0xb28be2;default->move.actor().dungeon.equals("central_park")?LEAF:move.actor().dungeon.equals("hamelin")?0xa8c582:SILVER;};
    }
    private static void draw(List<Quad> q,Track t,long now){
        PalaceAttack a=t.attack;ClassicMove move=ClassicMove.find(a.kind);int color=color(a,move);String clip=move.clip();
        boolean warning=now<a.release()&&!t.finished;double progress=a.progress(now);
        Vec3 c=new Vec3(a.x,80.055,a.z),end=new Vec3(a.ex,80.055,a.ez),d=end.subtract(c).multiply(1,0,1).normalize();if(d.lengthSqr()<.001)d=new Vec3(0,0,1);
        boolean ended=t.finished||now>a.end();long impact=t.finished?t.impact:a.end();
        if(ended){
            if(a.shape.equals("NONE")||a.shape.equals("DART"))return;
            double p=Math.clamp((now-impact)/260d,0,1);Vec3 at=a.shape.equals("BOMB")?end:a.shape.equals("DASH")?new Vec3(a.ex,80.055,a.ez):c;
            ring(q,at,Math.max(.15,(a.shape.equals("BOMB")?a.radius:1.2)*(.5+p*.5)),.08,color,(int)(170*(1-p)));return;
        }
        switch(a.shape){
            case "NONE" -> {
                if(Set.of("summon","puppet","rat_tower","support_call","time_call","instrument","teleport","possession","guard","powered_guard","heal","repair").contains(clip)){
                    ring(q,c,1.2,.045,color,warning?100:160);ring(q,c,Math.max(.1,1.1*(warning?(now-a.started)/(double)Math.max(1,a.windup):1-progress)),.025,color,130);
                    if(Set.of("heal","repair").contains(clip)){line(q,c.add(-.28,1.2,0),c.add(.28,1.2,0),.06,LEAF,190);line(q,c.add(0,.9,0),c.add(0,1.5,0),.06,LEAF,190);}
                }
            }
            case "FAN" -> {fan(q,c,d,a.radius,a.halfAngle,color,warning?28:75);if(!warning){Vec3 side=new Vec3(d.z,0,-d.x);line(q,c.add(side.scale(-a.radius*.65)).add(0,.9,0),c.add(d.scale(a.radius)).add(0,.9,0),.055,color,180);}}
            case "RECT" -> {rect(q,c,c.add(d.scale(a.radius)),a.width,color,warning?32:95);if(!warning){if(Set.of("thorn_line","wood_spikes","whip_combo").contains(clip))thorns(q,c,d,a.radius,progress);else if(clip.equals("breath"))for(int i=1;i<9;i++){Vec3 at=c.add(d.scale(a.radius*i/9));box(q,at.add(0,.35+Math.sin(now/90d+i)*.12,0),a.width*.32,.3,.28,color,100);}else line(q,c.add(0,.8,0),c.add(d.scale(a.radius)).add(0,.8,0),.075,color,205);}}
            case "DART" -> {if(warning)rect(q,c,end,a.width*2,color,22);else projectile(q,t.position(now),d,move,color,now);}
            case "BOMB" -> {
                disc(q,end,a.radius,color,warning?24:38);ring(q,end,a.radius,.065,color,175);
                if(warning){Vec3 previous=a.origin();for(int i=1;i<=12;i++){Vec3 next=a.position(a.release()+a.active*i/12);line(q,previous,next,.012,color,55);previous=next;}}
                else projectile(q,t.position(now),d,move,color,now);
            }
            case "DASH" -> {rect(q,c,end,a.width,color,warning?30:20);if(!warning){Vec3 at=t.position(now);line(q,a.origin().add(0,.35,0),at.add(0,.35,0),.08,color,100);ring(q,new Vec3(at.x,80.065,at.z),a.width*.65,.065,color,140);}}
            case "WAVE" -> {
                if(warning){disc(q,c,a.radius,color,15);ring(q,c,a.radius,.055,color,170);}
                else {double radius=Math.max(.12,a.radius*progress);ring(q,c,radius,a.width,color,90);ring(q,c.add(0,.12,0),radius,.07,color,205);}
            }
            case "CIRCLE" -> {
                disc(q,c,a.radius,color,warning?22:38);ring(q,c,a.radius,.07,color,185);
                if(!warning){
                    if(Set.of("ice","thunder","ore_burst","roots").contains(clip))pillar(q,c,clip,color,progress,now);
                    else if(clip.equals("fire")||clip.equals("explode"))for(int i=0;i<8;i++){double angle=i*Math.PI/4+now/800d;Vec3 at=c.add(Math.cos(angle)*a.radius*.65,0,Math.sin(angle)*a.radius*.65);box(q,at.add(0,.45,0),.16,.4+Math.sin(now/80d+i)*.12,.16,color,150);}
                    else if(clip.equals("tree_cage")){for(int i=0;i<8;i++){Vec3 at=c.add(Math.cos(i*Math.PI/4)*a.radius,.2,Math.sin(i*Math.PI/4)*a.radius);line(q,at,at.add(0,.5,0),.055,LEAF,210);}}
                    else if(clip.equals("music")||clip.equals("ghost_array")){for(int i=0;i<6;i++){double angle=i*Math.PI/3+now/1600d;note(q,c.add(Math.cos(angle)*a.radius*.65,.6,Math.sin(angle)*a.radius*.65),color,170);}}
                    else if(clip.equals("inhale"))for(int i=0;i<6;i++){double angle=i*Math.PI/3+now/250d;line(q,c.add(Math.cos(angle)*a.radius,.45,Math.sin(angle)*a.radius),c.add(Math.cos(angle+.7)*a.radius*.4,.8,Math.sin(angle+.7)*a.radius*.4),.04,color,140);}
                    else ring(q,c.add(0,.10,0),Math.max(.2,a.radius*progress),.05,color,155);
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
    private static void pillar(List<Quad> q,Vec3 c,String clip,int color,double progress,long now){
        if(clip.equals("thunder")){
            Vec3 previous=c.add(0,7,0);for(int i=1;i<=7;i++){Vec3 next=c.add(Math.sin(i*2.3+Math.floor(now/70d))*.28,7-i,Math.cos(i*1.7)*.16);line(q,previous,next,.065,color,240);previous=next;}
        }else if(clip.equals("roots"))thorns(q,c,new Vec3(0,0,1),3,progress);
        else for(int i=0;i<5;i++){double angle=i*Math.PI*2/5,height=(.7+(i%3)*.45)*Math.sin(Math.PI*Math.clamp(progress,.05,.95));Vec3 at=c.add(Math.cos(angle)*1.2,height/2,Math.sin(angle)*1.2);box(q,at,.25,height/2,.25,color,185);box(q,at.add(0,height/2,0),.15,.16,.15,0xe4f7ff,210);}
    }
    private static void thorns(List<Quad> q,Vec3 c,Vec3 d,double length,double progress){
        double height=.4+.9*Math.sin(Math.PI*progress);for(int i=0;i<6;i++){Vec3 at=c.add(d.scale(length*(i+.5)/6));line(q,at,at.add(i%2==0?.2:-.2,height,0),.12,WOOD,235);line(q,at.add(0,height*.6,0),at.add(.4,height*.8,.2),.055,LEAF,225);}
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame frame=event.getLevelRenderState().getRenderData(FRAME);if(frame!=null)WorldQuads.submit(event,frame.quads,frame.fade);}
    private ClassicVisuals(){}
}
