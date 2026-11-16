package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/** Render the planner's actual block models without placing local world blocks. */
final class ConveyorPlacementPreview {
    private record Ghost(BlockPos pos,Map<Identifier,List<BakedQuad>> materials){}
    private record Frame(List<Ghost> ghosts){}
    private static final ContextKey<Frame> FRAME=new ContextKey<>(IndustrialContent.id("conveyor_preview"));
    private ConveyorPlacementPreview(){}
    static void register(){NeoForge.EVENT_BUS.addListener(ConveyorPlacementPreview::extract);NeoForge.EVENT_BUS.addListener(ConveyorPlacementPreview::submit);}
    private static void extract(ExtractLevelRenderStateEvent event){
        var plan=ConveyorPlacementClient.preview();List<Ghost> ghosts=new ArrayList<>();
        if(plan!=null){
            Map<BlockState,Map<Identifier,List<BakedQuad>>> models=new HashMap<>();
            for(BlockPos pos:plan.fresh())ghosts.add(new Ghost(pos,models.computeIfAbsent(plan.states().get(pos),ConveyorPlacementPreview::model)));
        }
        event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(ghosts)));
    }
    private static Map<Identifier,List<BakedQuad>> model(BlockState state){
        List<BlockStateModelPart> parts=new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(0),parts);
        Map<Identifier,List<BakedQuad>> materials=new LinkedHashMap<>();
        for(var part:parts){
            append(materials,part.getQuads(null));
            for(Direction face:Direction.values())append(materials,part.getQuads(face));
        }
        materials.replaceAll((atlas,quads)->List.copyOf(quads));return Map.copyOf(materials);
    }
    private static void append(Map<Identifier,List<BakedQuad>> materials,List<BakedQuad> quads){
        for(var quad:quads)materials.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(),atlas->new ArrayList<>()).add(quad);
    }
    private static void submit(SubmitCustomGeometryEvent event){
        var frame=event.getLevelRenderState().getRenderData(FRAME);
        if(frame==null||event.getLevelRenderState().cameraRenderState==null)return;
        Vec3 camera=event.getLevelRenderState().cameraRenderState.pos;var pose=event.getPoseStack();
        for(var ghost:frame.ghosts()){
            pose.pushPose();pose.translate(ghost.pos().getX()-camera.x,ghost.pos().getY()-camera.y,ghost.pos().getZ()-camera.z);
            for(var material:ghost.materials().entrySet())event.getSubmitNodeCollector().submitCustomGeometry(pose,RenderTypes.entityTranslucent(material.getKey()),(matrix,vertices)->{
                var instance=new QuadInstance();instance.setColor(0x7087e6ff);instance.setLightCoords(0xF000F0);instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                for(var quad:material.getValue())vertices.putBakedQuad(matrix,quad,instance);
            });
            pose.popPose();
        }
    }
}
