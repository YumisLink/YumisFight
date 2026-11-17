package cn.yumis.fight.modules.arkguard.client;

import cn.yumis.fight.modules.arkguard.api.*;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.client.DamageNumber;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** All tracks are disposable presentation of committed world-unit facts. */
public final class ArkVisuals {
    private record Unit(UUID id,int entity,double maximum,double health,String mode,double skill,double rate,long at){}
    private record Effect(String kind,String style,int entity,UUID id,int victim,UUID victimId,Vec3 from,Vec3 to,
                          long at,long duration,int element,int index,boolean chant){}
    private record Frame(List<Quad> quads,boolean fade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","ark_battle"));
    private static final Map<Integer,Unit> units=new HashMap<>();
    private static final List<Effect> effects=new ArrayList<>();
    private static final List<DamageNumber> numbers=new ArrayList<>();
    private static Object world;
    private static String round="",stage="";
    private ArkVisuals(){}

    public static void register(){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArkVisuals::extract);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArkVisuals::submit);
    }
    static void clear(){units.clear();effects.clear();numbers.clear();round=stage="";world=null;}
    private static void world(){if(world!=Minecraft.getInstance().level){clear();world=Minecraft.getInstance().level;}}
    private static boolean inside(){
        var mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&ArkWar.contains(mc.level,mc.player.blockPosition());
    }
    private static void scene(JsonObject packet){
        world();String nextRound=packet.get("round").getAsString(),nextStage=packet.has("stage")?packet.get("stage").getAsString():"";
        if(!round.equals(nextRound)){numbers.clear();effects.clear();units.clear();}
        else if(!stage.equals(nextStage)){effects.clear();units.clear();}
        round=nextRound;stage=nextStage;
    }
    static void state(JsonObject packet){
        scene(packet);units.clear();if(!packet.has("actors"))return;
        long at=packet.get("at").getAsLong();
        for(var value:packet.getAsJsonArray("actors")){
            var actor=value.getAsJsonObject();int id=actor.get("entity").getAsInt();
            units.put(id,new Unit(UUID.fromString(actor.get("id").getAsString()),id,actor.get("maximum").getAsDouble(),actor.get("health").getAsDouble(),
                actor.has("skillMode")?actor.get("skillMode").getAsString():"",actor.has("skill")?actor.get("skill").getAsDouble():0,
                actor.has("skillRate")?actor.get("skillRate").getAsDouble():0,at));
        }
    }
    static void receive(JsonObject packet){
        scene(packet);if(!inside())return;
        var mc=Minecraft.getInstance();long localNow=ClientUi.now(),serverNow=ClientUi.current().serverNow();
        for(var value:packet.getAsJsonArray("effects")){
            var event=value.getAsJsonObject();String kind=event.get("kind").getAsString();
            UUID id=UUID.fromString(event.get("target").getAsString());int entity=event.get("entity").getAsInt(),element=event.get("element").getAsInt();
            long at=event.get("at").getAsLong(),duration=kind.equals("hit")?300:event.get("duration").getAsLong();
            Vec3 point=point(event,"");
            if(kind.equals("hit")){
                long age=Math.max(0,serverNow-at);
                if(age<DamageNumber.LIFETIME_MILLIS)numbers.add(new DamageNumber(event.get("amount").getAsDouble(),localNow+DamageNumber.LIFETIME_MILLIS-age,
                    event.get("enemy").getAsBoolean()?0xFFF0CF:0xFF7777,point,mc.level.dimension().identifier().toString(),id,event.get("critical").getAsBoolean()));
                var unit=units.get(entity);
                if(unit!=null&&unit.id().equals(id)&&event.has("health"))units.put(entity,new Unit(id,entity,unit.maximum(),event.get("health").getAsDouble(),unit.mode(),unit.skill(),unit.rate(),unit.at()));
                if(!event.get("impact").getAsBoolean())continue;
            }else if(kind.equals("cast")&&event.has("skillMode")){
                var unit=units.get(entity);
                if(unit!=null&&unit.id().equals(id))units.put(entity,new Unit(id,entity,unit.maximum(),unit.health(),event.get("skillMode").getAsString(),event.get("skill").getAsDouble(),event.get("skillRate").getAsDouble(),at));
            }
            if(serverNow>=at+duration)continue;
            boolean projectile=kind.equals("projectile");
            effects.add(new Effect(kind,projectile?event.get("style").getAsString():"",entity,id,
                projectile?event.get("victim").getAsInt():-1,projectile?UUID.fromString(event.get("victimId").getAsString()):null,
                point,projectile?point(event,"end"):point,at,duration,element,event.has("index")?event.get("index").getAsInt():0,event.has("chant")&&event.get("chant").getAsBoolean()));
        }
    }
    static void tick(){
        world();if(!inside()){clear();return;}
        long now=ClientUi.current().serverNow();effects.removeIf(effect->{
            if(now>=effect.at()+effect.duration())return true;
            if(!effect.chant())return false;
            var actor=entity(effect.entity(),effect.id());var unit=units.get(effect.entity());
            return actor!=null&&CombatContentTypes.controlled(actor)||unit!=null&&unit.id().equals(effect.id())&&unit.at()>effect.at()&&!unit.mode().equals("咏唱");
        });
        long localNow=ClientUi.now();numbers.removeIf(number->number.expires()<=localNow);
    }
    private static Vec3 point(JsonObject event,String prefix){return new Vec3(event.get(prefix+"x").getAsDouble(),event.get(prefix+"y").getAsDouble(),event.get(prefix+"z").getAsDouble());}
    private static LivingEntity entity(int id,UUID uuid){
        var mc=Minecraft.getInstance();var raw=mc.level==null?null:mc.level.getEntity(id);
        return raw instanceof LivingEntity living&&living.getUUID().equals(uuid)&&living.isAlive()?living:null;
    }
    private static Vec3 center(LivingEntity entity){return entity.getPosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0,entity.getBbHeight()*.6,0);}
    private static int color(int element){return switch(element){case 0->0xFFE6A0;case 1->0xFF783E;case 2->0x80DDFF;case 3->0xBF7AFF;default->0xA5BEFF;};}

    static void hud(GuiGraphicsExtractor graphics){
        world();var mc=Minecraft.getInstance();if(!inside()||mc.gui.hud.isHidden()||mc.gui.screen()!=null)return;
        var camera=mc.gameRenderer.mainCamera();var font=UiFonts.font();long now=ClientUi.current().serverNow();
        for(var unit:units.values()){
            var actor=entity(unit.entity(),unit.id());
            if(actor==null||actor.isInvisible()||!actor.isPickable())continue;
            Vec3 anchor=center(actor).add(0,actor.getBbHeight()*.4+.85,0),relative=anchor.subtract(camera.position());var forward=camera.forwardVector();
            if(relative.x*forward.x()+relative.y*forward.y()+relative.z*forward.z()<=0)continue;
            var screen=mc.gameRenderer.projectPointToScreen(anchor);
            if(!Double.isFinite(screen.x)||!Double.isFinite(screen.y)||Math.abs(screen.x)>1||Math.abs(screen.y)>1)continue;
            int width=62,x=(int)Math.round((screen.x+1)*.5*graphics.guiWidth())-width/2,y=(int)Math.round((1-screen.y)*.5*graphics.guiHeight());
            double health=Math.clamp(unit.health(),0,unit.maximum());
            bar(graphics,x,y,width,7,health/Math.max(1,unit.maximum()),0xFFBC4859);
            String text=NumberText.damage(health)+" / "+NumberText.damage(unit.maximum());
            float scale=Math.min(.65f,(width-4f)/Math.max(1,font.width(text)));
            graphics.pose().pushMatrix();graphics.pose().translate(x+(width-font.width(text)*scale)/2,y+.5f);graphics.pose().scale(scale,scale);
            graphics.text(font,text,0,0,0xFFFFFFFF,true);graphics.pose().popMatrix();
            if(!unit.mode().isEmpty()){
                double skill=Math.clamp(unit.skill()+Math.clamp(now-unit.at(),0,600)*unit.rate(),0,1);
                bar(graphics,x,y+8,width,6,skill,0xFF479EE8);
                String label=unit.mode().equals("自动技能")?(skill>=1?"自动 · 就绪":"自动 · 冷却"):unit.mode();
                float labelScale=.55f;
                graphics.pose().pushMatrix();graphics.pose().translate(x+(width-font.width(label)*labelScale)/2,y+8f);graphics.pose().scale(labelScale,labelScale);
                graphics.text(font,label,0,0,0xFFFFFFFF,true);graphics.pose().popMatrix();
            }
        }
        DamageNumber.render(graphics,numbers);
    }
    private static void bar(GuiGraphicsExtractor graphics,int x,int y,int width,int height,double fraction,int color){
        graphics.fill(x-1,y-1,x+width+1,y+height+1,0xE0171C29);graphics.fill(x,y,x+width,y+height,0xE03B2534);
        if(fraction>0)graphics.fill(x,y,x+(int)Math.round(width*fraction),y+height,color);
    }

    private static void extract(ExtractLevelRenderStateEvent event){
        world();List<Quad> quads=new ArrayList<>();long now=ClientUi.current().serverNow();
        if(inside()&&ClientUi.current().ready())for(var effect:effects){
            double phase=Math.clamp((now-effect.at())/(double)effect.duration(),0,1);if(phase>=1)continue;
            int color=color(effect.element());
            switch(effect.kind()){
                case "projectile"->{
                    var victim=entity(effect.victim(),effect.victimId());
                    Vec3 end=victim==null?effect.to():center(victim),delta=end.subtract(effect.from()),point=effect.from().add(delta.scale(phase));
                    if(effect.index()>0)point=point.add(0,Math.sin(phase*Math.PI)*.12*effect.index(),0);
                    if(effect.style().equals("stone"))point=point.add(0,Math.sin(phase*Math.PI)*.7,0);
                    if(effect.style().equals("arrow"))arrow(quads,point,delta.normalize(),color);
                    else{
                        orb(quads,point,effect.style().equals("stone")?.13:.18,effect.style().equals("stone")?0x99897B:color,230);
                        line(quads,point,point.subtract(delta.normalize().scale(.6)),.07,color,140);
                    }
                }
                case "cast"->{
                    var actor=entity(effect.entity(),effect.id());if(actor==null||actor.isInvisible()||effect.chant()&&CombatContentTypes.controlled(actor))continue;
                    Vec3 center=center(actor),feet=center.add(0,-actor.getBbHeight()*.6+.06,0);
                    int alpha=(int)(195*Math.min(1,(1-phase)*6));double spin=(now-effect.at())*.004;
                    ring(quads,feet,.62,.065,color,alpha);
                    ring(quads,center,.4+.06*Math.sin(spin),.025,color,alpha/2);
                    for(int i=0;i<4;i++){
                        double angle=spin+i*Math.PI/2;Vec3 mote=feet.add(Math.cos(angle)*.48,.25+(spin*.3+i*.4)%1.4,Math.sin(angle)*.48);
                        orb(quads,mote,.055,color,alpha);line(quads,mote,mote.add(0,-.22,0),.025,color,alpha/2);
                    }
                }
                case "hit"->{
                    double radius=.12+phase*.55;int alpha=(int)(245*(1-phase));Vec3 point=effect.from();
                    for(int i=0;i<8;i++){
                        double angle=i*Math.PI/4;Vec3 direction=new Vec3(Math.cos(angle),Math.sin(angle),Math.sin(angle*3)*.45);
                        line(quads,point.add(direction.scale(radius*.4)),point.add(direction.scale(radius)),.035,color,alpha);
                    }
                    orb(quads,point,.17*(1-phase),0xFFF6DC,alpha);
                }
                default->{}
            }
        }
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(quads),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void submit(SubmitCustomGeometryEvent event){var frame=event.getLevelRenderState().getRenderData(FRAME);if(frame!=null)WorldQuads.submit(event,frame.quads(),frame.fade());}
    private static void line(List<Quad> quads,Vec3 start,Vec3 end,double width,int color,int alpha){
        Vec3 direction=end.subtract(start).normalize(),side=direction.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<.001)side=new Vec3(1,0,0);else side=side.normalize();
        side=side.scale(width*.5);Vec3 up=direction.cross(side).normalize().scale(width*.5);
        face(quads,start.subtract(side),start.add(side),end.add(side),end.subtract(side),color,alpha);
        face(quads,start.subtract(up),start.add(up),end.add(up),end.subtract(up),color,alpha);
    }
    private static void face(List<Quad> quads,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,int alpha){
        quads.add(new Quad(a,b,c,d,color,alpha));quads.add(new Quad(d,c,b,a,color,alpha));
    }
    private static void ring(List<Quad> quads,Vec3 center,double radius,double width,int color,int alpha){
        for(int i=0;i<32;i++){
            double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
            face(quads,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),center.add(Math.cos(b)*radius,0,Math.sin(b)*radius),
                center.add(Math.cos(b)*(radius-width),0,Math.sin(b)*(radius-width)),center.add(Math.cos(a)*(radius-width),0,Math.sin(a)*(radius-width)),color,alpha);
        }
    }
    private static void orb(List<Quad> quads,Vec3 center,double radius,int color,int alpha){
        Vec3 top=center.add(0,radius,0),bottom=center.add(0,-radius,0);
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4,b=(i+1)*Math.PI/4;Vec3 first=center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),second=center.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
            face(quads,top,first,second,top,color,alpha);face(quads,bottom,second,first,bottom,color,alpha);
        }
    }
    private static void arrow(List<Quad> quads,Vec3 point,Vec3 direction,int color){
        Vec3 tail=point.subtract(direction.scale(.65)),side=direction.cross(new Vec3(0,1,0)).normalize().scale(.09);
        Vec3 up=direction.cross(side).normalize().scale(.09),neck=point.subtract(direction.scale(.18));
        line(quads,tail,neck,.035,0xC69C6D,255);
        face(quads,point,neck.add(side),neck.subtract(side),point,0xE7ECF2,255);
        face(quads,point,neck.add(up),neck.subtract(up),point,0xE7ECF2,255);
        Vec3 feather=tail.add(direction.scale(.2));
        face(quads,tail,tail.add(side),feather,tail,color,235);face(quads,tail,tail.subtract(side),feather,tail,color,235);
        face(quads,tail,tail.add(up),feather,tail,0xF3EDD9,235);face(quads,tail,tail.subtract(up),feather,tail,0xF3EDD9,235);
        line(quads,tail,tail.subtract(direction.scale(.4)),.025,color,100);
    }
}
