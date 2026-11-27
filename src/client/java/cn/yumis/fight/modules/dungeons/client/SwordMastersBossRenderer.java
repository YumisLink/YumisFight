package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.content.ForgeTowerSwordMasters.Member;
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
 * 锻炉塔第 20 层专属首领「阿拉德四剑圣」客户端外观渲染器：
 * 四位剑圣共用标准玩家骨骼，分别加载专属 64x64 皮肤与四大招牌 3D 兵器模型（钝器/短剑/巨剑/太刀）及动作姿态。
 */
public final class SwordMastersBossRenderer extends MobRenderer<DungeonEnemy, SwordMastersBossRenderer.State, SwordMastersBossRenderer.Model> {
    private final Member member;
    private final PlayerSkin skin;
    private ItemStack weaponVisual;

    public static final class State extends AvatarRenderState {
        public final ItemStackRenderState weaponItem = new ItemStackRenderState();
        public String move = "";
        public float progress;
        public long elapsedMs;
        public Member member = Member.BWANGA;
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
                switch (s.member) {
                    case BWANGA -> {
                        rightArm.xRot = -0.62f;
                        rightArm.zRot = 0.14f;
                        leftArm.xRot = -0.35f;
                    }
                    case BAAN -> {
                        rightArm.xRot = -0.35f;
                        rightArm.yRot = -0.22f;
                        leftArm.xRot = -0.45f;
                    }
                    case AGANZO -> {
                        rightArm.xRot = -0.78f;
                        rightArm.zRot = 0.22f;
                        leftArm.xRot = -0.52f;
                        leftArm.yRot = 0.25f;
                    }
                    case SIRAN -> {
                        // 太刀按鞘居合待机
                        rightArm.xRot = -0.28f;
                        rightArm.yRot = 0.35f;
                        leftArm.xRot = -0.65f;
                        leftArm.yRot = -0.38f;
                    }
                }
                return;
            }
            switch (s.move) {
                case "heavy_slam_windup", "awakening_leap", "awakening_charge" -> {
                    rightArm.xRot = -2.65f;
                    rightArm.zRot = 0.15f;
                    leftArm.xRot = -2.45f;
                    leftArm.zRot = -0.15f;
                }
                case "heavy_slam_hit", "greatsword_cleave", "awakening_finisher" -> {
                    float cleave = Math.min(1.0f, s.progress * 1.7f);
                    rightArm.xRot = -2.45f + cleave * 2.05f;
                    rightArm.yRot = -0.45f + cleave * 0.95f;
                    leftArm.xRot = -1.95f + cleave * 1.45f;
                }
                case "charge_windup", "charge_dash", "rising_dash" -> {
                    rightArm.xRot = -1.35f;
                    rightArm.yRot = -0.35f;
                    leftArm.xRot = 0.55f;
                    rightLeg.xRot = -0.58f;
                    leftLeg.xRot = 0.58f;
                }
                case "thrust_combo", "zigzag_step" -> {
                    float jab = (float) Math.sin(s.elapsedMs * 0.028);
                    rightArm.xRot = -1.55f + jab * 0.45f;
                    rightArm.yRot = 0.18f;
                    leftArm.xRot = 0.45f;
                }
                case "blade_fan", "whirlwind_spin" -> {
                    float spin = (float) Math.sin(s.elapsedMs * 0.032);
                    rightArm.xRot = -1.55f;
                    rightArm.yRot = spin * 1.15f;
                    rightArm.zRot = 0.72f;
                    leftArm.xRot = -1.35f;
                    leftArm.yRot = -spin * 0.95f;
                    leftArm.zRot = -0.65f;
                }
                case "iai_sheathe", "siran_awakening" -> {
                    rightArm.xRot = -0.55f;
                    rightArm.yRot = 0.65f;
                    leftArm.xRot = -0.75f;
                    leftArm.yRot = -0.55f;
                    rightLeg.xRot = -0.32f;
                    leftLeg.xRot = 0.32f;
                }
                case "iai_slash", "echo_cross", "heavy_sweep", "upper_slash", "light_slash", "hammer_throw" -> {
                    float sweep = Math.min(1.0f, s.progress * 1.8f);
                    rightArm.xRot = -1.75f + sweep * 1.35f;
                    rightArm.yRot = -0.95f + sweep * 1.95f;
                    rightArm.zRot = 0.38f;
                    leftArm.xRot = -0.55f;
                }
                case "exhaust_stagger" -> {
                    // 收剑破绽低头喘息姿态
                    head.xRot = 0.42f;
                    rightArm.xRot = -0.85f;
                    rightArm.zRot = 0.12f;
                    leftArm.xRot = -0.35f;
                }
                case "retreat_watch", "p3_return" -> {
                    rightArm.xRot = -0.45f;
                    rightArm.yRot = 0.38f;
                    leftArm.xRot = -0.45f;
                    leftArm.yRot = -0.38f;
                }
                default -> {}
            }
        }
    }

    public SwordMastersBossRenderer(EntityRendererProvider.Context context, Member member) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.48f);
        this.member = member;
        this.skin = PlayerSkin.insecure(
            new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ItemData.NS, "entity/" + member.id)),
            null, null, PlayerModelType.WIDE
        );
        addLayer(new WeaponLayer(this));
    }

    private ItemStack weaponVisual() {
        if (weaponVisual == null) weaponVisual = EquipmentVisualItems.weaponModel(member.weaponModelId);
        return weaponVisual;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DungeonEnemy entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, itemModelResolver);
        state.skin = skin;
        state.id = entity.getId();
        state.member = member;

        long now = cn.yumis.fight.modules.presentation.api.client.ClientUi.current().serverNow();
        long duration = entity.moveDuration();
        long elapsed = now - entity.moveAt();
        boolean activeMove = duration > 0 && elapsed >= 0 && elapsed < duration;
        state.move = activeMove ? entity.move() : "";
        state.elapsedMs = activeMove ? elapsed : 0L;
        state.progress = activeMove ? (float) Math.clamp(elapsed / (double) duration, 0.0, 1.0) : 0.0f;

        itemModelResolver.updateForLiving(state.weaponItem, weaponVisual(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return skin.body().texturePath();
    }

    private static final class WeaponLayer extends RenderLayer<State, Model> {
        WeaponLayer(SwordMastersBossRenderer renderer) {
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
            if (state.member == Member.AGANZO || state.member == Member.BWANGA) {
                pose.scale(1.18f, 1.18f, 1.18f);
            }
            state.weaponItem.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
