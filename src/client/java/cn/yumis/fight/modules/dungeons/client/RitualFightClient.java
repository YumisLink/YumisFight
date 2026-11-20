package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import static cn.yumis.fight.modules.dungeons.client.EnemyEffectGeometry.*;

/** World-positioned telegraphs and owner-side contacts for the demon altar. */
public final class RitualFightClient {
    private static final Gson JSON=new Gson();
    private static final Map<String,Fight> fights=new LinkedHashMap<>();
    private static final Map<String,Cast> casts=new LinkedHashMap<>();
    private static final ContextKey<List<Quad>> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","ritual_telegraphs"));
    private static Supplier<Life> localLife=Life::new;
    private static BooleanSupplier awakening=()->false;
    private static Object level;
    private static String epoch="";

    private record SafeSpot(String kind,double x,double y,double z,double radius,long until){
        Vec3 point(){return new Vec3(x,y,z);}
    }
    private record Fight(String runId,String name,String dimension,int level,double x,double y,double z,double radius,
                         String status,boolean ended,long at,long countdown,String hpText,Map<String,String> lines,List<SafeSpot> safeSpots){
        Vec3 center(){return new Vec3(x,y,z);}
        boolean contains(Vec3 position){Vec3 offset=position.subtract(center());return Math.abs(offset.y)<=8&&offset.horizontalDistanceSqr()<=radius*radius;}
    }
    private record Linger(long ms,long tickMs,double perTickCoefficient,int hitLimit,boolean magic,int element){}
    private record Move(String id,String runId,Target boss,String kind,long startedAt,long durationMs,long[] contactMs,
                        String shapeType,double radius,double range,double angle,double length,double width,double spacing,
                        double x,double y,double z,double dx,double dz,double arenaX,double arenaY,double arenaZ,double arenaRadius,
                        UUID lockedPlayer,double coefficient,boolean magic,int element,int hitLimit,Linger linger,
                        boolean blocksBehindCover,List<String> safeKinds,int selfStunSeconds,int selfSitSeconds,
                        int selfSlowSeconds,int selfSlowAmplifier,int selfRootSeconds,int selfHealDownSeconds,int selfDebtSeconds){
        Vec3 origin(){return new Vec3(x,y,z);}
        Vec3 direction(){Vec3 direction=new Vec3(dx,0,dz);return direction.lengthSqr()<.0001?new Vec3(1,0,0):direction.normalize();}
        Vec3 side(){var vector=direction();return new Vec3(-vector.z,0,vector.x);}
        long lastContact(){return contactMs.length==0?durationMs:contactMs[contactMs.length-1];}
        long end(){return startedAt+Math.max(durationMs,lastContact()+(linger==null?0:linger.ms()));}
    }
    private static final class Cast {
        final Move move;
        final Predicate<JsonObject> hit;
        int checked,hits,lingerChecked,lingerHits;
        boolean rooted;
        Cast(Move move,Predicate<JsonObject> hit){this.move=move;this.hit=hit;}
    }

    private RitualFightClient(){}

    public static void register(Supplier<Life> life,BooleanSupplier awakeningAtAltar){
        localLife=life;
        awakening=awakeningAtAltar;
        NeoForge.EVENT_BUS.addListener(RitualFightClient::tick);
        NeoForge.EVENT_BUS.addListener(RitualFightClient::extract);
        NeoForge.EVENT_BUS.addListener(RitualFightClient::submit);
    }

    private static void sync(){
        var minecraft=Minecraft.getInstance();
        var life=localLife.get();
        String next=ClientUi.current().session()+"/"+life.generation+"/"+life.instance;
        if(level!=minecraft.level||!next.equals(epoch)){
            level=minecraft.level;epoch=next;fights.clear();casts.clear();
        }
    }

    static void openMenu(JsonObject packet){
        sync();
        if(sameWorld(packet.get("dimension").getAsString())&&localLife.get().instance.isEmpty()){
            Minecraft.getInstance().gui.setScreen(new RitualMenuScreen(packet.deepCopy(),awakening.getAsBoolean()));
        }
    }

