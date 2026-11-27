package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.PalaceAttack;
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

/** The same frozen trajectory supplies palace telegraphs, visible projectiles and contact effects. */
public final class PalaceVisuals {
    private record Track(PalaceAttack attack,long removed){}
    private record Frame(List<Quad> quads,List<EffectSprites.SpriteQuad> sprites,boolean fade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","palace_effects"));
    private static final Map<String,Track> tracks=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new LinkedHashMap<>();
    private static final Set<String> shook=new HashSet<>();
    private static Supplier<String> instance=()->"";private static Object world;
    public static void register(Supplier<String> current){instance=current;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PalaceVisuals::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PalaceVisuals::submit);}
    private static void world(){if(world!=Minecraft.getInstance().level){world=Minecraft.getInstance().level;tracks.clear();closed.clear();shook.clear();}}
    public static void receive(JsonObject packet){
        world();if(world==null)return;PalaceAttack a=new Gson().fromJson(packet,PalaceAttack.class);long now=ClientUi.current().serverNow();
        if(a.remove){closed.put(a.id,now+10000);if(a.cancelled||a.shape.equals("DART"))tracks.remove(a.id);else tracks.put(a.id,new Track(a,now));}
        else if(!closed.containsKey(a.id))tracks.put(a.id,new Track(a,0));
        while(tracks.size()>512)tracks.remove(tracks.keySet().iterator().next());while(closed.size()>1024)closed.remove(closed.keySet().iterator().next());
        if(shook.size()>512)shook.clear();
    }
    private static void extract(ExtractLevelRenderStateEvent event){
        world();List<Quad> q=new ArrayList<>();List<EffectSprites.SpriteQuad> s=new ArrayList<>();
        Vec3 cam=event.getCamera().position();
        long now=ClientUi.current().serverNow();String here=instance.get();closed.values().removeIf(until->until<now);
        tracks.values().removeIf(t->!t.attack.instance.equals(here)||now>t.attack.end()+800||t.removed>0&&now>t.removed+400);
        if(ClientUi.current().ready())for(Track t:tracks.values())if(now>=t.attack.started)draw(q,s,cam,t,now);
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),List.copyOf(s),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void draw(List<Quad> q,List<EffectSprites.SpriteQuad> s,Vec3 cam,Track t,long now){
        PalaceAttack a=t.attack;boolean warning=now<a.release();int color=a.element==0?0xf6d267:a.element==1?0xff914e:a.kind.equals("STONE_PILLAR")?0xcfae81:0xf07862;
        Vec3 c=new Vec3(a.x,80.045,a.z),end=new Vec3(a.ex,80.045,a.ez),direction=end.subtract(c).normalize();
        double warnP=a.release()<=a.started?1:Math.clamp((now-a.started)/(double)(a.release()-a.started),0,1);
        double activeP=Math.clamp(a.progress(now),0,0.99);
        if(t.removed>0){
            if(a.shape.equals("BOMB")){
                double age=Math.clamp((now-t.removed)/400d,0,0.99);
                ring(q,end,a.radius*(.5+age),.18,color,(int)(220*(1-age)));box(q,end.add(0,.4,0),a.radius*(1-age),.3,a.radius*(1-age),0xffd482,(int)(120*(1-age)));
                EffectSprites.shockwave(s,end.add(0,.03,0),a.radius*(.4+.8*age),age*1.3,color,(int)(230*(1-age)));
                EffectSprites.impactBurst(s,end.add(0,.12,0),cam,a.radius*.95,age,0xffd482,235,true);
                EffectSprites.glow(s,end.add(0,.45,0),cam,a.radius*.55*(1-age),0xfff2c2,(int)(210*(1-age)));
                if(shook.add(a.id+"#bomb"))CameraShake.impulse(end,Math.max(12,a.radius*4),0.26f,80,180);
            }
            return;
        }
        switch(a.shape){
            case "FAN" -> {
                fan(q,c,direction,a.radius,a.halfAngle,color,warning?55:100);
                if(!warning){
                    ring(q,c.add(direction.scale(a.radius*.5)).add(0,.7,0),a.radius*.35,.09,a.element==0?0xfff0b5:0xdfe8eb,150);
                    EffectSprites.slashArc(s,c.add(0,.9,0),direction,8,a.radius,activeP,a.element==0?0xfff0b5:color,215);
                }
            }
            case "RECT","DASH" -> {
                Vec3 to=a.shape.equals("RECT")?c.add(direction.scale(a.radius)):end;rect(q,c,to,a.width,color,warning?55:120);
                if(!warning&&a.kind.equals("LIGHT_BEAM")){
                    Vec3 bFrom=c.add(0,1.25,0),bTo=to.add(0,1.25,0);
                    line(q,bFrom,bTo,.42,0xffd960,210);line(q,bFrom,bTo,.15,0xfffbdf,250);
                    EffectSprites.texturedBeam(s,bFrom,bTo,cam,.46,now,true,0xffd960,235);
                    LightningBolt.helixArcs(s,bFrom,bTo,cam,now,.42,2,0xfff3a8,195);
                    if(shook.add(a.id+"#beam"))CameraShake.impulse(bFrom,18,0.28f,100,200);
                }
            }
            case "DART" -> {
                if(warning)rect(q,c,end,a.width*2,color,40);
                else{
                    Vec3 tip=a.position(now),tail=tip.subtract(a.destination().subtract(a.origin()).normalize().scale(.65));
                    line(q,tail,tip,.055,0xd7e8d2,245);box(q,tip,.08,.08,.08,0xe7f1e6,245);
                    RibbonTrail.push("palace:"+a.id,tip,.14,0xd7e8d2,190,220,ClientUi.now());
                    EffectSprites.glow(s,tip,cam,.32,0xe7f1e6,195);
                }
            }
            case "BOMB" -> {
                disc(q,end,a.radius,color,40);ring(q,end,a.radius,.08,color,210);
                if(warning)EffectSprites.warningCircle(s,end.add(0,.015,0),a.radius,warnP,color,175);
                else{
                    Vec3 p=a.position(now);box(q,p,.22,.22,.22,0x3e4538,255);line(q,p.add(0,.2,0),p.add(.1,.4,0),.04,0xffd369,245);
                    RibbonTrail.push("palace:"+a.id,p.add(0,.25,0),.18,0xffd369,195,240,ClientUi.now());
                    EffectSprites.glow(s,p.add(.1,.4,0),cam,.38,0xffd369,225);
                }
            }
            case "WAVE" -> {
                if(warning){disc(q,c,a.radius,color,20);ring(q,c,a.radius,.07,color,170);EffectSprites.warningCircle(s,c.add(0,.015,0),a.radius,warnP,color,165);}
                else{
                    double radius=Math.max(.1,a.radius*a.progress(now));
                    ring(q,c,radius,a.width,color,160);ring(q,c.add(0,.35,0),radius,.075,0xfff2b5,245);
                    EffectSprites.shockwave(s,c.add(0,.04,0),radius,now*0.0022,color,215);
                }
            }
            default -> {
                disc(q,c,a.radius,color,warning?50:100);ring(q,c,a.radius,.08,color,215);
                if(warning)EffectSprites.warningCircle(s,c.add(0,.015,0),a.radius,warnP,color,175);
                else if(a.kind.equals("LIGHTNING")){
                    Vec3 previous=c.add(0,11,0);for(int i=1;i<=8;i++){Vec3 next=c.add(Math.sin(i*2.7)*.3,11*(1-i/8d),Math.cos(i*3.1)*.25);line(q,previous,next,.17,0x73daff,230);line(q,previous,next,.05,0xf5ffff,255);previous=next;}
                    LightningBolt.bolt(s,c.add(0,12,0),c,cam,a.id.hashCode()*31L+(now/50L),10,.95,.14,0x73daff,235,true);
                    EffectSprites.impactBurst(s,c.add(0,.05,0),cam,a.radius*.95,activeP,0x73daff,235,true);
                    EffectSprites.shockwave(s,c.add(0,.03,0),a.radius*(.35+.75*activeP),activeP*1.2,0x73daff,(int)(215*(1-activeP)));
                    if(shook.add(a.id+"#light"))CameraShake.impulse(c,18,0.28f,80,170);
                }else if(a.kind.equals("STONE_PILLAR")){
                    for(int i=-1;i<=1;i++)box(q,c.add(i*.6,.8,0),.3,1.15,.35,i==0?0x9a8871:0x695b50,245);
                    EffectSprites.shockwave(s,c.add(0,.04,0),a.radius*(.4+.7*activeP),activeP,0xcfae81,(int)(210*(1-activeP)));
                    EffectSprites.impactBurst(s,c.add(0,.08,0),cam,a.radius*.8,activeP,0xdac2a3,210,true);
                    if(shook.add(a.id+"#pillar"))CameraShake.impulse(c,16,0.28f,90,180);
                }else if(a.kind.equals("GOLEM_SLAM")){
                    double sr=Math.max(.2,a.radius*a.progress(now));
                    ring(q,c,sr,.2,0xdac2a3,160);
                    EffectSprites.shockwave(s,c.add(0,.04,0),sr,activeP*1.1,0xdac2a3,(int)(225*(1-activeP*.8)));
                    EffectSprites.impactBurst(s,c.add(0,.08,0),cam,a.radius*.9,activeP,0xffd9a0,220,true);
                    if(shook.add(a.id+"#slam"))CameraShake.impulse(c,20,0.34f,100,220);
                }
            }
        }
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame f=event.getLevelRenderState().getRenderData(FRAME);if(f!=null){WorldQuads.submit(event,f.quads,f.fade);EffectSprites.submit(event,f.sprites,f.fade);}}
    private PalaceVisuals(){}
}
