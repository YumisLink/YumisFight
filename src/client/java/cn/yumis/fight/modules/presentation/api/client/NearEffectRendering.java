package cn.yumis.fight.modules.presentation.api.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import java.util.*;

/** Opt-in camera-distance masking. Callers retain ownership of effects and their lifetimes. */
public final class NearEffectRendering {
    private static final List<RenderPipeline> PIPELINES=new ArrayList<>();
    private static final RenderPipeline QUADS=pipeline(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE,"entity",0);
    private static final RenderPipeline ITEM_CUTOUT_OUTSIDE=pipeline(RenderPipelines.ITEM_CUTOUT,"item",1);
    private static final RenderPipeline ITEM_CUTOUT_INSIDE=pipeline(RenderPipelines.ITEM_CUTOUT,"item",2);
    private static final RenderPipeline ITEM_TRANSLUCENT_OUTSIDE=pipeline(RenderPipelines.ITEM_TRANSLUCENT,"item",1);
    private static final RenderPipeline ITEM_TRANSLUCENT_INSIDE=pipeline(RenderPipelines.ITEM_TRANSLUCENT,"item",2);
    private static final RenderPipeline GLINT_OUTSIDE=pipeline(RenderPipelines.GLINT,"glint",1);
    private static final RenderPipeline GLINT_INSIDE=pipeline(RenderPipelines.GLINT,"glint",2);
    private static final SingleQuadParticle.Layer PARTICLE_OUTSIDE=new SingleQuadParticle.Layer(false,TextureAtlas.LOCATION_PARTICLES,pipeline(RenderPipelines.OPAQUE_PARTICLE,"particle",1));
    private static final SingleQuadParticle.Layer PARTICLE_INSIDE=new SingleQuadParticle.Layer(true,TextureAtlas.LOCATION_PARTICLES,pipeline(RenderPipelines.OPAQUE_PARTICLE,"particle",2));
    private static final RenderType QUAD_TYPE=RenderType.create("near_faded_effect_quads",RenderSetup.builder(QUADS)
        .withTexture("Sampler0",Identifier.withDefaultNamespace("textures/block/white_concrete.png"))
        .useOverlay().sortOnUpload().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup());
    private record ItemKey(RenderType original,Identifier atlas,boolean inside){}
    private static final Map<ItemKey,RenderType> ITEMS=new HashMap<>();
    private record GlintKey(OutputTarget target,boolean inside){}
    private static final Map<GlintKey,RenderType> GLINTS=new HashMap<>();

    private NearEffectRendering(){}

    public static void register(IEventBus bus){
        bus.addListener((RegisterRenderPipelinesEvent event)->PIPELINES.forEach(event::registerPipeline));
    }

    /** Sample during render extraction, using that frame's actual camera. */
    public static boolean enabled(Camera camera){return ClientSettings.nearEffectFade&&!camera.isDetached();}

    static RenderType quads(){return QUAD_TYPE;}
    static SingleQuadParticle.Layer particleLayer(boolean inside){return inside?PARTICLE_INSIDE:PARTICLE_OUTSIDE;}

    static RenderType item(RenderType original,Identifier atlas,boolean inside){
        return ITEMS.computeIfAbsent(new ItemKey(original,atlas,inside),key->{
            boolean translucent=original.pipeline()==RenderPipelines.ITEM_TRANSLUCENT;
            if(!translucent&&original.pipeline()!=RenderPipelines.ITEM_CUTOUT)throw new IllegalArgumentException("Unsupported effect item pipeline: "+original.pipeline());
            RenderPipeline pipeline=translucent?(inside?ITEM_TRANSLUCENT_INSIDE:ITEM_TRANSLUCENT_OUTSIDE):(inside?ITEM_CUTOUT_INSIDE:ITEM_CUTOUT_OUTSIDE);
            var setup=RenderSetup.builder(pipeline).withTexture("Sampler0",atlas).useLightmap().useOverlay()
                .affectsCrumbling().setOutputTarget(original.outputTarget()).setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            if(inside||original.sortOnUpload())setup.sortOnUpload();
            return RenderType.create("near_effect_item_"+(inside?"inside":"outside"),setup.createRenderSetup());
        });
    }

    static RenderType glint(OutputTarget target,boolean inside){
        return GLINTS.computeIfAbsent(new GlintKey(target,inside),key->RenderType.create("near_effect_glint_"+(inside?"inside":"outside"),
            RenderSetup.builder(inside?GLINT_INSIDE:GLINT_OUTSIDE).withTexture("Sampler0",ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
                .setTextureTransform(TextureTransform.GLINT_TEXTURING).setOutputTarget(target).createRenderSetup()));
    }

    private static RenderPipeline pipeline(RenderPipeline original,String shader,int pass){
        var builder=original.toBuilder().withLocation(id("pipeline/near_effect_"+shader+"_"+original.getLocation().getPath().replace('/','_')+"_"+pass))
            .withVertexShader(id("core/near_effect_"+shader)).withFragmentShader(id("core/near_effect_"+shader))
            .withShaderDefine("NEAR_EFFECT_PASS",pass).withColorTargetState(original.getColorTargetState());
        if(pass==2){
            var depth=original.getDepthStencilState();
            builder.withDepthStencilState(new DepthStencilState(depth.depthTest()==CompareOp.EQUAL?CompareOp.GREATER_THAN_OR_EQUAL:depth.depthTest(),false,depth.depthBiasScaleFactor(),depth.depthBiasConstant()));
            if(original!=RenderPipelines.GLINT)builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        }
        var pipeline=builder.build();PIPELINES.add(pipeline);return pipeline;
    }

    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight",path);}
}