    static void run(JsonObject packet){
        sync();
        var fight=JSON.fromJson(packet,Fight.class);
        if(!sameWorld(fight.dimension()))return;
        if(fight.ended()){
            fights.remove(fight.runId());
            casts.values().removeIf(cast->cast.move.runId().equals(fight.runId()));
            var player=Minecraft.getInstance().player;
            if(player!=null&&fight.contains(player.position())){
                player.removeEffect(CombatContentTypes.RITUAL_ROOT);
                player.removeEffect(CombatContentTypes.RITUAL_DEBT);
                player.removeEffect(CombatContentTypes.HEAL_DOWN);
            }
        }else fights.put(fight.runId(),fight);
    }

    static void attack(JsonObject packet,Predicate<JsonObject> hit){
        sync();
        var move=JSON.fromJson(packet,Move.class);
        if(move.boss()!=null&&sameWorld(move.boss().dimension)&&move.shapeType()!=null
                &&ClientUi.current().serverNow()<=move.end()+200){
            casts.putIfAbsent(move.id(),new Cast(move,hit));
        }
    }

    static void cancel(String id){
        var cast=casts.remove(id);
        var player=Minecraft.getInstance().player;
        if(cast!=null&&cast.rooted&&player!=null)
            player.removeEffect(CombatContentTypes.RITUAL_ROOT);
    }

    static void clearDebt(){
        var player=Minecraft.getInstance().player;
        if(player!=null)player.removeEffect(CombatContentTypes.RITUAL_DEBT);
    }

    private static boolean sameWorld(String dimension){
        var world=Minecraft.getInstance().level;
        return world!=null&&world.dimension().identifier().toString().equals(dimension);
    }

    private static void tick(ClientTickEvent.Post event){
        sync();
        var minecraft=Minecraft.getInstance();
        long now=ClientUi.current().serverNow();
        fights.values().removeIf(fight->now>fight.at()+4000);
        if(minecraft.player==null||!ClientUi.current().ready()||localLife.get().dead||!localLife.get().instance.isEmpty()){
            casts.clear();return;
        }
        if(minecraft.isPaused())return;
        for(var iterator=casts.values().iterator();iterator.hasNext();){
            var cast=iterator.next();
            var move=cast.move;
            if(!sameWorld(move.boss().dimension)||now>move.end()+250){iterator.remove();continue;}
            while(cast.checked<move.contactMs().length&&now>=move.startedAt()+move.contactMs()[cast.checked]){
                int index=cast.checked++;
                long at=move.startedAt()+move.contactMs()[index];
                boolean rootTick=move.selfRootSeconds()>0&&index>0;
                if(cast.hits<move.hitLimit()&&now-at<=250
                        &&(rootTick?cast.rooted&&minecraft.player.hasEffect(CombatContentTypes.RITUAL_ROOT):inside(move,now,false))){
                    boolean landed=contact(cast,"hit/"+index,move.coefficient(),move.magic(),move.element(),at,true);
                    if(index==0&&move.selfRootSeconds()>0)
                        cast.rooted=landed&&minecraft.player.hasEffect(CombatContentTypes.RITUAL_ROOT);
                    cast.hits++;
                }
            }
            var linger=move.linger();
            if(linger!=null)while(cast.lingerHits<linger.hitLimit()&&(cast.lingerChecked+1)*linger.tickMs()<=linger.ms()){
                long at=move.startedAt()+move.lastContact()+(cast.lingerChecked+1)*linger.tickMs();
                if(now<at)break;
                int index=cast.lingerChecked++;
                if(now-at<=250&&inside(move,now,true)){
                    contact(cast,"linger/"+index,linger.perTickCoefficient(),linger.magic(),linger.element(),at,false);
                    cast.lingerHits++;
                }
            }
        }
    }

