package cn.yumis.fight.modules.presentation.api.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.system.MemoryStack;
import java.util.*;

/** Opt-in camera-distance masking and shared effect render pipelines. Callers retain ownership of effects and their lifetimes. */
public final class NearEffectRendering {
    private static final String SETTINGS_UNIFORM="NearEffectSettings";
    private static final BindGroupLayout SETTINGS_LAYOUT=BindGroupLayout.builder().withUniform(SETTINGS_UNIFORM,UniformType.UNIFORM_BUFFER).build();
    private static final BlendFunction ADDITIVE_ALPHA_BLEND=new BlendFunction(BlendFactor.SRC_ALPHA,BlendFactor.ONE);
    // Minecraft 26.2 uses reverse-Z depth (near=1, far=0): GREATER_THAN_OR_EQUAL passes closer geometry without writing depth.
    private static final DepthStencilState EFFECT_NO_DEPTH_WRITE=new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,false);
    private static final List<RenderPipeline> PIPELINES=new ArrayList<>();
    private static final RenderPipeline QUADS=pipeline(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE,"entity",0);
    private static final RenderPipeline EFFECT_TRANSLUCENT=effectPipeline("effect_translucent",BlendFunction.TRANSLUCENT,false);
    private static final RenderPipeline EFFECT_TRANSLUCENT_NEAR=effectPipeline("effect_translucent_near",BlendFunction.TRANSLUCENT,true);
    private static final RenderPipeline EFFECT_ADDITIVE=effectPipeline("effect_additive",ADDITIVE_ALPHA_BLEND,false);
    private static final RenderPipeline EFFECT_ADDITIVE_NEAR=effectPipeline("effect_additive_near",ADDITIVE_ALPHA_BLEND,true);
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
    private record EffectKey(Identifier texture,boolean additive,boolean nearFade){}
    private static final Map<EffectKey,RenderType> EFFECTS=new HashMap<>();
    private record ItemKey(RenderType original,Identifier atlas,boolean inside){}
    private static final Map<ItemKey,RenderType> ITEMS=new HashMap<>();
    private record GlintKey(OutputTarget target,boolean inside){}
    private static final Map<GlintKey,RenderType> GLINTS=new HashMap<>();
    private static GpuBuffer settingsBuffer;
    private static int uploadedDistance=-1;

    private NearEffectRendering(){}

    public static void register(IEventBus bus){
        bus.addListener((RegisterRenderPipelinesEvent event)->PIPELINES.forEach(event::registerPipeline));
        NeoForge.EVENT_BUS.addListener(NearEffectRendering::updateSettings);
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent event)->{
            if(settingsBuffer!=null)settingsBuffer.close();
            settingsBuffer=null;
            uploadedDistance=-1;
        });
    }

    /** Sample during render extraction, using that frame's actual camera. */
    public static boolean enabled(Camera camera){return ClientSettings.nearEffectFade&&ClientSettings.nearEffectFadeDistanceSteps>0&&!camera.isDetached();}

    private static void updateSettings(RenderFrameEvent.Pre event){
        int distance=ClientSettings.nearEffectFadeDistanceSteps;
        if(uploadedDistance==distance)return;
        var device=RenderSystem.getDevice();
        try(var stack=MemoryStack.stackPush()){
            var data=Std140Builder.onStack(stack,16).putVec4(distance/100f,0,0,0).get();
            if(settingsBuffer==null)settingsBuffer=device.createBuffer(()->"Near effect settings",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,data);
            else device.createCommandEncoder().writeToBuffer(settingsBuffer.slice(),data);
        }
        uploadedDistance=distance;
    }

    public static void bindSettings(RenderPass pass,RenderPipeline pipeline){
        if(settingsBuffer!=null&&(pipeline==QUADS||pipeline==EFFECT_TRANSLUCENT_NEAR||pipeline==EFFECT_ADDITIVE_NEAR
            ||pipeline==ITEM_CUTOUT_OUTSIDE||pipeline==ITEM_CUTOUT_INSIDE||pipeline==ITEM_TRANSLUCENT_OUTSIDE||pipeline==ITEM_TRANSLUCENT_INSIDE
            ||pipeline==GLINT_OUTSIDE||pipeline==GLINT_INSIDE||pipeline==PARTICLE_OUTSIDE.pipeline()||pipeline==PARTICLE_INSIDE.pipeline())){
            pass.setUniform(SETTINGS_UNIFORM,settingsBuffer);
        }
    }

    static RenderType quads(){return QUAD_TYPE;}

    /** Cached double-sided, no-depth-write textured RenderType for additive glow or translucent effects. */
    public static RenderType effect(Identifier texture,boolean additive,boolean nearFade){
        return EFFECTS.computeIfAbsent(new EffectKey(texture,additive,nearFade),key->{
            RenderPipeline chosen=key.additive()
                ?(key.nearFade()?EFFECT_ADDITIVE_NEAR:EFFECT_ADDITIVE)
                :(key.nearFade()?EFFECT_TRANSLUCENT_NEAR:EFFECT_TRANSLUCENT);
            var setup=RenderSetup.builder(chosen).withTexture("Sampler0",key.texture()).useOverlay();
            if(!key.additive())setup.sortOnUpload();
            return RenderType.create("yumis_effect_"+(key.additive()?"add_":"trans_")+(key.nearFade()?"near_":"plain_")+key.texture().getPath().replace('/','_'),setup.createRenderSetup());
        });
    }

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

    private static RenderPipeline effectPipeline(String name,BlendFunction blend,boolean nearFade){
        var builder=RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE.toBuilder()
            .withLocation(id("pipeline/"+name))
            .withCull(false)
            .withDepthStencilState(EFFECT_NO_DEPTH_WRITE)
            .withColorTargetState(new ColorTargetState(blend));
        if(nearFade){
            builder.withVertexShader(id("core/near_effect_entity"))
                .withFragmentShader(id("core/near_effect_entity"))
                .withShaderDefine("NEAR_EFFECT_PASS",0)
                .withBindGroupLayout(SETTINGS_LAYOUT);
        }
        var pipeline=builder.build();
        PIPELINES.add(pipeline);
        return pipeline;
    }

    private static RenderPipeline pipeline(RenderPipeline original,String shader,int pass){
        var builder=original.toBuilder().withLocation(id("pipeline/near_effect_"+shader+"_"+original.getLocation().getPath().replace('/','_')+"_"+pass))
            .withVertexShader(id("core/near_effect_"+shader)).withFragmentShader(id("core/near_effect_"+shader))
            .withShaderDefine("NEAR_EFFECT_PASS",pass).withBindGroupLayout(SETTINGS_LAYOUT).withColorTargetState(original.getColorTargetState());
        if(pass==2){
            var depth=original.getDepthStencilState();
            builder.withDepthStencilState(new DepthStencilState(depth.depthTest()==CompareOp.EQUAL?CompareOp.GREATER_THAN_OR_EQUAL:depth.depthTest(),false,depth.depthBiasScaleFactor(),depth.depthBiasConstant()));
            if(original!=RenderPipelines.GLINT)builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        }
        var pipeline=builder.build();PIPELINES.add(pipeline);return pipeline;
    }

    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight",path);}
}
