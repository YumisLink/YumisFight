package cn.yumis.fight.modules.dungeons.client;
import cn.yumis.fight.modules.dungeons.api.GoldenWeapon;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.*;

/** The existing 3D meshes follow authoritative world trajectories; they never become item entities. */
public final class TreasureWeaponVisuals {
    private record Track(GoldenWeapon weapon,long received){}
    private record Sword(ItemStackRenderState model,Vec3 position,Vec3 direction){}
    private record Frame(List<Sword> swords,List<Quad> quads,boolean nearFade){}
    private static final Map<UUID,Track> weapons=new LinkedHashMap<>();
    private static final Map<UUID,Long> closed=new LinkedHashMap<>();
    private static final ContextKey<Frame> FRAME=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","treasure_weapons"));
    private static Object world;
    public static void receive(JsonObject packet){
        var mc=Minecraft.getInstance();if(world!=mc.level){weapons.clear();closed.clear();world=mc.level;}if(mc.level==null)return;
        long now=ClientUi.now();closed.values().removeIf(until->until<now);
        var w=new Gson().fromJson(packet,GoldenWeapon.class);
        if(packet.get("remove").getAsBoolean()){
            weapons.remove(w.id);
            if(closed.putIfAbsent(w.id,now+15000)==null&&w.phase.equals("EXPLODE"))EffectParticles.explosion(w.position());
            while(closed.size()>8192)closed.remove(closed.keySet().iterator().next());
        }else if(!closed.containsKey(w.id))weapons.put(w.id,new Track(w,now));
    }
    public static void register(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TreasureWeaponVisuals::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TreasureWeaponVisuals::submit);}
    private static void extract(ExtractLevelRenderStateEvent event){
        var mc=Minecraft.getInstance();if(world!=mc.level){weapons.clear();closed.clear();world=mc.level;}if(mc.player==null)return;
        List<Sword> swords=new ArrayList<>();List<Quad> quads=new ArrayList<>();long now=ClientUi.now();
        weapons.values().removeIf(t->now-t.received()>15000||t.weapon().position().distanceToSqr(mc.player.position())>128*128);
        for(var t:weapons.values()){
            var w=t.weapon();var model=new ItemStackRenderState();mc.getItemModelResolver().updateForLiving(model,EquipmentVisualItems.weaponModel(w.model),ItemDisplayContext.NONE,mc.player);
            Vec3 p=w.position();if(w.phase.equals("FLY"))p=p.add(w.direction().scale(Math.min(.25,GoldenWeapon.SPEED*(now-t.received())/1000d)));
            swords.add(new Sword(model,p,w.direction()));
            if(w.phase.equals("LAND")){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;double r=GoldenWeapon.RADIUS;Vec3 p0=p.add(Math.cos(a)*r,.04,Math.sin(a)*r),p1=p.add(Math.cos(b)*r,.04,Math.sin(b)*r),p2=p.add(Math.cos(b)*(r-.12),.04,Math.sin(b)*(r-.12)),p3=p.add(Math.cos(a)*(r-.12),.04,Math.sin(a)*(r-.12));quads.add(new Quad(p0,p1,p2,p3,0xffbf38,190));}}
        }
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(swords),List.copyOf(quads),NearEffectRendering.enabled(event.getCamera())));
    }
    private static void submit(SubmitCustomGeometryEvent event){var frame=event.getLevelRenderState().getRenderData(FRAME);if(frame==null||event.getLevelRenderState().cameraRenderState==null)return;Vec3 camera=event.getLevelRenderState().cameraRenderState.pos;var pose=event.getPoseStack();for(var sword:frame.swords()){var p=sword.position();pose.pushPose();pose.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);Vec3 d=sword.direction();pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,1,0),new org.joml.Vector3f((float)d.x,(float)d.y,(float)d.z)));pose.scale(1.2f,1.2f,1.2f);EffectModels.submit(sword.model(),pose,event.getSubmitNodeCollector(),0xF000F0,OverlayTexture.NO_OVERLAY,frame.nearFade());pose.popPose();}WorldQuads.submit(event,frame.quads(),frame.nearFade());}
    private TreasureWeaponVisuals(){}
}
