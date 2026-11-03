package cn.yumis.fight.modules.presentation.api.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.MatrixUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;
import org.joml.Quaternionf;
import java.util.*;

/** Draws opted-in visual item stacks; normal held items and entity renderers never use this collector. */
public final class EffectModels {
    private EffectModels(){}

    public static void submit(ItemStackRenderState model,PoseStack pose,SubmitNodeCollector nodes,int light,int overlay,boolean nearFade){
        model.submit(pose,nearFade?new Collector(nodes,nodes):nodes,light,overlay,0);
    }

    private record Material(RenderType original,Identifier atlas){}

    private static final class Collector implements SubmitNodeCollector {
        private final SubmitNodeCollector root;
        private final OrderedSubmitNodeCollector delegate;
        Collector(SubmitNodeCollector root,OrderedSubmitNodeCollector delegate){this.root=root;this.delegate=delegate;}

        @Override public OrderedSubmitNodeCollector order(int order){return new Collector(root,root.order(order));}

        @Override public void submitItem(PoseStack pose,ItemDisplayContext display,int light,int overlay,int outline,int[] tints,List<BakedQuad> quads,ItemStackRenderState.FoilType foil){
            Map<Material,List<BakedQuad>> groups=new LinkedHashMap<>();
            for(var quad:quads){var material=quad.materialInfo();groups.computeIfAbsent(new Material(material.itemRenderType(),material.sprite().atlasLocation()),key->new ArrayList<>()).add(quad);}
            for(var group:groups.entrySet()){
                var material=group.getKey();var batch=List.copyOf(group.getValue());
                for(boolean inside:new boolean[]{false,true}){
                    delegate.submitCustomGeometry(pose,NearEffectRendering.item(material.original(),material.atlas(),inside),(matrix,vertices)->{
                        var instance=new QuadInstance();instance.setLightCoords(light);instance.setOverlayCoords(overlay);
                        for(var quad:batch){int tint=quad.materialInfo().tintIndex();instance.setColor(tint>=0&&tint<tints.length?tints[tint]:-1);vertices.putBakedQuad(matrix,quad,instance);}
                    });
                    if(foil!=ItemStackRenderState.FoilType.NONE)submitFoil(pose,display,material.original(),batch,foil,inside);
                }
            }
        }

        private void submitFoil(PoseStack pose,ItemDisplayContext display,RenderType original,List<BakedQuad> quads,ItemStackRenderState.FoilType foil,boolean inside){
            var target=Minecraft.getInstance().gameRenderer.gameRenderState().useShaderTransparency()&&original.outputTarget()==OutputTarget.ITEM_ENTITY_TARGET?OutputTarget.ITEM_ENTITY_TARGET:OutputTarget.MAIN_TARGET;
            delegate.submitCustomGeometry(pose,NearEffectRendering.glint(target,inside),(matrix,vertices)->{
                VertexConsumer output=vertices;
                if(foil==ItemStackRenderState.FoilType.SPECIAL){
                    var decal=matrix.copy();
                    if(display==ItemDisplayContext.GUI)MatrixUtil.mulComponentWise(decal.pose(),.5f);
                    else if(display.firstPerson())MatrixUtil.mulComponentWise(decal.pose(),.75f);
                    output=new SheetedDecalTextureGenerator(vertices,decal,.0078125f);
                }
                var instance=new QuadInstance();for(var quad:quads)output.putBakedQuad(matrix,quad,instance);
            });
        }

        @Override public void submitShadow(PoseStack pose,float radius,List<EntityRenderState.ShadowPiece> pieces){delegate.submitShadow(pose,radius,pieces);}
        @Override public void submitNameTag(PoseStack pose,Vec3 attachment,int offset,Component name,boolean seeThrough,int light,CameraRenderState camera){delegate.submitNameTag(pose,attachment,offset,name,seeThrough,light,camera);}
        @Override public void submitText(PoseStack pose,float x,float y,FormattedCharSequence text,boolean shadow,Font.DisplayMode mode,int light,int color,int background,int outline){delegate.submitText(pose,x,y,text,shadow,mode,light,color,background,outline);}
        @Override public void submitFlame(PoseStack pose,EntityRenderState state,Quaternionf rotation){delegate.submitFlame(pose,state,rotation);}
        @Override public void submitLeash(PoseStack pose,EntityRenderState.LeashState state){delegate.submitLeash(pose,state);}
        @Override public <S> void submitModel(Model<? super S> model,S state,PoseStack pose,RenderType type,int light,int overlay,int color,TextureAtlasSprite sprite,int outline,ModelFeatureRenderer.CrumblingOverlay crumbling){delegate.submitModel(model,state,pose,type,light,overlay,color,sprite,outline,crumbling);}
        @Override public void submitMovingBlock(PoseStack pose,MovingBlockRenderState state,int outline){delegate.submitMovingBlock(pose,state,outline);}
        @Override public void submitBlockModel(PoseStack pose,RenderType type,List<BlockStateModelPart> parts,int[] tints,int light,int overlay,int outline){delegate.submitBlockModel(pose,type,parts,tints,light,overlay,outline);}
        @Override public void submitBreakingBlockModel(PoseStack pose,List<BlockStateModelPart> parts,int progress){delegate.submitBreakingBlockModel(pose,parts,progress);}
        @Override public void submitShapeOutline(PoseStack pose,VoxelShape shape,RenderType type,int color,float width,boolean afterTerrain){delegate.submitShapeOutline(pose,shape,type,color,width,afterTerrain);}
        @Override public void submitCustomGeometry(PoseStack pose,RenderType type,SubmitNodeCollector.CustomGeometryRenderer renderer){delegate.submitCustomGeometry(pose,type,renderer);}
        @Override public void submitQuadParticleGroup(QuadParticleRenderState state){delegate.submitQuadParticleGroup(state);}
        @Override public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group,CameraRenderState camera,boolean onTop){delegate.submitGizmoPrimitives(group,camera,onTop);}
        @Override public void submitMultiLayerBlockModel(PoseStack pose,List<BlockStateModelPart> parts,boolean translucent,int[] tints,int light,int overlay,int outline){delegate.submitMultiLayerBlockModel(pose,parts,translucent,tints,light,overlay,outline);}
        @Override public <T extends SubmitNode,S extends T> void submitSpecial(RenderPhaseKey<T> phase,S node){delegate.submitSpecial(phase,node);}
    }
}
