package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
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
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 封印塔第50层专属首领「伊蕾娜」客户端外观渲染器：
 * 纤细玩家骨骼（PLAYER_SLIM）+ textures/entity/npc/elaina.png（仅引用贴图路径，不依赖 npc 模块）
 * + 巫师帽头饰（elaina_witch_hat）+ 法杖手持 / 骑乘魔杖飞行姿态。
 */
public final class ElainaBossRenderer extends MobRenderer<DungeonEnemy, ElainaBossRenderer.State, ElainaBossRenderer.Model> {
    private static final PlayerSkin SKIN = PlayerSkin.insecure(
        new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ItemData.NS, "entity/npc/elaina")),
        null, null, PlayerModelType.SLIM
    );

    public static final class State extends AvatarRenderState {
        public final ItemStackRenderState staffItem = new ItemStackRenderState();
        public final ItemStackRenderState potionItem = new ItemStackRenderState();
        public boolean ridingWand;
        public boolean hoveringCast;
        public boolean drinkingPotion;
        public boolean groundCast;
        public float castProgress;
        public long castElapsedMs;
    }

    public static final class Model extends PlayerModel {
        private static final float DEG = (float) (Math.PI / 180.0);

        Model(ModelPart root) {
            super(root, true);
        }

        @Override
        public void setupAnim(AvatarRenderState raw) {
            super.setupAnim(raw);
            if (!(raw instanceof State state)) return;
            if (state.ridingWand) {
                // 骑乘魔杖飞行姿态：双腿侧坐/微曲，右手扶杖，左手前伸施法
                applyRidingLegs();
                rightArm.xRot = -0.65f;
                rightArm.zRot = 0.12f;
                leftArm.xRot = -1.10f;
                leftArm.zRot = -0.25f;
            } else if (state.hoveringCast) {
                // 高空悬浮吟唱姿态：身下乘骑魔杖 + 上半身沿用 avatar/chant.json 咏唱动作（前 150ms 举手就位后保持微摆）
                applyRidingLegs();
                float u = Math.clamp(state.castElapsedMs / 150.0f, 0.0f, 1.0f);
                float sway = (float) Math.sin(state.castElapsedMs * 0.004) * (2.0f * DEG);
                leftArm.xRot = (-12.0f + (-34.0f - -12.0f) * u) * DEG + sway * 0.5f;
                rightArm.xRot = (-28.0f + (-84.0f - -28.0f) * u) * DEG + sway;
                rightArm.yRot = (10.0f + (66.0f - 10.0f) * u) * DEG;
            } else if (state.drinkingPotion) {
                // 饮用生命药水姿态：右手举瓶至口边
                rightArm.xRot = -1.35f;
                rightArm.yRot = -0.35f;
            } else if (state.groundCast) {
                // 地面施法姿态（追踪星辰 / 24连日冕 / 时钟回溯 / 时之封印）：右手前举魔杖挥舞施法
                float swing = (float) Math.sin(state.castProgress * Math.PI * 2.0) * 0.25f;
                rightArm.xRot = -1.55f + swing;
                rightArm.zRot = 0.15f;
                leftArm.xRot = -0.75f;
                leftArm.zRot = -0.18f;
            }
        }

        private void applyRidingLegs() {
            rightLeg.xRot = -1.15f;
            leftLeg.xRot = -0.95f;
            rightLeg.zRot = 0.22f;
            leftLeg.zRot = -0.22f;
        }
    }

    private ItemStack witchHat;
    private ItemStack staffVisual;
    private ItemStack potionVisual;

    public ElainaBossRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.45f);
        addLayer(new HatLayer(this));
        addLayer(new WandLayer(this));
    }

    private ItemStack witchHat() {
        if (witchHat == null) witchHat = EquipmentItems.visualHeadwear("elaina_witch_hat");
        return witchHat;
    }

    private ItemStack staffVisual() {
        if (staffVisual == null) staffVisual = EquipmentVisualItems.weaponModel("common_staff");
        return staffVisual;
    }

    private ItemStack potionVisual() {
        if (potionVisual == null) potionVisual = new ItemStack(Items.POTION);
        return potionVisual;
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
        String move = activeMove ? entity.move() : "";
        boolean airborne = entity.isNoGravity();
        state.castElapsedMs = activeMove ? Math.max(0L, elapsed) : 150L;
        state.castProgress = activeMove ? (float) Math.clamp(elapsed / (double) duration, 0.0, 1.0) : 0.0f;
        state.ridingWand = "wand_charge".equals(move) || "corona_flight".equals(move) || "pumpkin_spin".equals(move);
        state.hoveringCast = !state.ridingWand && (airborne
            || "transition_80".equals(move) || "transition_50".equals(move)
            || "transition_20".equals(move) || "finale_meteor_1".equals(move)
            || "elemental_orbs".equals(move));
        state.drinkingPotion = "drink_potion".equals(move);
        state.groundCast = !state.ridingWand && !state.hoveringCast && !state.drinkingPotion && !move.isEmpty();

        itemModelResolver.updateForLiving(state.headItem, witchHat(), ItemDisplayContext.HEAD, entity);
        itemModelResolver.updateForLiving(state.staffItem, staffVisual(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        if (state.drinkingPotion) {
            itemModelResolver.updateForLiving(state.potionItem, potionVisual(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        }
        state.boundingBoxHeight = Math.max(state.boundingBoxHeight, 3.05f * state.scale);
        if (state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, state.scale, 0);
        }
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN.body().texturePath();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
    }

    private static final class HatLayer extends RenderLayer<State, Model> {
        HatLayer(ElainaBossRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yaw, float pitch) {
            if (state.isInvisible) return;
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
            state.headItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    private static final class WandLayer extends RenderLayer<State, Model> {
        WandLayer(ElainaBossRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yaw, float pitch) {
            if (state.isInvisible) return;
            if (state.ridingWand || state.hoveringCast) {
                // 骑乘状态（含高空悬浮吟唱）：将法杖横置于角色身下
                pose.pushPose();
                pose.translate(0.0f, 0.78f, 0.0f);
                pose.mulPose(Axis.XP.rotationDegrees(90.0f));
                pose.mulPose(Axis.ZP.rotationDegrees(180.0f));
                pose.scale(1.25f, 1.25f, 1.25f);
                state.staffItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
                return;
            }
            pose.pushPose();
            getParentModel().rightArm.translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
            pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            pose.translate(0.0625f, 0.125f, -0.625f);
            if (state.drinkingPotion) {
                state.potionItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            } else {
                state.staffItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            }
            pose.popPose();
        }
    }
}
