package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import java.util.function.Supplier;
import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/** 落雷凯诺 bossAction 的命中演出：只画 source=goblin_shaman 的六种攻击，预警归 EnemyTelegraphs、命中判定仍归本人客户端。 */
public final class KenoVisuals {
    record Action(String id,String instance,String kind,boolean magic,Vec3 center,Vec3 aim,double radius,double halfAngle,long release,long active){}
    private record Frame(List<Quad> quads,boolean fade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","keno_effects"));
    private static final Set<String> KINDS=Set.of("LIGHTNING","ULTIMATE","RING","FAN","BASIC","CLAW");
    private static final long CHARGE=450,FLICKER=80;
    private static final Map<String,Action> actions=new LinkedHashMap<>();
    private static final Map<String,Long> cancelled=new HashMap<>();
    private static Supplier<String> instance=()->"";
    private static Object world;

    public static void register(Supplier<String> current){instance=current;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(KenoVisuals::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(KenoVisuals::submit);}
    public static void receive(JsonObject j){
        world();
        if(!j.has("source")||!"goblin_shaman".equals(j.get("source").getAsString()))return;
        String kind=j.has("kind")?j.get("kind").getAsString():"";
        if(!KINDS.contains(kind))return;
        String id=j.get("id").getAsString();
        if(cancelled.containsKey(id))return;
        actions.put(id,parse(j));
        while(actions.size()>256)actions.remove(actions.keySet().iterator().next());
    }
    public static void cancel(String id){world();actions.remove(id);cancelled.put(id,ClientUi.current().serverNow()+10000);}
    private static void world(){if(world!=Minecraft.getInstance().level){world=Minecraft.getInstance().level;actions.clear();cancelled.clear();}}
    static Action parse(JsonObject j){
        return new Action(j.get("id").getAsString(),j.getAsJsonObject("boss").get("instance").getAsString(),j.get("kind").getAsString(),j.get("magic").getAsBoolean(),
            new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble()),
            new Vec3(j.get("dx").getAsDouble(),0,j.get("dz").getAsDouble()),
            j.get("radius").getAsDouble(),j.get("halfAngle").getAsDouble(),
            j.get("startedAt").getAsLong()+j.get("windupTicks").getAsLong()*50,j.get("activeTicks").getAsLong()*50);
    }

    private static void extract(ExtractLevelRenderStateEvent event){
        world();List<Quad> q=new ArrayList<>();
        if(Minecraft.getInstance().player!=null&&ClientUi.current().ready()){
            long now=ClientUi.current().serverNow();String here=instance.get();
            cancelled.values().removeIf(until->until<now);
            actions.values().removeIf(a->!a.instance().equals(here)||expired(a,now));
            for(Action a:actions.values())draw(q,a,now);
        }else actions.clear();
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame f=event.getLevelRenderState().getRenderData(FRAME);if(f!=null)WorldQuads.submit(event,f.quads(),f.fade());}

    /** 一次攻击的绘制入口：前摇末段蓄能、release 后命中演出；帧内确定性随机保证同帧不闪跳。 */
    static void draw(List<Quad> q,Action a,long now){
        if(now>=a.release()){strikePhase(q,a,now);return;}
        if(a.release()-now<=CHARGE)chargePhase(q,a,now);
    }
    /** 各招演出余晖窗口（毫秒），到期由 extract 清除。 */
    static long linger(Action a){return switch(a.kind()){case "ULTIMATE"->480L;case "RING"->520L;case "LIGHTNING"->420L;case "BASIC"->200L;default->280L;};}
    static boolean expired(Action a,long now){return now>a.release()+linger(a);}

    private static void chargePhase(List<Quad> q,Action a,long now){
        double cp=1-(a.release()-now)/(double)CHARGE;
        int gold=a.magic?0xffd75e:0xffcf6b;
        Vec3 ground=a.center().add(0,.05,0);
        switch(a.kind()){
            case "RING"->{
                for(int i=0;i<4;i++){
                    double angle=(now/FLICKER)*.61+i*Math.PI/2;
                    Vec3 p=ground.add(Math.cos(angle)*(1+a.radius()*.12*cp),0,Math.sin(angle)*(1+a.radius()*.12*cp));
                    line(q,p.add(0,.2,0),p.add(Math.cos(angle+.7)*.8,1.1,Math.sin(angle+.7)*.8),.05,gold,(int)(60+90*cp));
                }
            }
            case "FAN","BASIC","CLAW"->arcLine(q,ground,heading(a.aim()),a.radius(),a.halfAngle(),.04,gold,(int)(26*cp));
            default->{
                line(q,ground,ground.add(0,a.kind().equals("ULTIMATE")?16:13,0),.05,gold,(int)(26+74*cp));
                ring(q,ground,.22+.5*cp,.07,gold,(int)(40+50*cp));
            }
        }
    }

    private static void strikePhase(List<Quad> q,Action a,long now){
        long dt=now-a.release(),span=linger(a);
        if(dt>=span)return;
        double p=dt/(double)span;
        Vec3 ground=a.center().add(0,.05,0);
        switch(a.kind()){
            case "LIGHTNING"->lightningImpact(q,a,ground,p,now/FLICKER,false);
            case "ULTIMATE"->lightningImpact(q,a,ground,p,now/FLICKER,true);
            case "RING"->ringImpact(q,a,ground,p,now/FLICKER);
            default->slashImpact(q,a,ground,p);
        }
    }

    /** 落雷命中：锯齿主雷柱＋分支闪电＋闪光盘＋冲击环＋残留光柱；grand 为大招的双环与侧雷。 */
    private static void lightningImpact(List<Quad> q,Action a,Vec3 ground,double p,long step,boolean grand){
        int fade=(int)(255*(1-p));
        int gold=0xffd75e,white=0xfff6cf;
        double ease=1-Math.pow(1-p,3);
        disc(q,ground,(grand?a.radius()*.7:a.radius()*.55)*(1-.25*p),white,(int)(fade*.75));
        ring(q,ground,a.radius()*(.25+.95*ease),Math.max(.06,.16-.1*p),gold,(int)(fade*.9));
        if(grand)ring(q,ground,a.radius()*(.15+.7*Math.max(0,(p-.15)/.85)),.2,white,(int)(fade*.6));
        WorldQuads.beam(q,ground,ground.add(0,grand?2:1.2,0),white,.1,(int)(fade*.5),gold,.28,(int)(fade*.25));
        bolt(q,a.id(),ground,step,grand?16:13,grand?.16:.1,fade,grand?11:9,grand?1.6:1.1);
        if(grand)for(int side=0;side<2;side++){
            double angle=step*1.7+side*2.094;
            bolt(q,a.id()+"#"+side,ground.add(Math.cos(angle)*a.radius()*.5,0,Math.sin(angle)*a.radius()*.5),step,10,.07,(int)(fade*.6),7,1);
        }
    }

    /** 锯齿雷柱：每 FLICKER 毫秒换一次确定性抖动；白芯＋金晕双层与两条分支。 */
    private static void bolt(List<Quad> q,String seed,Vec3 ground,long step,double height,double width,int fade,int segments,double sway){
        Random random=new Random(seed.hashCode()*1129L+step*613L);
        Vec3[] points=new Vec3[segments+1];
        for(int i=0;i<=segments;i++){
            double t=(double)i/segments;
            double jx=(i==0||i==segments)?0:(random.nextDouble()-.5)*sway*(1-.3*t);
            double jz=(i==0||i==segments)?0:(random.nextDouble()-.5)*sway*(1-.3*t);
            points[i]=ground.add(jx,height*(1-t),jz);
        }
        for(int i=0;i<segments;i++){
            line(q,points[i],points[i+1],width,0xfff6cf,fade);
            line(q,points[i],points[i+1],width*2.6,0xffd75e,(int)(fade*.45));
        }
        for(int branch=0;branch<2;branch++){
            Vec3 start=points[segments*(branch+1)/3];
            double angle=random.nextDouble()*Math.PI*2;
            Vec3 end=start.add(Math.cos(angle)*sway*1.3,-height/segments*(random.nextDouble()*2+1.5),Math.sin(angle)*sway*1.3);
            line(q,start,end,width*.8,0xfff6cf,(int)(fade*.8));
            line(q,start,end,width*2,0xffd75e,(int)(fade*.35));
        }
    }

    /** 雷环命中：金色主环扩散＋80ms 错峰白色副环＋中心闪光＋环缘竖电刺。 */
    private static void ringImpact(List<Quad> q,Action a,Vec3 ground,double p,long step){
        int gold=0xffd75e,white=0xfff6cf;
        double ease=1-Math.pow(1-p,3);
        double radius=.8+(a.radius()-.8)*ease;
        ring(q,ground,radius,Math.max(.07,.34*(1-p)+.06),gold,(int)(235*(1-p)));
        double p2=Math.max(0,(p-.18)/.82);
        if(p2>0)ring(q,ground,.8+(a.radius()-.8)*(1-Math.pow(1-p2,3)),Math.max(.05,.18*(1-p2)),white,(int)(180*(1-p2)));
        disc(q,ground,1.6*(1-.4*p),white,(int)(170*(1-p)));
        for(int i=0;i<6;i++){
            double angle=i*Math.PI/3+step*.37;
            Vec3 base=ground.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            int alpha=(int)(210*Math.max(0,1-p*2.2));
            if(alpha>0)line(q,base.add(0,.1,0),base.add(Math.cos(angle)*.45,1,Math.sin(angle)*.45),.05,gold,alpha);
        }
    }

    /** 扇击／普攻／雷爪命中：扇面高亮闪＋外缘弧线扫掠；id 哈希决定扫掠方向与雷爪三连的爪位差。 */
    private static void slashImpact(List<Quad> q,Action a,Vec3 ground,double p){
        boolean basic=a.kind().equals("BASIC"),claw=a.kind().equals("CLAW");
        int color=a.magic?0xffd75e:0xffcf6b;
        double fade=1-p;
        double heading=heading(a.aim())+sweep(a.id(),p)*(claw?.35:basic?.12:.2)+(claw?(Math.floorMod(a.id().hashCode(),3)-1)*.18:0);
        Vec3 dir=new Vec3(Math.cos(heading),0,Math.sin(heading));
        fan(q,ground,dir,a.radius()*(.92+.08*p),a.halfAngle(),color,(int)((basic?120:160)*fade));
        arcLine(q,ground,heading,a.radius()*(.96+.04*p),a.halfAngle(),.07,color,(int)(205*fade));
        if(!basic)arcLine(q,ground,heading,a.radius()*.68,a.halfAngle(),.05,0xfff6cf,(int)(190*fade));
    }

    /** 24 段弧线；用于挥击外缘与蓄能时的外沿微亮。 */
    private static void arcLine(List<Quad> q,Vec3 c,double heading,double radius,double half,double width,int color,int alpha){
        if(alpha<=0)return;
        for(int i=0;i<24;i++){
            double a0=heading-half+2*half*i/24,a1=heading-half+2*half*(i+1)/24;
            Vec3 u=c.add(Math.cos(a0)*radius,0,Math.sin(a0)*radius),v=c.add(Math.cos(a1)*radius,0,Math.sin(a1)*radius);
            line(q,u,v,width,color,alpha);
        }
    }
    private static double heading(Vec3 aim){return Math.atan2(aim.z,aim.x);}
    private static double sweep(String id,double p){return (p-.5)*2*(((id.hashCode()&1)==0)?-1:1);}
    private KenoVisuals(){}
}
