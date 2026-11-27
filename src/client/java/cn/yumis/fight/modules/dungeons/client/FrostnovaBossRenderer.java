package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.content.FrostTowerFrostnova;
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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 霜塔第 20 层专属首领「霜星」客户端外观渲染器：
 * - 纤细玩家骨骼（PLAYER_SLIM）+ 专属霜星皮肤 + 冰晶法杖手持；
 * - 完整演出二阶段转阶段身体姿态：倒下(0~1s) -> 保持平躺/半仰浮空升起(1~2.4s) -> 空中站直(2.4~3.2s) -> 稳稳落地(3.2~4.0s)。
 */
public final class FrostnovaBossRenderer extends MobRenderer<DungeonEnemy, FrostnovaBossRenderer.State, FrostnovaBossRenderer.Model> {
    private static final PlayerSkin SKIN = PlayerSkin.insecure(
        new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ItemData.NS, "entity/frost_tower_frostnova")),
        null, null, PlayerModelType.SLIM
    );

    public static final class State extends AvatarRenderState {
        public final ItemStackRenderState staffItem = new ItemStackRenderState();
        public String move = "";
        public float progress;
        public long elapsedMs;
        public float bodyPitchDeg;
        public float bodyLiftOffset;
    }

    public static final class Model extends PlayerModel {
        Model(ModelPart root) {
            super(root, true);
        }

        @Override
        public void setupAnim(AvatarRenderState raw) {
            super.setupAnim(raw);
            if (!(raw instanceof State s)) return;
            if ("phase_revive".equals(s.move)) {
                long t = s.elapsedMs;
                if (t < FrostTowerFrostnova.REVIVE_FALL_END_MS) {
                    // 倒下：双臂无力摊开
                    float p = Math.clamp(t / (float) FrostTowerFrostnova.REVIVE_FALL_END_MS, 0.0f, 1.0f);
                    rightArm.xRot = -0.25f * p;
                    rightArm.zRot = 0.55f * p;
                    leftArm.xRot = -0.20f * p;
                    leftArm.zRot = -0.55f * p;
                } else if (t < FrostTowerFrostnova.REVIVE_RISE_END_MS) {
                    // 身体先飞起来：失重漂浮微摆
                    float sway = (float) Math.sin(t * 0.008) * 0.14f;
                    rightArm.xRot = -0.35f + sway;
                    rightArm.zRot = 0.45f;
                    leftArm.xRot = -0.35f - sway;
                    leftArm.zRot = -0.45f;
                    rightLeg.xRot = 0.18f;
                    leftLeg.xRot = -0.10f;
                } else if (t < FrostTowerFrostnova.REVIVE_STAND_END_MS) {
                    // 空中站起来：张开双臂聚能
                    rightArm.xRot = -1.65f;
                    rightArm.zRot = 0.58f;
                    leftArm.xRot = -1.55f;
                    leftArm.zRot = -0.58f;
                    rightLeg.xRot = 0.08f;
                    leftLeg.xRot = -0.08f;
                } else {
                    // 落地瞬间：双臂下压释放寒霜冲击
                    rightArm.xRot = -0.85f;
                    rightArm.zRot = 0.35f;
                    leftArm.xRot = -0.85f;
                    leftArm.zRot = -0.35f;
                }
                return;
            }
            if (s.move.isEmpty()) {
                // 霜星优雅持杖待机架势
                rightArm.xRot = -0.45f;
                rightArm.zRot = 0.06f;
                leftArm.xRot = -0.15f;
                return;
            }
            switch (s.move) {
                case "back_barrage" -> {
                    // 展开双臂在背后凝结并指挥连射冰刺
                    float pulse = (float) Math.sin(s.elapsedMs * 0.022) * 0.15f;
                    rightArm.xRot = -1.45f + pulse;
                    rightArm.zRot = 0.65f;
                    leftArm.xRot = -1.35f - pulse;
                    leftArm.zRot = -0.65f;
                }
                case "homing_fan" -> {
                    float swing = (float) Math.sin(s.progress * Math.PI);
                    rightArm.xRot = -1.80f + swing * 1.15f;
                    rightArm.yRot = -0.35f + swing * 0.70f;
                    leftArm.xRot = -0.75f;
                }
                case "frost_burst" -> {
                    if (s.progress < 0.74f) {
                        // 蓄力抱胸聚寒
                        rightArm.xRot = -1.05f;
                        rightArm.yRot = 0.55f;
                        leftArm.xRot = -1.05f;
                        leftArm.yRot = -0.55f;
                    } else {
                        // 爆发张开双臂
                        rightArm.xRot = -1.35f;
                        rightArm.zRot = 0.95f;
                        leftArm.xRot = -1.35f;
                        leftArm.zRot = -0.95f;
                    }
                }
                case "frost_field" -> {
                    // 轻点地面铺开寒霜领域
                    rightArm.xRot = -0.95f + (float) Math.sin(s.progress * Math.PI) * 0.65f;
                    leftArm.xRot = -0.55f;
                }
                case "ice_blink" -> {
                    // 优雅后仰滑退
                    rightArm.xRot = -0.75f;
                    rightArm.zRot = 0.45f;
                    leftArm.xRot = -1.15f;
                    leftArm.zRot = -0.35f;
                    rightLeg.xRot = -0.35f;
                    leftLeg.xRot = 0.35f;
                }
                case "winter_nova" -> {
                    // 浮空咏唱极寒星爆
                    float sway = (float) Math.sin(s.elapsedMs * 0.012) * 0.18f;
                    rightArm.xRot = -2.15f + sway;
                    rightArm.zRot = 0.48f;
                    leftArm.xRot = -2.05f - sway;
                    leftArm.zRot = -0.48f;
                    rightLeg.xRot = 0.22f;
                    leftLeg.xRot = -0.14f;
                }
                default -> {}
            }
        }
    }

    private ItemStack staffVisual;

    public FrostnovaBossRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.45f);
        addLayer(new StaffLayer(this));
    }

    private ItemStack staffVisual() {
        if (staffVisual == null) staffVisual = EquipmentVisualItems.weaponModel("common_staff");
        return staffVisual;
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

        // 计算转阶段「倒下 -> 身体先飞起来 -> 站起来落地」的整体倾倒角
        state.bodyPitchDeg = 0.0f;
        state.bodyLiftOffset = 0.0f;
        if ("phase_revive".equals(state.move)) {
            if (elapsed < FrostTowerFrostnova.REVIVE_FALL_END_MS) {
                // 0.0s ~ 1.0s：向后平躺倒下（0° -> 86°）
                float p = smooth(elapsed / (float) FrostTowerFrostnova.REVIVE_FALL_END_MS);
                state.bodyPitchDeg = 86.0f * p;
                state.bodyLiftOffset = 0.18f * p;
            } else if (elapsed < FrostTowerFrostnova.REVIVE_RISE_END_MS) {
                // 1.0s ~ 2.4s：保持仰躺姿态浮空升起，后半段微抬至 68°
                float p = (elapsed - FrostTowerFrostnova.REVIVE_FALL_END_MS)
                    / (float) (FrostTowerFrostnova.REVIVE_RISE_END_MS - FrostTowerFrostnova.REVIVE_FALL_END_MS);
                state.bodyPitchDeg = 86.0f - 18.0f * smooth(p);
                state.bodyLiftOffset = 0.18f;
            } else if (elapsed < FrostTowerFrostnova.REVIVE_STAND_END_MS) {
                // 2.4s ~ 3.2s：在空中缓缓直立站起来（68° -> 0°）
                float p = (elapsed - FrostTowerFrostnova.REVIVE_RISE_END_MS)
                    / (float) (FrostTowerFrostnova.REVIVE_STAND_END_MS - FrostTowerFrostnova.REVIVE_RISE_END_MS);
                state.bodyPitchDeg = 68.0f * (1.0f - smooth(p));
                state.bodyLiftOffset = 0.18f * (1.0f - smooth(p));
            }
        }

        itemModelResolver.updateForLiving(state.staffItem, staffVisual(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (Math.abs(state.bodyPitchDeg) > 0.01f) {
            pose.pushPose();
            pose.translate(0.0f, 0.9f + state.bodyLiftOffset, 0.0f);
            pose.mulPose(Axis.XP.rotationDegrees(-state.bodyPitchDeg));
            pose.translate(0.0f, -0.9f, 0.0f);
            super.submit(state, pose, collector, camera);
            pose.popPose();
            return;
        }
        super.submit(state, pose, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN.body().texturePath();
    }

    private static float smooth(float x) {
        float c = Math.clamp(x, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }

    private static final class StaffLayer extends RenderLayer<State, Model> {
        StaffLayer(FrostnovaBossRenderer renderer) {
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
            state.staffItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
