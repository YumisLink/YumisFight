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
import static cn.yumis.fight.modules.dungeons.api.SkasaMoves.*;

/** Geometry and ice performance only; the dungeon's signed contacts decide actual hits. */
public final class SkasaVisuals {
    private record Frame(List<Quad> quads,boolean fade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","skasa_effects"));
    private static final Gson JSON=new Gson();
    private static final Map<String,SkasaAttack> tracks=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new LinkedHashMap<>();
    private static Supplier<String> instance=()->"";
    private static Object world;
    private static final int ICE=0x73d3ff,WHITE=0xd9f7ff,DARK=0x175381,SAFE=0x83ebcf;
    public static void register(Supplier<String> current){
        instance=current;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkasaVisuals::extract);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkasaVisuals::submit);
    }
    private static void world(){if(world!=Minecraft.getInstance().level){world=Minecraft.getInstance().level;tracks.clear();closed.clear();}}
    public static void receive(JsonObject packet){
        world();if(world==null)return;SkasaAttack a=JSON.fromJson(packet,SkasaAttack.class);long now=ClientUi.current().serverNow();
        if(a.remove){closed.put(a.id,now+10000);tracks.remove(a.id);}else if(!closed.containsKey(a.id))tracks.put(a.id,a);
        while(tracks.size()>512)tracks.remove(tracks.keySet().iterator().next());
        while(closed.size()>1024)closed.remove(closed.keySet().iterator().next());
    }
    private static void extract(ExtractLevelRenderStateEvent event){
        world();var mc=Minecraft.getInstance();List<Quad> q=new ArrayList<>();long now=ClientUi.current().serverNow();String here=instance.get();
        closed.values().removeIf(until->now>until);tracks.values().removeIf(a->!a.instance.equals(here)||now>a.end()+500);
        if(mc.level!=null&&mc.player!=null&&ClientUi.current().ready()){
            for(var a:tracks.values())if(now>=a.started&&now<=a.end())draw(q,a,now);
        }else tracks.clear();
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void draw(List<Quad> q,SkasaAttack a,long now){
        SkasaMoves move=a.move();boolean warning=now<a.release();Vec3 c=a.origin().add(0,.05,0);
        double age=(now-a.release())/(double)move.active,progress=Math.clamp((now-a.started)/(double)move.windup,0,1);
        int alpha=warning?(int)(35+45*progress):a.contacting(now)?130:35;
        switch(move){
            case SLAM,TRIPLE -> {
                int pulse=a.pulse(now);
                if(warning)half(q,a,a.side(0),60);
                else{
                    if(a.contacting(now)){half(q,a,a.side(pulse),125);fault(q,a,a.side(pulse),now-a.contactAt(pulse));}
                    if(move==TRIPLE&&pulse+1<move.hits)half(q,a,a.side(pulse+1),45);
                }
            }
            case CLAW -> {
                Vec3 d=new Vec3(a.side*.70710678,0,-.70710678);
                fan(q,c,d,move.range+.3,Math.PI/4+.04,DARK,alpha);
                if(!warning){
                    Vec3 center=c.add(a.side*2,1.2,-2);
                    for(int i=0;i<3;i++)line(q,center.add(-a.side*1.4+i*.25,.7,-1),center.add(a.side*1.5+i*.25,-.8,1),.055,WHITE,210);
                }
            }
            case PUNCH,KICK,SPIDER_BITE -> {
                fan(q,c,a.direction(),move.range+.3,Math.PI/3,DARK,alpha);
                if(!warning)ring(q,c.add(a.direction().scale(move.range*.7)),.5,.09,WHITE,180);
            }
            case NASRA_KICK,HATCHLING_CHARGE,ICE_LINE -> {
                double width=move==ICE_LINE?1.5:2.7;Vec3 end=c.add(a.direction().scale(move.range));
                if(warning)rect(q,c,end,width,DARK,alpha);
                else{
                    Vec3 tip=a.position(now).add(0,move==ICE_LINE?.7:.35,0);
                    Vec3 tail=tip.subtract(a.direction().scale(move==ICE_LINE?1.3:2));
                    line(q,tail,tip,move==ICE_LINE?.1:.2,ICE,210);box(q,tip,.17,.22,.17,WHITE,225);
                    if(move!=ICE_LINE)ring(q,tip.add(0,-.3,0),.9,.09,ICE,140);
                }
            }
            case NASRA_CYCLONE,VORTEX -> {
                disc(q,c,move.range+.3,DARK,alpha);ring(q,c,move.range+.3,.07,ICE,200);
                if(move==VORTEX){
                    // Inward trails distinguish suction from the stationary roar charge.
                    for(int i=0;i<16;i++){
                        double phase=(now/900d+i*.17)%1,angle=i*Math.PI/8;
                        Vec3 outer=c.add(Math.cos(angle)*(4+7*(1-phase)),.15+phase,Math.sin(angle)*(4+7*(1-phase)));
                        Vec3 inner=c.add(Math.cos(angle)*(3+6*(1-phase)),.2+phase,Math.sin(angle)*(3+6*(1-phase)));
                        line(q,outer,inner,.035,WHITE,warning?90:165);
                    }
                }
                if(!warning)for(int layer=0;layer<7;layer++){
                    double radius=move.range*(.25+layer*.12),height=.2+layer*.38,spin=now/150d+layer*.6;
                    for(int i=0;i<8;i++){
                        double angle=spin+i*Math.PI/4;Vec3 p=c.add(Math.cos(angle)*radius,height,Math.sin(angle)*radius);
                        line(q,p,p.add(-Math.sin(angle)*.65,.1,Math.cos(angle)*.65),.045,layer%2==0?WHITE:ICE,175);
                    }
                }
            }
            case ROAR -> {
                fan(q,c,a.direction(),move.range+.3,Math.PI/3,DARK,alpha);
                if(warning){
                    box(q,c.add(-.7,2,0),.12,.08,.08,0xff596f,200);
                    box(q,c.add(.7,2,0),.12,.08,.08,0xff596f,200);
                }else{
                    double pulse=Math.clamp((now-a.contactAt(a.pulse(now)))/400d,0,1);
                    fan(q,c.add(0,.55,0),a.direction(),move.range*pulse,Math.PI/3,WHITE,(int)(150*(1-pulse)));
                    ring(q,c.add(a.direction().scale(2)),1+pulse*3,.12,ICE,(int)(180*(1-pulse)));
                }
            }
            case BREATH,ROUTE_BREATH -> {
                if(move==ROUTE_BREATH)rect(q,c,c.add(a.direction().scale(move.range)),6.6,DARK,alpha);
                else{
                    fan(q,c,a.direction(),move.range+.3,Math.toRadians(75),DARK,alpha);
                    for(int side:new int[]{-1,1})rect(q,new Vec3(a.cell*512+side*4.15,a.y+.065,146),new Vec3(a.cell*512+side*4.15,a.y+.065,151),3.7,SAFE,warning?70:100);
                }
                if(warning){
                    for(int i=0;i<7;i++){
                        double angle=i*Math.PI*2/7+now/500d;
                        Vec3 p=c.add(Math.cos(angle)*(.7+.6*(1-progress)),1.4+Math.sin(angle)*.4,0);
                        box(q,p,.055,.1,.055,WHITE,175);
                    }
                }else{
                    Vec3 side=new Vec3(a.dz,0,-a.dx);
                    for(int i=0;i<26;i++){
                        double phase=(age*3+i*.137)%1,distance=phase*move.range;
                        double spread=move==ROUTE_BREATH?2.6:Math.min(15,distance*.7);
                        Vec3 p=c.add(a.direction().scale(distance)).add(side.scale(Math.sin(i*7.3)*spread)).add(0,.25+(i%4)*.32,0);
                        if(move==BREATH&&a.safeBreath(p))continue;
                        line(q,p,p.subtract(a.direction().scale(.7)),.065,i%3==0?WHITE:ICE,150);
                        if(i%3==0)box(q,p,.09,.22,.09,WHITE,160);
                    }
                }
            }
        }
    }
    private static void half(List<Quad> q,SkasaAttack a,int side,int alpha){
        double center=a.cell*512+side*9.5;
        rect(q,new Vec3(center,a.y+.055,120.7),new Vec3(center,a.y+.055,151.3),19.6,DARK,alpha);
        for(int z=124;z<=148;z+=6)line(q,new Vec3(a.cell*512,a.y+.075,z),new Vec3(a.cell*512+side*18.5,a.y+.075,z),.025,ICE,105);
    }
    private static void fault(List<Quad> q,SkasaAttack a,int side,long elapsed){
        double age=Math.clamp(elapsed/300d,0,1);
        for(int i=0;i<14;i++){
            double x=a.cell*512+side*(1+(i%4)*4.8),z=123+(i/4)*8;
            Vec3 p=new Vec3(x,a.y+.12+Math.sin(age*Math.PI)*.35,z);
            line(q,p.add(-.5,0,-.5),p.add(.6,.15,.8),.065,WHITE,(int)(210*(1-age)));
        }
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame f=event.getLevelRenderState().getRenderData(FRAME);if(f!=null)WorldQuads.submit(event,f.quads,f.fade);}
    private SkasaVisuals(){}
}
