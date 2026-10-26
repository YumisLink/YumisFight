package cn.yumis.fight.client;
import cn.yumis.fight.rules.SkillVisualEvent;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Adapts the new common visual protocol to the restored YDNF geometry renderer. */
public final class Effects {
    public static final SkillVfx VFX=new SkillVfx();
    private static final Map<String,Integer> sequences=new HashMap<>();
    private Effects(){
    }
    public static void receive(JsonObject j){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!j.has("id")||!j.has("caster"))return;
        String id=j.get("id").getAsString(),kind=j.get("kind").getAsString();
        if(kind.equals("BASIC"))kind="ATTACK";
        String phase=j.has("phase")?j.get("phase").getAsString():"START";
        int sequence=j.has("sequence")?j.get("sequence").getAsInt():phase.equals("START")?0:sequences.getOrDefault(id,0)+1;
        if(phase.equals("END")||phase.equals("CANCEL"))sequence=4096;
        boolean first=!sequences.containsKey(id);
        sequences.merge(id,sequence,Math::max);
        long now=ClientState.INSTANCE.serverNow(),started=j.get("startedAt").getAsLong();
        long duration=j.has("duration")?j.get("duration").getAsLong():kind.equals("STORM")?8000:kind.equals("GHOST")?3800:kind.equals("OVERDRIVE")?30000:2500;
        Vec3 origin=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble());
        Vec3 point=j.has("px")?new Vec3(j.get("px").getAsDouble(),j.get("py").getAsDouble(),j.get("pz").getAsDouble()):origin;
        Vec3 direction=new Vec3(j.get("dx").getAsDouble(),j.has("dy")?j.get("dy").getAsDouble():0,j.get("dz").getAsDouble());
        UUID caster=UUID.fromString(j.get("caster").getAsString());
        String dimension=j.has("dimension")?j.get("dimension").getAsString():mc.level.dimension().identifier().toString();
        var event=new SkillVisualEvent(caster,UUID.fromString(j.get("session").getAsString()),UUID.fromString(id),j.get("generation").getAsLong(),dimension,j.has("instance")?j.get("instance").getAsString():ClientState.INSTANCE.own.instance,kind,SkillVisualEvent.Phase.valueOf(phase),sequence,started,j.has("at")?j.get("at").getAsLong():Math.max(started,now),duration,j.get("seed").getAsLong(),point(origin),point(point),point(direction),j.has("charge")?j.get("charge").getAsDouble():0,j.has("color")?j.get("color").getAsInt():kind.equals("GHOST")?SkillVisualEvent.DARK:SkillVisualEvent.CYAN);
        VFX.accept(event);
        if(first&&kind.equals("STORM")&&mc.player!=null&&caster.equals(mc.player.getUUID())&&dimension.equals(mc.level.dimension().identifier().toString())&&event.session().toString().equals(ClientState.INSTANCE.session))VFX.beginCamera(event.cast());
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
            particle(center.add(Math.cos(a)*radius,.06,Math.sin(a)*radius),color,.5f);
        }
    }
    public static void spark(Vec3 point,int color){
        particle(point,color,.7f);
    }
    public static void particle(Vec3 p,int color,float size){
        var level=Minecraft.getInstance().level;
        if(level!=null)level.addAlwaysVisibleParticle(new DustParticleOptions(color,size),true,p.x,p.y,p.z,0,0,0);
    }
}
