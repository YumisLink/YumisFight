package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.RitualMechanismEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;

/** The temporary mechanics are recognizable vanilla objects, with their real collision centers. */
public final class RitualMechanismRenderer extends EntityRenderer<RitualMechanismEntity,RitualMechanismRenderer.State> {
    public static final class State extends EntityRenderState {
        final ItemStackRenderState model=new ItemStackRenderState();
        float height,weight;
    }
    private final ItemModelResolver models;
    private final Item item;

    public RitualMechanismRenderer(EntityRendererProvider.Context context,String kind){
        super(context);
        models=context.getItemModelResolver();
        shadowRadius=.22f;
        item=switch(kind){
            case "ritual_fake_branch" -> Items.DEAD_BUSH;
            case "ritual_hedge" -> Items.AZALEA_LEAVES;
            case "ritual_root_knot" -> Items.ROOTED_DIRT;
            case "ritual_sprout" -> Items.FLOWERING_AZALEA;
            case "ritual_spring" -> Items.COPPER_INGOT;
            case "ritual_eye_coin" -> Items.GOLD_NUGGET;
            case "ritual_pull_line" -> Items.STRING;
            case "ritual_anti_magic" -> Items.PRISMARINE_CRYSTALS;
            case "ritual_resonance" -> Items.LODESTONE;
            case "ritual_broken_watch" -> Items.CLOCK;
            case "ritual_empty_pan" -> Items.HEAVY_WEIGHTED_PRESSURE_PLATE;
            case "ritual_campfire" -> Items.CAMPFIRE;
            case "ritual_stele" -> Items.CHISELED_DEEPSLATE;
            case "ritual_confessional" -> Items.DARK_OAK_DOOR;
            case "ritual_shard" -> Items.AMETHYST_CLUSTER;
            case "gilgamesh_wedge" -> Items.GOLDEN_SWORD;
            case "gilgamesh_enkidu" -> Items.GOLD_BLOCK;
            case "roxy_hydro_core" -> Items.HEART_OF_THE_SEA;
            default -> Items.AMETHYST_SHARD;
        };
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(RitualMechanismEntity entity,State state,float partial){
        super.extractRenderState(entity,state,partial);
        state.height=Math.min(1.2f,entity.getBbHeight()*.5f);
        state.weight=Math.clamp(Math.max(entity.getBbWidth(),entity.getBbHeight())*.88f,.55f,1.65f);
        // Item components may still be unbound when startup creates the renderer.
        models.updateForTopItem(state.model,new ItemStack(item),ItemDisplayContext.FIXED,entity.level(),null,entity.getId());
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();
        pose.translate(0,state.height,0);
        pose.scale(state.weight,state.weight,state.weight);
        state.model.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.popPose();
        super.submit(state,pose,collector,camera);
    }
}
