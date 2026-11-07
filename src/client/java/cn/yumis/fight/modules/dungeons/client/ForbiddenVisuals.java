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

/** Content-only warnings and effects; no damage, immunity or encounter progression is decided here. */
public final class ForbiddenVisuals {
    private record Frame(List<Quad> quads,boolean fade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","forbidden_effects"));
    private static final Gson JSON=new Gson();
    private static final Map<String,GblAttack> tracks=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new LinkedHashMap<>();
    private static Supplier<String> instance=()->"";
    private static Object world;
    public static void register(Supplier<String> current){instance=current;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ForbiddenVisuals::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ForbiddenVisuals::submit);}
    private static void world(){if(world!=Minecraft.getInstance().level){world=Minecraft.getInstance().level;tracks.clear();closed.clear();}}
    public static void receive(JsonObject packet){
        world();if(world==null)return;GblAttack a=JSON.fromJson(packet,GblAttack.class);long now=ClientUi.current().serverNow();
        if(a.remove){closed.put(a.id,now+10000);tracks.remove(a.id);}else if(!closed.containsKey(a.id))tracks.put(a.id,a);
        while(tracks.size()>512)tracks.remove(tracks.keySet().iterator().next());while(closed.size()>1024)closed.remove(closed.keySet().iterator().next());
    }
    private static void extract(ExtractLevelRenderStateEvent event){
        world();var mc=Minecraft.getInstance();List<Quad> q=new ArrayList<>();long now=ClientUi.current().serverNow();String here=instance.get();
        closed.values().removeIf(until->now>until);tracks.values().removeIf(a->!a.instance.equals(here)||now>a.end()+500);
        if(mc.level!=null&&mc.player!=null&&ClientUi.current().ready()){
            for(var a:tracks.values())if(now>=a.started)draw(q,a,now);
            for(var raw:mc.level.entitiesForRendering())if(raw instanceof DungeonEnemy core&&ForbiddenEnemyKind.of(core)==ForbiddenEnemyKind.BARRIER_CORE&&core.isAlive()&&!core.isInvisible()&&core.distanceToSqr(mc.player)<4096)shield(q,core.position(),now);
        }else tracks.clear();
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void draw(List<Quad> q,GblAttack a,long now){
        boolean warning=now<a.release();int color=a.element==1?0xff8848:a.element==3?0xb48af1:0xf3c9a3;
        Vec3 c=new Vec3(a.x,a.y+.045,a.z),end=new Vec3(a.ex,a.y+.045,a.ez),direction=end.subtract(c).normalize();
        switch(a.shape){
            case "LINE" -> {
                if(warning)rect(q,c,end,a.width*2,color,45);
                else if(now<=a.end()){
                    Vec3 tip=a.position(now),tail=tip.subtract(a.destination().subtract(a.origin()).normalize().scale(.75));
                    line(q,tail,tip,.065,a.magic?0xcaa5ff:0xdbe6e9,245);box(q,tip,.09,.09,.09,a.magic?0xf3d6ff:0xffdfb5,245);
                }
            }
            case "FAN" -> fan(q,c,direction,a.radius,a.halfAngle,color,warning?55:105);
            case "WAVE" -> {
                if(warning){disc(q,c,a.radius,color,20);ring(q,c,a.radius,.07,color,170);}
                else if(now<=a.end()){double radius=Math.max(.1,a.waveRadius(now));ring(q,c,radius,a.width,color,155);ring(q,c.add(0,.24,0),radius,.07,0xf1d9ff,220);}
            }
            case "EXPLOSION" -> {
                double age=Math.clamp((now-a.started)/500d,0,1);int alpha=(int)(230*(1-age));
                ring(q,c,a.radius*(.3+.7*age),.22,color,alpha);disc(q,c,a.radius*(1-age),0xffc67c,alpha/2);
                for(int i=0;i<10;i++){double angle=i*Math.PI/5;Vec3 p=c.add(Math.cos(angle)*a.radius*age,.2+Math.sin(Math.PI*age)*1.3,Math.sin(angle)*a.radius*age);box(q,p,.09,.14,.09,i%2==0?0xffc47c:0xe35e42,alpha);}
            }
            case "SUMMON" -> {
                double pulse=.6+.2*Math.sin((now-a.started)/100d);ring(q,c,a.radius,.08,color,185);ring(q,c,a.radius*pulse,.06,0xf1c9fa,150);
                if(warning)disc(q,c,a.radius,color,25);
            }
            case "BARRIER" -> {if(warning){ring(q,c,a.radius,.1,0xf1d17f,190);line(q,c.add(0,.1,0),c.add(0,2.5,0),.035,0xffecb0,120);}}
            default -> { }
        }
    }
    private static void shield(List<Quad> q,Vec3 base,long now){
        Vec3 center=base.add(0,1.35,0);int color=0xe5cf88;double spin=now/1600d;
        ring(q,base.add(0,.08,0),1.5,.065,color,175);ring(q,base.add(0,2.65,0),.45,.05,color,150);
        for(int i=0;i<16;i++){
            double a=i*Math.PI/8+spin,b=(i+1)*Math.PI/8+spin;
            Vec3 left=center.add(Math.cos(a)*1.55,0,Math.sin(a)*1.55),right=center.add(Math.cos(b)*1.55,0,Math.sin(b)*1.55);
            Vec3 high=base.add(0,2.8,0),low=base.add(0,.02,0);
            q.add(new Quad(high,left,right,high,color,17));q.add(new Quad(low,right,left,low,color,12));
            if(i%2==0){line(q,left,high,.018,0xffebae,100);line(q,left,low,.018,0xffebae,100);}
        }
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame f=event.getLevelRenderState().getRenderData(FRAME);if(f!=null)WorldQuads.submit(event,f.quads,f.fade);}
    private ForbiddenVisuals(){}
}