    private static boolean contact(Cast cast,String suffix,double coefficient,boolean magic,int element,long at,boolean applyEffects){
        var move=cast.move;
        var hit=new JsonObject();
        hit.addProperty("id",move.id()+"/"+suffix);
        hit.addProperty("clientContact",true);
        hit.add("boss",JSON.toJsonTree(move.boss()));
        hit.addProperty("kind",move.kind());
        hit.addProperty("coefficient",coefficient);
        hit.addProperty("magic",magic);
        hit.addProperty("element",element);
        hit.addProperty("occurredAt",at);
        if(applyEffects){
            int stun=Math.max(move.selfStunSeconds(),move.selfSitSeconds());
            if(stun>0)hit.addProperty("ritualStunTicks",stun*20);
            if(move.selfRootSeconds()>0&&suffix.equals("hit/0"))hit.addProperty("ritualRootTicks",move.selfRootSeconds()*20);
            if(move.selfSlowSeconds()>0){
                hit.addProperty("ritualSlowTicks",move.selfSlowSeconds()*20);
                hit.addProperty("ritualSlowAmplifier",move.selfSlowAmplifier());
            }
            if(move.selfHealDownSeconds()>0)hit.addProperty("ritualHealDownTicks",move.selfHealDownSeconds()*20);
            if(move.selfDebtSeconds()>0)hit.addProperty("ritualDebtTicks",move.selfDebtSeconds()*20);
        }
        return cast.hit.test(hit);
    }

