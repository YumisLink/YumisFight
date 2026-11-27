package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
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
 * 星仪塔第 20 层专属首领「吉尔伽美什（英雄王）」客户端外观渲染器：
 * - 宽版玩家骨骼（PLAYER / WIDE）+ 专属黄金甲红纹皮肤；
 * - 待机呈英雄王招牌抱胸傲立姿，释放王之财宝单手/双臂展开指挥；
 * - 25% 进入 P3 拔出乖离剑后于右手显现旋转赤金宝剑形态（配合 GilgameshBossVisuals 叠加三段旋转乖离圆筒）。
 */
public final class GilgameshBossRenderer extends MobRenderer<DungeonEnemy, GilgameshBossRenderer.State, GilgameshBossRenderer.Model> {
    private static final PlayerSkin SKIN = PlayerSkin.insecure(
        new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ItemData.NS, "entity/orrery_tower_gilgamesh")),
        null, null, PlayerModelType.WIDE
    );

    public static final class State extends AvatarRenderState {
        public final ItemStackRenderState eaItem = new ItemStackRenderState();
        public String move = "";
        public float progress;
        public long elapsedMs;
        public boolean showEa;
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
                if (s.showEa) {
                    // P3 拔剑后：右手斜持乖离剑，左手抱于胸前
                    rightArm.xRot = -0.58f;
                    rightArm.zRot = 0.14f;
                    leftArm.xRot = -0.82f;
                    leftArm.yRot = -0.58f;
                } else {
                    // P1/P2 招牌傲慢抱胸架势
                    rightArm.xRot = -0.88f;
                    rightArm.yRot = 0.62f;
                    rightArm.zRot = -0.10f;
                    leftArm.xRot = -0.88f;
                    leftArm.yRot = -0.62f;
                    leftArm.zRot = 0.10f;
                }
                return;
            }

            switch (s.move) {
                case "babylon_volley", "babylon_wedge", "babylon_sky_rain" -> {
                    // 单手挥斥开启王之财宝
                    float pulse = (float) Math.sin(s.elapsedMs * 0.018) * 0.12f;
                    rightArm.xRot = -1.55f + pulse;
                    rightArm.zRot = 0.28f;
                    leftArm.xRot = -0.78f;
                    leftArm.yRot = -0.52f;
                }
                case "babylon_surround", "royal_repulse" -> {
                    // 双臂大张，金色涟漪环绕
                    rightArm.xRot = -1.35f;
                    rightArm.zRot = 0.88f;
                    leftArm.xRot = -1.35f;
                    leftArm.zRot = -0.88f;
                }
                case "enkidu_chains" -> {
                    // 右臂前伸唤出天之锁
                    rightArm.xRot = -1.62f;
                    rightArm.yRot = -0.15f;
                    leftArm.xRot = -0.85f;
                    leftArm.yRot = -0.55f;
                }
                case "vimana_cruise" -> {
                    // 乘维摩那俯瞰星盘：傲立抱胸
                    rightArm.xRot = -0.92f;
                    rightArm.yRot = 0.64f;
                    leftArm.xRot = -0.92f;
                    leftArm.yRot = -0.64f;
                    rightLeg.xRot = 0.12f;
                    leftLeg.xRot = -0.12f;
                }
                case "draw_ea" -> {
                    // 25% 浮空高举拔出乖离剑
                    float lift = Math.clamp(s.progress * 1.4f, 0.0f, 1.0f);
                    rightArm.xRot = -0.6f - 2.25f * lift;
                    rightArm.zRot = 0.25f * lift;
                    leftArm.xRot = -1.15f * lift;
                    leftArm.zRot = -0.55f * lift;
                }
                case "ea_space_rend" -> {
                    // 手持 Ea 横扫空间撕裂斩
                    float swing = (float) Math.sin(s.progress * Math.PI);
                    rightArm.xRot = -1.75f + swing * 1.05f;
                    rightArm.yRot = -0.75f + swing * 1.55f;
                    leftArm.xRot = -0.75f;
                }
                case "enuma_elish", "finale_enuma" -> {
                    // 天地乖离开辟之星：高举旋转乖离剑蓄力释放
                    float tremor = (float) Math.sin(s.elapsedMs * 0.025) * 0.08f;
                    rightArm.xRot = -2.65f + tremor;
                    rightArm.zRot = 0.22f;
                    leftArm.xRot = -1.45f - tremor;
                    leftArm.zRot = -0.68f;
                }
                default -> {}
            }
        }
    }

    private ItemStack eaVisual;

    public GilgameshBossRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.48f);
        addLayer(new EaLayer(this));
    }

    private ItemStack eaVisual() {
        if (eaVisual == null) eaVisual = EquipmentVisualItems.weaponModel("bylle");
        return eaVisual;
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

        long now = ClientUi.current().serverNow();
        long duration = entity.moveDuration();
        long elapsed = now - entity.moveAt();
        boolean activeMove = duration > 0 && elapsed >= 0 && elapsed < duration;
        state.move = activeMove ? entity.move() : "";
        state.elapsedMs = activeMove ? elapsed : 0L;
        state.progress = activeMove ? (float) Math.clamp(elapsed / (double) duration, 0.0, 1.0) : 0.0f;
        state.showEa = GilgameshBossClient.isEaDrawn()
            || "draw_ea".equals(state.move)
            || "ea_space_rend".equals(state.move)
            || "enuma_elish".equals(state.move)
            || "finale_enuma".equals(state.move);

        if (state.showEa) {
            itemModelResolver.updateForLiving(state.eaItem, eaVisual(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        }
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN.body().texturePath();
    }

    private static final class EaLayer extends RenderLayer<State, Model> {
        EaLayer(GilgameshBossRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yaw, float pitch) {
            if (state.isInvisible || !state.showEa) return;
            pose.pushPose();
            getParentModel().rightArm.translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
            pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            pose.translate(0.0625f, 0.125f, -0.68f);
            pose.scale(1.15f, 1.15f, 1.15f);
            state.eaItem.submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
