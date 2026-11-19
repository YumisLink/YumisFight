package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.api.DeviceType;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/** The vanilla pig, front-panel status and moving drill assembly. */
final class PetrochemicalRenderer {
    private static final Identifier PIG_TEXTURE = Identifier.withDefaultNamespace("textures/entity/pig/pig_temperate.png");
    private static final Identifier DRILL_TEXTURE = IndustrialContent.id("textures/block/petrochemistry/drill_ram.png");
    private final Font font;
    private final PigModel pig;
    private final Model<BlockEntityRenderState> drill;

    PetrochemicalRenderer(BlockEntityRendererProvider.Context context) {
        font = context.font();
        pig = new PigModel(context.bakeLayer(ModelLayers.PIG));
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("ram", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-2, -28, -2, 4, 24, 4)
            .texOffs(28, 0).addBox(-3.5f, -7, -3.5f, 7, 3, 7)
            .texOffs(28, 15).addBox(-2.75f, -4, -2.75f, 5.5f, 2, 5.5f)
            .texOffs(28, 29).addBox(-1.5f, -2, -1.5f, 3, 2, 3), PartPose.ZERO);
        drill = new Model<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), RenderTypes::entityCutout) {};
    }

    void submit(IndustrialRenderer.State state, PoseStack pose, SubmitNodeCollector nodes) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (state.facing) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        }));
        if (state.petrochemical == DeviceType.OIL_DRILL) {
            float stroke = state.petroRunning ? (1 - (float) Math.cos(state.petroAge * Math.PI / 20)) * .125f : 0;
            pose.translate(0, .9 - stroke, 0);
            pose.scale(-1, -1, 1);
            nodes.submitModel(drill, state, pose, drill.renderType(DRILL_TEXTURE), state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0, null);
        } else {
            pose.translate(-.5, 0, -.5);
            if (state.pig != null) {
                pose.pushPose();
                pose.translate(1, .502, 1.08);
                pose.scale(-1, -1, 1);
                pose.translate(0, -1.501, 0);
                int light = LightCoordsUtil.pack(Math.max(7, LightCoordsUtil.block(state.lightCoords)),
                    LightCoordsUtil.sky(state.lightCoords));
                nodes.submitModel(pig, state.pig, pose, pig.renderType(PIG_TEXTURE), light,
                    OverlayTexture.NO_OVERLAY, 0, null);
                pose.popPose();
            }
            panel(state, pose, nodes);
        }
        pose.popPose();
    }

    private void panel(IndustrialRenderer.State state, PoseStack pose, SubmitNodeCollector nodes) {
        var text = (state.petrochemical == DeviceType.BUDDY_SHELL
            ? Component.translatable("block.yumis_dungeon_and_fight.buddy_generator.panel.empty")
            : !state.petroConnected
                ? Component.translatable("block.yumis_dungeon_and_fight.buddy_generator.panel.disconnected")
                : state.petroRunning ? Component.literal("600")
                : Component.translatable("block.yumis_dungeon_and_fight.buddy_generator.panel.idle")).getVisualOrderText();
        pose.translate(.19, .664, .129);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        float scale = .25f / Math.max(28, font.width(text));
        pose.scale(scale, -scale, scale);
        nodes.submitText(pose, -font.width(text) / 2f, -4, text, false, Font.DisplayMode.POLYGON_OFFSET,
            15728880, state.petroRunning && state.petroConnected ? 0xffb9f7ff : 0xffc3cdd5, 0, 0);
    }

    static LivingEntityRenderState pigState(float age) {
        var state = new LivingEntityRenderState();
        state.yRot = 8 * (float) Math.sin(age * Math.PI / 120);
        state.xRot = 3 * (float) Math.sin(age * Math.PI / 160);
        return state;
    }
}
