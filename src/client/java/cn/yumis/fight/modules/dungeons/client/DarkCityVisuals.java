package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import java.util.function.Supplier;
import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/** The gate's warnings and concrete skull/horse silhouettes; no hit or health state is written here. */
public final class DarkCityVisuals {
    private record Frame(List<Quad> quads,boolean fade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","dark_city_effects"));
    private static final Map<String,GblAttack> tracks=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new LinkedHashMap<>();
    private static Supplier<String> instance=()->"";
    private static Object world;
    private static final int DARK=0x9d7bcc,BONE=0xe0d8b8,SOUL=0x75d6b4,GOLD=0xc8ac69;
    public static void register(Supplier<String> current){instance=current;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DarkCityVisuals::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DarkCityVisuals::submit);}
    static void clear(){tracks.clear();closed.clear();world=Minecraft.getInstance().level;}
    static void receive(GblAttack a){
        if(world!=Minecraft.getInstance().level)clear();long now=ClientUi.current().serverNow();
        if(a.remove){tracks.remove(a.id);closed.put(a.id,now+10000);}else if(!closed.containsKey(a.id))tracks.putIfAbsent(a.id,a);
    }
    private static void extract(ExtractLevelRenderStateEvent event){
        if(world!=Minecraft.getInstance().level)clear();
        var mc=Minecraft.getInstance();List<Quad> q=new ArrayList<>();long now=ClientUi.current().serverNow();String here=instance.get();
        closed.values().removeIf(until->now>until);tracks.values().removeIf(a->!a.instance.equals(here)||now>a.end()+300);
        if(mc.level!=null&&mc.player!=null&&ClientUi.current().ready()){
            for(var a:tracks.values())if(now>=a.started&&now<=a.end())draw(q,a,now);
            for(var raw:mc.level.entitiesForRendering())if(raw instanceof DungeonEnemy e&&DarkCityEnemyKind.of(e)==DarkCityEnemyKind.TINGO&&e.guarded()&&e.isAlive()&&e.distanceToSqr(mc.player)<4096){
                Vec3 c=e.position().add(0,.06,0);ring(q,c,1.05,.075,GOLD,180);
                for(int i=0;i<8;i++){double angle=i*Math.PI/4+now/1600d;box(q,c.add(Math.cos(angle),.5+Math.sin(now/200d+i)*.08,Math.sin(angle)),.045,.11,.045,GOLD,180);}
            }
        }else tracks.clear();
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void draw(List<Quad> q,GblAttack a,long now){
        DarkCityMoves move=DarkCityMoves.valueOf(a.kind);boolean warning=now<a.release();
        int color=move==DarkCityMoves.NIGHTMARE?SOUL:move==DarkCityMoves.GUARD?GOLD:a.element==3?DARK:BONE;
        Vec3 c=a.origin().add(0,.055-move.height(),0),end=new Vec3(a.ex,c.y,a.ez),direction=end.subtract(c).normalize();
        int alpha=warning?45:110;
        switch(a.shape){
            case "FAN" -> fan(q,c,direction,a.radius,a.halfAngle,color,alpha);
            case "CIRCLE" -> {disc(q,c,a.radius,color,warning?30:65);ring(q,c,a.radius,.09,color,185);if(!warning)for(int i=0;i<3;i++){double angle=now/130d+i*Math.PI*2/3;line(q,c.add(0,.75,0),c.add(Math.cos(angle)*a.radius,.75,Math.sin(angle)*a.radius),.075,BONE,180);}}
            case "WAVE" -> {
                if(warning){disc(q,c,a.radius,color,20);ring(q,c,a.radius,.065,color,180);}
                else{double r=Math.max(.1,a.waveRadius(now));ring(q,c,r,a.width,DARK,120);ring(q,c.add(0,.18,0),r,.1,BONE,210);}
            }
            case "CHARGE" -> {
                if(warning)rect(q,c,end,a.width*2,color,55);
                else{Vec3 at=a.position(now).add(0,.06,0);line(q,a.origin().add(0,.06,0),at,.12,SOUL,95);ring(q,at,1.05,.08,BONE,120);}
            }
            case "LINE" -> {
                if(warning)rect(q,c,end,a.width*2,color,40);
                else if(move==DarkCityMoves.SKULL)skull(q,a.position(now),direction);
                else if(move==DarkCityMoves.NIGHTMARE)horse(q,a.position(now).add(0,-move.height(),0),direction,now);
                else{
                    Vec3 tip=a.position(now);line(q,tip.subtract(direction.scale(.9)),tip,.035,BONE,235);
                    Vec3 side=new Vec3(direction.z,0,-direction.x);line(q,tip.subtract(direction.scale(.18)).add(side.scale(.13)),tip,.035,GOLD,240);line(q,tip.subtract(direction.scale(.18)).subtract(side.scale(.13)),tip,.035,GOLD,240);
                }
            }
            case "GUARD" -> {ring(q,c,1.2,.08,GOLD,warning?125:210);if(warning)disc(q,c,1.2,GOLD,25);}
            default -> { }
        }
    }
    private static Vec3 local(Vec3 origin,Vec3 d,double x,double y,double z){return origin.add(d.z*x+d.x*z,y,-d.x*x+d.z*z);}
    private static void cuboid(List<Quad> q,Vec3 origin,Vec3 d,double x,double y,double z,double hx,double hy,double hz,int color,int alpha){
        Vec3[] v={local(origin,d,x-hx,y-hy,z-hz),local(origin,d,x+hx,y-hy,z-hz),local(origin,d,x+hx,y+hy,z-hz),local(origin,d,x-hx,y+hy,z-hz),local(origin,d,x-hx,y-hy,z+hz),local(origin,d,x+hx,y-hy,z+hz),local(origin,d,x+hx,y+hy,z+hz),local(origin,d,x-hx,y+hy,z+hz)};
        for(int[] f:new int[][]{{4,5,6,7},{1,0,3,2},{5,1,2,6},{0,4,7,3},{3,7,6,2},{0,1,5,4}})q.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color,alpha));
    }
    private static void skull(List<Quad> q,Vec3 c,Vec3 d){
        cuboid(q,c,d,0,0,0,.18,.19,.15,BONE,245);cuboid(q,c,d,0,-.19,.025,.13,.045,.1,0xbeb697,240);
        for(int side:new int[]{-1,1})cuboid(q,c,d,side*.085,.035,.155,.052,.052,.012,0x251d30,250);
        cuboid(q,c,d,0,-.04,.16,.024,.035,.012,0x251d30,250);
    }
    private static void horse(List<Quad> q,Vec3 c,Vec3 d,long now){
        cuboid(q,c,d,0,.95,0,.31,.32,.66,0x325447,135);
        cuboid(q,c,d,0,1.35,.48,.22,.36,.2,SOUL,150);
        cuboid(q,c,d,0,1.66,.73,.22,.19,.28,0x9ce9ca,185);
        cuboid(q,c,d,0,1.56,1,.17,.12,.14,BONE,180);
        for(int side:new int[]{-1,1}){
            cuboid(q,c,d,side*.15,1.94,.61,.055,.14,.065,SOUL,175);
            cuboid(q,c,d,side*.225,1.71,.85,.025,.04,.055,0xe4ffd8,235);
            for(int leg:new int[]{-1,1}){
                double stride=Math.sin(now/85d+side*1.2+leg*2)*.22;
                cuboid(q,c,d,side*.24,.43+Math.abs(stride)*.5,leg*.47+stride,.08,.35,.085,SOUL,155);
                cuboid(q,c,d,side*.24,.12+Math.abs(stride),leg*.47+stride,.095,.065,.13,0xd1f7d9,180);
            }
        }
        for(int i=0;i<5;i++)cuboid(q,c,d,Math.sin(now/130d+i)*.06,1.25+i*.11,.19+i*.095,.055,.16,.09,SOUL,130);
        for(int i=0;i<4;i++)cuboid(q,c,d,Math.sin(now/110d+i)*.1,1.1-i*.1,-.68-i*.15,.08,.09,.18,SOUL,110-i*12);
        Vec3 tail=c.subtract(d.scale(1.6));line(q,tail.add(0,.4,0),c.add(0,.6,0),.07,SOUL,55);
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame frame=event.getLevelRenderState().getRenderData(FRAME);if(frame!=null)WorldQuads.submit(event,frame.quads,frame.fade);}
    private DarkCityVisuals(){}
}
