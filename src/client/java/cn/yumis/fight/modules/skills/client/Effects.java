package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;

import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import com.google.gson.*;
import net.minecraft.client.Minecraft;

import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Decodes current skill visual messages for the module renderer. */
public final class Effects {
    public static final SkillVfx VFX=new SkillVfx();
    private static final Map<String,Integer> sequences=new LinkedHashMap<>();
    private Effects(){
    }
    public static void receive(JsonObject j){
        if(j.has("replay")){var events=j.getAsJsonArray("replay");for(int i=0;i<Math.min(32,events.size());i++){var event=events.get(i).getAsJsonObject();if(!event.has("replay"))receive(event);}return;}
        var mc=Minecraft.getInstance();
        if(mc.level==null||!j.has("id")||!j.has("caster"))return;
        String id=j.get("id").getAsString(),kind=j.get("kind").getAsString();
        String phase=j.has("phase")?j.get("phase").getAsString():"START";
        int sequence=j.has("sequence")?j.get("sequence").getAsInt():phase.equals("START")?0:sequences.getOrDefault(id,0)+1;
        if(phase.equals("END")||phase.equals("CANCEL"))sequence=4096;
        boolean first=!sequences.containsKey(id);
        sequences.merge(id,sequence,Math::max);
        if(sequences.size()>8192)sequences.remove(sequences.keySet().iterator().next());
        long now=SkillsClient.current().serverNow(),started=j.get("startedAt").getAsLong(),clockDelta=cn.yumis.fight.modules.presentation.api.client.ClientUi.now()-(j.has("serverNow")?j.get("serverNow").getAsLong():now);
        long duration=j.has("duration")?j.get("duration").getAsLong():SkillVisualRegistry.handler(kind).defaultDuration();
        Vec3 origin=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble());
        Vec3 point=j.has("px")?new Vec3(j.get("px").getAsDouble(),j.get("py").getAsDouble(),j.get("pz").getAsDouble()):origin;
        Vec3 direction=new Vec3(j.get("dx").getAsDouble(),j.has("dy")?j.get("dy").getAsDouble():0,j.get("dz").getAsDouble());
        UUID caster=UUID.fromString(j.get("caster").getAsString());
        String dimension=j.has("dimension")?j.get("dimension").getAsString():mc.level.dimension().identifier().toString();
        var event=new SkillVisualEvent(caster,UUID.fromString(j.get("session").getAsString()),UUID.fromString(id),j.get("generation").getAsLong(),dimension,j.has("instance")?j.get("instance").getAsString():SkillsClient.current().own().instance,kind,SkillVisualEvent.Phase.valueOf(phase),sequence,started+clockDelta,(j.has("at")?j.get("at").getAsLong():Math.max(started,now))+clockDelta,duration,j.get("seed").getAsLong(),point(origin),point(point),point(direction),j.has("charge")?j.get("charge").getAsDouble():0,j.has("color")?j.get("color").getAsInt():SkillVisualRegistry.handler(kind).defaultColor(),j.has("forwardOffset")?j.get("forwardOffset").getAsDouble():0,j.has("horizontalScale")?j.get("horizontalScale").getAsDouble():1,j.has("sweep")&&j.get("sweep").getAsBoolean());
        VFX.accept(event,j.has("castSpeed")?j.get("castSpeed").getAsDouble():1);
        if(j.has("motion")){JsonObject motion=j.getAsJsonObject("motion").deepCopy();if(motion.has("at"))motion.addProperty("at",motion.get("at").getAsLong()+clockDelta);VFX.motion(event.cast(),event.sequence(),motion);}
        if(first&&SkillVisualRegistry.handler(kind).cinematic()&&mc.player!=null&&caster.equals(mc.player.getUUID())&&dimension.equals(mc.level.dimension().identifier().toString())&&event.session().toString().equals(SkillsClient.current().session()))VFX.beginCamera(event.cast());
    }
    private static SkillVisualEvent.Point point(Vec3 v){
        return new SkillVisualEvent.Point(v.x,v.y,v.z);
    }
    public static void tick(){
        VFX.tick();
    }
    public static void clear(){
        VFX.clear();
        sequences.clear();
    }
    public static void warning(Vec3 center,double radius,int color){
        for(int i=0;i<40;i++){
            double a=i*Math.PI/20;
            cn.yumis.fight.modules.presentation.api.client.Particles.dust(center.add(Math.cos(a)*radius,.06,Math.sin(a)*radius),color,.5f);
        }
    }
    public static void spark(Vec3 point,int color){
        cn.yumis.fight.modules.presentation.api.client.Particles.dust(point,color,.7f);
    }

}