    private static boolean inside(Move move,long now,boolean lingering){
        var minecraft=Minecraft.getInstance();
        var player=minecraft.player;
        if(player==null||player.isDeadOrDying()||player.isSpectator())return false;
        Vec3 offset=player.position().subtract(move.origin());
        Vec3 arena=player.position().subtract(new Vec3(move.arenaX(),move.arenaY(),move.arenaZ()));
        if(arena.horizontalDistanceSqr()>move.arenaRadius()*move.arenaRadius()||Math.abs(arena.y)>8
                ||Math.abs(offset.y)>3||move.lockedPlayer()!=null&&!move.lockedPlayer().equals(player.getUUID()))return false;
        if(!lingering&&(move.kind().equals("stop_clock")||move.kind().equals("chores_broom")||move.shapeType().equals("ring"))&&!player.onGround())return false;
        if(move.safeKinds()!=null){
            var stage=fights.get(move.runId());
            if(stage!=null&&stage.safeSpots()!=null)for(var spot:stage.safeSpots()){
                Vec3 distance=player.position().subtract(spot.point());
                if(now<spot.until()&&Math.abs(distance.y)<2&&move.safeKinds().contains(spot.kind())
                        &&distance.horizontalDistanceSqr()<=spot.radius()*spot.radius())return false;
            }
        }
        double along=offset.dot(move.direction()),across=Math.abs(offset.dot(move.side()));
        double distance=offset.horizontalDistance();
        boolean geometry=switch(move.shapeType()){
            case "circle","ring" -> distance<=move.radius()+.25;
            case "fan" -> distance<=move.range()+.25&&(distance<.25||along/distance>=Math.cos(Math.toRadians(move.angle())*.5));
            case "lane" -> along>=-.25&&along<=move.length()+.25&&across<=move.width()*.5+.25;
            case "double_lane" -> along>=-.25&&along<=move.length()+.25&&Math.abs(across-move.spacing()*.5)<=move.width()*.5+.25;
            case "single","line_to_target" -> move.lockedPlayer()!=null;
            default -> false;
        };
        if(!geometry)return false;
        return !(move.blocksBehindCover()||move.shapeType().equals("line_to_target"))
            || minecraft.level.clip(new ClipContext(move.origin().add(0,1,0),player.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS;
    }

    private static void extract(ExtractLevelRenderStateEvent event){
        sync();
        var quads=new ArrayList<Quad>();
        var floor=new ArrayList<Quad>();
        long now=ClientUi.current().serverNow();
        var player=Minecraft.getInstance().player;
        if(player!=null&&localLife.get().instance.isEmpty()){
            for(var fight:fights.values()){
                if(!sameWorld(fight.dimension()))continue;
                ring(floor,fight.center().add(0,.03,0),fight.radius(),.09,0xb27ada,145);
                if(fight.safeSpots()!=null)for(var spot:fight.safeSpots())
                    if(now<spot.until())ring(floor,spot.point().add(0,.045,0),spot.radius(),.12,0xa9f7ba,190);
            }
            for(var cast:casts.values()){
                var move=cast.move;
                if(!sameWorld(move.boss().dimension)||now>move.end())continue;
                int color=switch(move.element()){case 0->0xf9d77c;case 1->0xff7a42;case 2->0xa0e8ff;case 3->0xca93ef;default->0xfc796a;};
                Vec3 at=move.origin().add(0,.06,0), facing=move.direction();
                int alpha=now<move.startedAt()+move.lastContact()?72:45;
                switch(move.shapeType()){
                    case "circle","ring" -> {disc(floor,at,move.radius(),color,alpha);ring(floor,at,move.radius(),.07,color,215);}
                    case "fan" -> fan(floor,at,facing,move.range(),Math.toRadians(move.angle())*.5,color,alpha);
                    case "lane" -> groundLane(floor,at,facing,move.length(),move.width(),color,alpha);
                    case "double_lane" -> {
                        Vec3 side=move.side().scale(move.spacing()*.5);
                        Vec3 one=at.add(side),two=at.subtract(side);
                        groundLane(floor,one,facing,move.length(),move.width(),color,alpha);
                        groundLane(floor,two,facing,move.length(),move.width(),color,alpha);
                    }
                    case "line_to_target","single" -> {
                        var target=move.lockedPlayer()==null?null:Minecraft.getInstance().level.getPlayerByUUID(move.lockedPlayer());
                        if(target!=null)line(quads,at.add(0,1,0),target.getEyePosition(),.055,color,215);
                    }
                }
            }
            var surface=new HashMap<Vec3,Vec3>();
            for(var quad:floor){
                var a=surface.computeIfAbsent(quad.a(),RitualFightClient::groundSample);
                var b=surface.computeIfAbsent(quad.b(),RitualFightClient::groundSample);
                var c=surface.computeIfAbsent(quad.c(),RitualFightClient::groundSample);
                var d=surface.computeIfAbsent(quad.d(),RitualFightClient::groundSample);
                quads.add(new Quad(a,b,c,d,quad.rgb(),quad.alpha()));
            }
        }
        event.getRenderState().setRenderData(FRAME,List.copyOf(quads));
    }
    private static void groundLane(List<Quad> floor,Vec3 origin,Vec3 facing,double length,double width,int color,int alpha){
        for(double step=0;step<length;step+=1){
            var start=origin.add(facing.scale(step));
            rect(floor,start,origin.add(facing.scale(Math.min(step+1,length))),width,color,alpha);
        }
    }
    private static Vec3 groundSample(Vec3 point){
        var minecraft=Minecraft.getInstance();
        var hit=minecraft.level.clip(new ClipContext(point.add(0,1.1,0),point.add(0,-3,0),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,minecraft.player));
        return hit.getType()==HitResult.Type.BLOCK?hit.getLocation().add(0,.055,0):point;
    }
    private static void submit(SubmitCustomGeometryEvent event){
        var quads=event.getLevelRenderState().getRenderData(FRAME);
        if(quads!=null)WorldQuads.submit(event,quads);
    }

    public static void hud(GuiGraphicsExtractor graphics){
        var minecraft=Minecraft.getInstance();
        if(minecraft.player==null||minecraft.gui.hud.isHidden()||!localLife.get().instance.isEmpty())return;
        var fight=fights.values().stream().filter(f->sameWorld(f.dimension())&&f.contains(minecraft.player.position())).findFirst().orElse(null);
        if(fight==null)return;
        var font=UiFonts.font();
        long elapsed=ClientUi.current().serverNow()-fight.at();
        String detail=fight.status().equals("PENDING")?"开战 "+Math.max(0,(fight.countdown()-elapsed+999)/1000)+" 秒":fight.hpText();
        String title="Lv."+fight.level()+" "+fight.name()+"  ·  "+(detail==null?"":detail);
        graphics.text(font,title,(graphics.guiWidth()-font.width(title))/2,31,UiTheme.HUD_GOLD,true);
        if(fight.lines()!=null){
            int y=43;
            for(String line:fight.lines().values()){
                graphics.text(font,line,(graphics.guiWidth()-font.width(line))/2,y,UiTheme.HUD_TEXT,true);y+=12;
            }
        }
    }
}
