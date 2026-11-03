package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Visibility is sampled from the actual extracted camera; post processing consumes that frame. */
final class CoronaBlur {
    private record Blast(Vec3 point,long at,String dimension){}
    private static final ConcurrentLinkedQueue<Blast> blasts=new ConcurrentLinkedQueue<>();
    private static final Identifier EFFECT=Identifier.parse("yumis_dungeon_and_fight:corona_blur");
    private static final ContextKey<Boolean> FRAME=new ContextKey<>(Identifier.parse("yumis_dungeon_and_fight:corona_blur_frame"));
    private static boolean registered;private static volatile long until;private static String dimension="";
    private CoronaBlur(){}
    static void register(){if(registered)return;registered=true;NeoForge.EVENT_BUS.addListener(CoronaBlur::extract);NeoForge.EVENT_BUS.addListener(CoronaBlur::render);}
    static void flash(Vec3 point){var mc=Minecraft.getInstance();if(mc.level!=null&&mc.options.getCameraType().isFirstPerson())blasts.add(new Blast(point,ClientUi.now(),mc.level.dimension().identifier().toString()));}
    static void clear(){until=0;blasts.clear();dimension="";}
    private static void extract(ExtractLevelRenderStateEvent event){
        long now=ClientUi.now();var mc=Minecraft.getInstance();String world=event.getLevel().dimension().identifier().toString();Vec3 eye=event.getCamera().position();
        if(event.getCamera().isDetached()||!mc.options.getCameraType().isFirstPerson()){
            clear();event.getRenderState().setRenderData(FRAME,false);return;
        }
        for(Blast blast;(blast=blasts.poll())!=null;){
            if(now-blast.at>250||!world.equals(blast.dimension)||mc.player==null||eye.distanceToSqr(blast.point)>1024||!event.getFrustum().isVisible(new AABB(blast.point,blast.point).inflate(2)))continue;
            if(event.getLevel().clip(new ClipContext(eye,blast.point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()!=HitResult.Type.MISS)continue;
            until=now+800;dimension=world;
        }
        event.getRenderState().setRenderData(FRAME,now<until&&world.equals(dimension));
    }
    private static void render(RenderLevelStageEvent.AfterLevel event){
        if(!Boolean.TRUE.equals(event.getLevelRenderState().getRenderData(FRAME)))return;
        var mc=Minecraft.getInstance();
        if(!mc.options.getCameraType().isFirstPerson()||mc.gameRenderer.mainCamera().isDetached()){clear();return;}
        var chain=mc.getShaderManager().getPostChain(EFFECT,LevelTargetBundle.MAIN_TARGETS);if(chain!=null)chain.process(mc.gameRenderer.mainRenderTarget(),GraphicsResourceAllocator.UNPOOLED);
    }
}
