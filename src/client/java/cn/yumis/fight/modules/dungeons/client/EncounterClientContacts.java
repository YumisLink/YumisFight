package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.*;

/** Owner-side contacts for the existing dungeon action contracts; AI and enemy movement stay on the server. */
public final class EncounterClientContacts {
    public enum Family { GBL, PALACE, FORBIDDEN, SKASA, CLASSIC }
    private static final Gson JSON=new Gson();
    private static final class Contact {
        Family family;GblAttack gbl;PalaceAttack palace;SkasaAttack skasa;Target source;
        long previous,pullAt;int cell,area;boolean retired,unstoppable;double arc,enemyFraction;UUID lockedPlayer;
        String id(){return gbl!=null?gbl.id:palace!=null?palace.id:skasa.id;}
        String instance(){return gbl!=null?gbl.instance:palace!=null?palace.instance:skasa.instance;}
        long release(){return gbl!=null?gbl.release():palace!=null?palace.release():skasa.release();}
        long end(){return gbl!=null?gbl.end():palace!=null?palace.end():skasa.end();}
        String key(){return family+"/"+id();}
    }
    private static Supplier<Life> own=Life::new;
    private static IntSupplier playerLevel=()->1;
    private static BooleanSupplier synchronize=()->false,invulnerable=()->false;
    private static Consumer<JsonObject> hit=j->{};
    private static final Map<String,Contact> contacts=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new HashMap<>(),struck=new HashMap<>();
    private static Object world;
    private static String epoch="";
    private EncounterClientContacts(){}
    public static void register(Supplier<Life> life,IntSupplier level,BooleanSupplier sync,BooleanSupplier immune){own=life;playerLevel=level;synchronize=sync;invulnerable=immune;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EncounterClientContacts::tick);}
    private static void state(){
        var mc=Minecraft.getInstance();var life=own.get();String next=ClientUi.current().session()+"/"+life.generation+"/"+life.instance;
        if(world!=mc.level||!next.equals(epoch)){world=mc.level;epoch=next;contacts.clear();closed.clear();struck.clear();}
    }
    public static void receive(Family family,JsonObject packet,Consumer<JsonObject> localHit){
        state();if(world==null||!ClientUi.current().ready())return;hit=localHit;long now=ClientUi.current().serverNow();
        var next=new Contact();next.family=family;
        if(family==Family.PALACE||family==Family.CLASSIC)next.palace=JSON.fromJson(packet,PalaceAttack.class);
        else if(family==Family.SKASA)next.skasa=JSON.fromJson(packet,SkasaAttack.class);
        else next.gbl=JSON.fromJson(packet,GblAttack.class);
        String key=next.key();var previous=contacts.get(key);next.cell=packet.has("cell")?packet.get("cell").getAsInt():previous==null?0:previous.cell;next.area=packet.has("area")?packet.get("area").getAsInt():previous==null?0:previous.area;
        if(family==Family.CLASSIC){
            if(ClassicMove.find(next.palace.kind)==null)return;
            next.arc=packet.has("arc")?packet.get("arc").getAsDouble():previous==null?0:previous.arc;
            next.enemyFraction=packet.has("enemyFraction")?packet.get("enemyFraction").getAsDouble():previous==null?0:previous.enemyFraction;
            next.lockedPlayer=packet.has("lockedPlayer")?UUID.fromString(packet.get("lockedPlayer").getAsString()):previous==null?null:previous.lockedPlayer;
            next.unstoppable=packet.has("unstoppable")&&packet.get("unstoppable").getAsBoolean();
        }
        if(packet.get("remove").getAsBoolean()){
            if(previous!=null&&!(packet.has("cancelled")&&packet.get("cancelled").getAsBoolean())){
                next.source=previous.source;next.retired=previous.retired;next.previous=previous.previous;next.pullAt=previous.pullAt;
                step(next,Math.min(next.end(),packet.has("contactUntil")?packet.get("contactUntil").getAsLong():now));
            }
            contacts.remove(key);closed.put(key,now+10000);return;
        }
        if(closed.containsKey(key)||!next.instance().equals(own.get().instance)||own.get().dead||!packet.has("boss"))return;
        next.source=JSON.fromJson(packet.get("boss"),Target.class);next.retired=packet.has("retired")&&packet.get("retired").getAsBoolean();
        if(now>next.end()+(next.retired?1000:0))return;
        next.previous=previous==null?Math.max(next.release(),Math.min(next.end(),now)):previous.previous;next.pullAt=previous==null?next.release():previous.pullAt;
        contacts.put(key,next);
    }
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        state();var mc=Minecraft.getInstance();long now=ClientUi.current().serverNow();closed.values().removeIf(until->now>until);struck.values().removeIf(until->now>until);
        if(mc.player==null||mc.level==null||!ClientUi.current().ready()||own.get().dead||mc.player.isDeadOrDying()){contacts.clear();return;}
        if(mc.isPaused())return;
        for(var c:new ArrayList<>(contacts.values()))if(step(c,Math.min(now,c.end()))||now>=c.end())contacts.remove(c.key());
    }
    private static boolean step(Contact c,long at){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||own.get().dead||p.isDeadOrDying()||!c.instance().equals(own.get().instance)||!c.source.dimension.equals(mc.level.dimension().identifier().toString()))return true;
        var raw=mc.level.getEntity(c.source.entityId);
        if(!c.retired){
            if(raw==null)return false;
            if(!(raw instanceof DungeonEnemy e)||!e.getUUID().equals(c.source.id)||!e.isAlive()||!c.unstoppable&&CombatContentTypes.controlled(e))return true;
        }
        if(at<c.release())return false;
        c.previous=Math.min(c.previous,at);Entity source=raw==null?p:raw;
        if(c.skasa!=null)return skasa(c,source,at);
        return c.palace!=null?palace(c,source,at):gbl(c,source,at);
    }
    private static boolean visible(Vec3 from,Entity source){
        var mc=Minecraft.getInstance();return mc.level.clip(new ClipContext(from,mc.player.position().add(0,.7,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source)).getType()==HitResult.Type.MISS;
    }
    private static void contact(Contact c,String group,String kind,double coefficient,boolean magic,int element,long at,int stunTicks){
        String key=c.family+"/"+group;if(coefficient<=0||struck.containsKey(key))return;struck.put(key,c.end()+10000);
        var action=new JsonObject();action.addProperty("id",epoch+"/"+key);action.addProperty("clientContact",true);action.add("boss",JSON.toJsonTree(c.source));action.addProperty("kind",kind);action.addProperty("coefficient",coefficient);action.addProperty("magic",magic);action.addProperty("element",element);action.addProperty("occurredAt",at);
        if(stunTicks>0){action.addProperty("selfStunTicks",stunTicks);action.addProperty("controlId",epoch+"/"+c.key());}hit.accept(action);
    }
    private static boolean gbl(Contact c,Entity source,long at){
        var mc=Minecraft.getInstance();var p=mc.player;var a=c.gbl;Vec3 from=a.position(c.previous),to=a.position(at);boolean blocked=false;
        if(a.shape.equals("LINE")||a.shape.equals("LOB")){var wall=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));blocked=wall.getType()!=HitResult.Type.MISS;if(blocked){to=wall.getLocation();if(a.shape.equals("LOB")){a.ex=to.x;a.ey=to.y;a.ez=to.z;a.active=Math.max(1,at-a.release());}}}
        boolean inside;
        if(a.shape.equals("LINE")){var box=p.getBoundingBox().inflate(a.width+.15);inside=box.contains(from)||box.clip(from,to).isPresent();}
        else if(a.shape.equals("LOB"))inside=(blocked||at>=a.end())&&Math.abs(p.getY()-a.ey)<2.6&&p.position().subtract(a.destination()).horizontalDistanceSqr()<=Math.pow(a.radius+.3,2);
        else{
            Vec3 offset=p.position().subtract(a.origin()),direction=a.destination().subtract(a.origin()).multiply(1,0,1).normalize();double distance=offset.horizontalDistance();
            inside=Math.abs(offset.y)<=(a.shape.equals("WAVE")?1.05:2.6);
            if(a.shape.equals("WAVE")){long previous=c.family==Family.GBL?at-75:c.previous;inside&=distance>=Math.max(0,a.waveRadius(previous)-a.width*.5-.3)&&distance<=a.waveRadius(at)+a.width*.5+.3;}
            else inside&=distance<=a.radius+.3&&(!a.shape.equals("FAN")||distance<.4||direction.lengthSqr()<.001||offset.multiply(1,0,1).normalize().dot(direction)>=Math.cos(a.halfAngle));
        }
        Vec3 origin=a.shape.equals("LOB")?a.destination().add(0,.4,0):a.origin().add(0,.5,0);
        if(inside&&(a.shape.equals("LINE")||visible(origin,source)))contact(c,a.id,a.kind,a.coefficient,a.magic,a.element,ClientUi.current().serverNow(),0);
        c.previous=at;return blocked;
    }
    private static boolean palace(Contact c,Entity source,long at){
        var mc=Minecraft.getInstance();var p=mc.player;var a=c.palace;boolean classic=c.family==Family.CLASSIC;
        if(!(classic?cn.yumis.fight.modules.dungeons.content.ClassicDungeonRooms.contains(c.cell,c.area,p.position()):cn.yumis.fight.modules.dungeons.content.CastlePalace.contains(c.cell,c.area,p.position()))){c.previous=at;return false;}
        if(c.lockedPlayer!=null&&!c.lockedPlayer.equals(p.getUUID())){c.previous=at;return false;}
        if(c.enemyFraction>0){
            int level=c.source.level(playerLevel.getAsInt(),synchronize.getAsBoolean());
            double coefficient=c.source.health(level)*c.enemyFraction/c.source.attack(level);
            contact(c,a.group,a.kind,coefficient,true,a.element,ClientUi.current().serverNow(),0);c.previous=at;return true;
        }
        if(a.shape.equals("NONE")){c.previous=at;return false;}
        Vec3 from=position(c,c.previous),to=position(c,at);boolean blocked=false;
        if(a.shape.equals("DART")||a.shape.equals("BOMB"))for(long time=c.previous;time<at;){long next=Math.min(at,time+25);var wall=mc.level.clip(new ClipContext(a.position(time),a.position(next),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));if(wall.getType()!=HitResult.Type.MISS){to=wall.getLocation();blocked=true;break;}time=next;}
        if(blocked&&a.shape.equals("BOMB")){a.destination(to);a.active=Math.max(1,at-a.release());}
        Vec3 offset=p.position().subtract(a.origin()),direction=a.destination().subtract(a.origin()).multiply(1,0,1).normalize();double distance=offset.horizontalDistance();boolean inside;
        if(a.shape.equals("DART")){var box=p.getBoundingBox().inflate(a.width+.1);inside=box.contains(from)||box.clip(from,to).isPresent();}
        else if(a.shape.equals("BOMB"))inside=(blocked||at>=a.end())&&Math.abs(p.getY()-a.ey)<2.6&&p.position().subtract(a.destination()).horizontalDistanceSqr()<=Math.pow(a.radius+.3,2);
        else if(Math.abs(offset.y)>2.6)inside=false;
        else if(a.shape.equals("RECT")){double forward=offset.dot(direction),side=Math.abs(offset.x*direction.z-offset.z*direction.x);inside=forward>=-.3&&forward<=a.radius+.3&&side<=a.width*.5+.3;}
        else if(a.shape.equals("DASH")){var box=p.getBoundingBox().inflate(a.width*.5,.5,a.width*.5);inside=box.contains(from)||box.clip(from,to).isPresent();}
        else if(a.shape.equals("WAVE"))inside=(!classic||Math.abs(offset.y)<1.05)&&distance>=Math.max(0,a.radius*a.progress(c.previous)-a.width*.5-.3)&&distance<=a.radius*a.progress(at)+a.width*.5+.3;
        else inside=distance<=a.radius+.3&&(!a.shape.equals("FAN")||distance<.4||direction.lengthSqr()<.001||offset.multiply(1,0,1).normalize().dot(direction)>=Math.cos(a.halfAngle));
        ClassicMove move=classic?ClassicMove.find(a.kind):null;
        if(classic&&Set.of("inhale","tree_cage").contains(move.clip())&&distance<=a.radius+.5&&Math.abs(offset.y)<2.6&&at>=c.pullAt){
            c.pullAt=at+200;
            if(!invulnerable.getAsBoolean()&&!p.isSpectator()&&!p.getAbilities().invulnerable&&visible(a.origin().add(0,.7,0),source)){
                boolean cage=move.clip().equals("tree_cage");Vec3 pull=a.origin().subtract(p.position()).multiply(1,0,1);
                if(pull.lengthSqr()>(cage?Math.pow(a.radius-.65,2):.25))p.setDeltaMovement(p.getDeltaMovement().add(pull.normalize().scale(cage?.17:.055)));
            }
        }
        if(inside&&(a.shape.equals("DART")||visible((a.shape.equals("BOMB")?a.destination():a.origin()).add(0,.6,0),source)))contact(c,a.group,a.kind,a.coefficient,a.magic,a.element,ClientUi.current().serverNow(),move==null?0:move.stunTicks());
        c.previous=at;return blocked;
    }
    private static Vec3 position(Contact c,long at){Vec3 p=c.palace.position(at);return c.arc>0?p.add(0,c.arc*Math.sin(Math.PI*c.palace.progress(at)),0):p;}
    private static boolean skasa(Contact c,Entity source,long at){
        var mc=Minecraft.getInstance();var p=mc.player;var a=c.skasa;var move=a.move();if(!cn.yumis.fight.modules.dungeons.content.SkasaLair.contains(a.cell,a.area,p.position())){c.previous=at;return false;}boolean blocked=false;
        if(move==SkasaMoves.ICE_LINE){Vec3 from=a.position(c.previous).add(0,.7,0),to=a.position(at).add(0,.7,0);var wall=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));blocked=wall.getType()!=HitResult.Type.MISS;if(blocked&&from.distanceToSqr(to)>.000001)at=c.previous+(long)((at-c.previous)*from.distanceTo(wall.getLocation())/from.distanceTo(to));}
        if(move==SkasaMoves.VORTEX&&at>=c.pullAt){c.pullAt=at+250;if(!p.isSpectator()&&!p.getAbilities().invulnerable&&a.inArena(p.position())&&Math.abs(p.getX()-a.x)<10){Vec3 pull=a.origin().subtract(p.position()).multiply(1,0,1);if(pull.lengthSqr()>.25)p.setDeltaMovement(p.getDeltaMovement().add(pull.normalize().scale(.04)));}}
        int pulse=a.pulse(at);if((move.travels()||a.contacting(at))&&a.contains(p.position(),pulse,c.previous,at)&&visible(a.origin().add(0,.7,0),source))contact(c,a.id+"/"+pulse,a.kind,move.coefficient,move.magic,move.element,ClientUi.current().serverNow(),move==SkasaMoves.ROAR?12:0);
        c.previous=at;return blocked;
    }
}
