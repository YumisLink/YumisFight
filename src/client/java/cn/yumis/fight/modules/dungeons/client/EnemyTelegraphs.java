package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
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

/** Geometric telegraphs for existing generic enemy actions. No damage calculation lives here. */
public final class EnemyTelegraphs {
    private record Frame(List<Quad> quads,List<SpriteQuad> sprites,boolean fade){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","enemy_telegraphs"));
    private static final Map<String,JsonObject> actions=new LinkedHashMap<>();
    private static final Map<String,Long> cancelled=new HashMap<>();
    private static Supplier<String> instance=()->"";private static Object world;
    public static void register(Supplier<String> current){instance=current;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EnemyTelegraphs::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EnemyTelegraphs::submit);}
    private static void world(){if(world!=Minecraft.getInstance().level){world=Minecraft.getInstance().level;actions.clear();cancelled.clear();}}
    public static void receive(JsonObject j){world();if(j.has("encounterHit")||j.has("treasureHit"))return;String id=j.get("id").getAsString();if(!cancelled.containsKey(id))actions.put(id,j.deepCopy());while(actions.size()>256)actions.remove(actions.keySet().iterator().next());}
    public static void cancel(String id){world();actions.remove(id);cancelled.put(id,ClientUi.current().serverNow()+10000);}
    private static void extract(ExtractLevelRenderStateEvent event){
        world();List<Quad> q=new ArrayList<>();List<SpriteQuad> sp=new ArrayList<>();long now=ClientUi.current().serverNow();String here=instance.get();
        cancelled.values().removeIf(until->until<now);
        actions.values().removeIf(j->!j.getAsJsonObject("boss").get("instance").getAsString().equals(here)||now>j.get("startedAt").getAsLong()+(j.get("windupTicks").getAsLong()+j.get("activeTicks").getAsLong())*50);
        if(ClientUi.current().ready())for(JsonObject j:actions.values()){
            long started=j.get("startedAt").getAsLong(),windup=Math.max(50L,j.get("windupTicks").getAsLong()*50L),release=started+windup;if(now>=release)continue;
            double progress=Math.clamp((now-started)/(double)windup,0,1);
            Vec3 c=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble()+.045,j.get("z").getAsDouble()),d=new Vec3(j.get("dx").getAsDouble(),0,j.get("dz").getAsDouble()).normalize();
            double radius=j.get("radius").getAsDouble();int color=j.get("element").getAsInt()==0?0xffd75e:0xff6c60;
            String shape=j.has("shape")?j.get("shape").getAsString():"FAN";
            switch(shape){
                case "RECT" -> {rect(q,c,c.add(d.scale(radius)),j.get("width").getAsDouble(),color,65);rect(q,c.add(0,.01,0),c.add(d.scale(radius*progress)).add(0,.01,0),j.get("width").getAsDouble()*.88,0xffffff,55);}
                case "SQUARE" -> {Vec3 a=c.add(-radius,0,-radius),b=c.add(radius,0,-radius),r=c.add(radius,0,radius),s=c.add(-radius,0,radius);q.add(new Quad(a,b,r,s,color,65));line(q,a,b,.05,color,210);line(q,b,r,.05,color,210);line(q,r,s,.05,color,210);line(q,s,a,.05,color,210);}
                case "CIRCLE" -> EffectSprites.warningCircle(sp,c.add(0,.01,0),radius,progress,color,215);
                default -> {fan(q,c,d,radius,j.get("halfAngle").getAsDouble(),color,60);fan(q,c.add(0,.01,0),d,radius*progress,j.get("halfAngle").getAsDouble(),0xffffff,48);}
            }
        }
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(q),List.copyOf(sp),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void submit(SubmitCustomGeometryEvent event){Frame f=event.getLevelRenderState().getRenderData(FRAME);if(f!=null){WorldQuads.submit(event,f.quads,f.fade);EffectSprites.submit(event,f.sprites,f.fade);}}
    private EnemyTelegraphs(){}
}
