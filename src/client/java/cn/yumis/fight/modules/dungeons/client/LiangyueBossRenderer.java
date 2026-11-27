package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import cn.yumis.fight.platform.ItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 剧场塔第 20 层专属首领「神剑-梁月的幻影」客户端外观渲染器：
 * 标准玩家骨骼（PLAYER）+ 专属幻影剑士皮肤 + 3D 神剑手持与四项剑技动态姿态。
 */
public final class LiangyueBossRenderer extends MobRenderer<DungeonEnemy, LiangyueBossRenderer.State, LiangyueBossRenderer.Model> {
    private static final PlayerSkin SKIN = PlayerSkin.insecure(
        new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ItemData.NS, "entity/theater_tower_liangyue")),
        null, null, PlayerModelType.WIDE
    );

    public static final class State extends AvatarRenderState {
        public final ItemStackRenderState swordItem = new ItemStackRenderState();
        public String move = "";
        public float progress;
        public long elapsedMs;
    }

    public static final class Model extends PlayerModel {
        Model(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(AvatarRenderState raw) {
            super.setupAnim(raw);
            if (!(raw instanceof State s)) return;
            if (s.move.isEmpty()) {
                // 待机执剑架势
                rightArm.xRot = -0.42f;
                rightArm.zRot = 0.08f;
                leftArm.xRot = -0.25f;
                return;
            }
            if (s.move.startsWith("triple_slash_")) {
                float swing = (float) Math.sin(s.progress * Math.PI);
                boolean reverse = s.move.endsWith("2");
                rightArm.xRot = -1.95f + swing * 1.65f;
                rightArm.yRot = (reverse ? 0.55f : -0.55f) + (reverse ? -1.1f : 1.1f) * swing;
                rightArm.zRot = reverse ? -0.35f : 0.35f;
                leftArm.xRot = -0.75f;
            } else if ("raging_dragon_windup".equals(s.move)) {
                rightArm.xRot = -0.45f;
                rightArm.yRot = -0.65f;
                rightArm.zRot = 0.45f;
                leftArm.xRot = -1.15f;
                leftArm.yRot = 0.40f;
            } else if ("raging_dragon_dash".equals(s.move)) {
                rightArm.xRot = -1.55f;
                rightArm.yRot = 0.25f;
                leftArm.xRot = 0.65f;
                rightLeg.xRot = -0.55f;
                leftLeg.xRot = 0.55f;
            } else if ("raging_dragon_wave".equals(s.move) || "phantom_dance_finisher".equals(s.move)) {
                float cleave = Math.min(1.0f, s.progress * 1.6f);
                rightArm.xRot = -2.35f + cleave * 1.85f;
                rightArm.yRot = -0.65f + cleave * 1.35f;
                leftArm.xRot = -1.65f + cleave * 1.15f;
                leftArm.yRot = 0.45f - cleave * 0.65f;
            } else if ("phantom_dance_windup".equals(s.move)) {
                rightArm.xRot = -1.85f;
                rightArm.zRot = 0.25f;
                leftArm.xRot = -1.45f;
                leftArm.zRot = -0.25f;
            } else if ("phantom_dance_flurry".equals(s.move)) {
                float rapid = (float) Math.sin(s.elapsedMs * 0.032);
                float rapidCos = (float) Math.cos(s.elapsedMs * 0.032);
                rightArm.xRot = -1.35f + rapid * 0.95f;
                rightArm.yRot = rapidCos * 0.75f;
                leftArm.xRot = -1.25f - rapid * 0.70f;
                leftArm.yRot = -rapidCos * 0.55f;
            } else if ("storm_awakening".equals(s.move)) {
                float spin = (float) Math.sin(s.elapsedMs * 0.025);
                rightArm.xRot = -1.65f + spin * 0.85f;
                rightArm.zRot = 0.42f;
                leftArm.xRot = -1.15f - spin * 0.55f;
                leftArm.zRot = -0.35f;
                rightLeg.xRot = -0.45f;
                leftLeg.xRot = 0.35f;
            }
        }
    }

    private ItemStack swordVisual;

    public LiangyueBossRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.45f);
        addLayer(new SwordLayer(this));
    }

    private ItemStack swordVisual() {
        if (swordVisual == null) swordVisual = EquipmentVisualItems.weaponModel("skyspine");
        return swordVisual;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DungeonEnemy entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, itemModelResolver);
        state.skin = SKIN;
        state.id = entity.getId();

        long now = cn.yumis.fight.modules.presentation.api.client.ClientUi.current().serverNow();
        long duration = entity.moveDuration();
        long elapsed = now - entity.moveAt();
        boolean activeMove = duration > 0 && elapsed >= 0 && elapsed < duration;
        state.move = activeMove ? entity.move() : "";
        state.elapsedMs = activeMove ? elapsed : 0L;
        state.progress = activeMove ? (float) Math.clamp(elapsed / (double) duration, 0.0, 1.0) : 0.0f;

        itemModelResolver.updateForLiving(state.swordItem, swordVisual(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN.body().texturePath();
    }

    private static final class SwordLayer extends RenderLayer<State, Model> {
        SwordLayer(LiangyueBossRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yaw, float pitch) {
            if (state.isInvisible) return;
            pose.pushPose();
            getParentModel().rightArm.translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
            pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            pose.translate(0.0625f, 0.125f, -0.625f);
            state.swordItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
