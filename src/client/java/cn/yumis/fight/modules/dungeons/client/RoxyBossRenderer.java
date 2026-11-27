package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.content.SluiceTowerRoxy;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.platform.ItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * 水闸塔第 20 层专属首领「洛琪希」客户端外观渲染器：
 * - 纤细玩家骨骼（PLAYER_SLIM）+ 专属蓝发双辫法师袍皮肤（sluice_tower_roxy.png）+ 娇小体型微缩（0.94×）；
 * - 专属 3D 宽檐微歪尖顶大巫师帽（含随暴雨摇曳的帽尖与蓝金帽带飘带）；
 * - 专属 3D 蓝宝石水龙法杖（深胡桃木杖身 + 银蓝龙角双爪 + 全亮自发光旋转蓝宝石晶核）；
 * - 完整骨骼动作编排：水圣弹幕、高压水刃横扫、激流顿杖、水镜后撤、潮汐展开、12s 半跪→升空长咏唱、破核虚弱喘息、豪雷指天、台风眼高空御风。
 *
 * 注：必须继承 HumanoidRenderState（而非 AvatarRenderState），避免 MC 26.2 EntityRenderDispatcher.getRenderer(EntityRenderState)
 * 将 AvatarRenderState 分流给原版 AvatarRenderer 而跳过本渲染器的自定义动作与 3D 装备层。
 */
public final class RoxyBossRenderer extends MobRenderer<DungeonEnemy, RoxyBossRenderer.State, RoxyBossRenderer.RoxyBodyModel> {
    public static final ModelLayerLocation GEAR_LAYER =
        new ModelLayerLocation(Identifier.fromNamespaceAndPath(ItemData.NS, "sluice_tower_roxy_gear"), "main");

    private static final Identifier BODY_TEXTURE =
        Identifier.fromNamespaceAndPath(ItemData.NS, "textures/entity/sluice_tower_roxy.png");
    private static final Identifier GEAR_TEXTURE =
        Identifier.fromNamespaceAndPath(ItemData.NS, "textures/entity/sluice_tower_roxy_gear.png");

    public static final class State extends HumanoidRenderState {
        public String move = "";
        public float progress;
        public long elapsedMs;
        public float bodyTiltDeg;
        public float bodySinkOffset;
        public boolean stormActive;
        public float gemSpinRad;
        public float windSwayRad;
    }

    public static final class RoxyBodyModel extends HumanoidModel<State> {
        RoxyBodyModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);

            if ("cumulonimbus_chant".equals(s.move)) {
                long t = s.elapsedMs;
                if (t < SluiceTowerRoxy.TRANSITION_KNEEL_END_MS) {
                    // 0.0s ~ 1.2s：半跪俯身、右手拄杖稳住身形
                    float p = smooth(t / (float) Math.max(1L, SluiceTowerRoxy.TRANSITION_KNEEL_END_MS));
                    head.xRot = 0.28f * p;
                    rightArm.xRot = -0.78f * p;
                    rightArm.zRot = 0.14f * p;
                    leftArm.xRot = -0.45f * p;
                    leftArm.zRot = -0.22f * p;
                    rightLeg.xRot = -0.82f * p;
                    leftLeg.xRot = 0.55f * p;
                } else if (t < SluiceTowerRoxy.TRANSITION_RISE_END_MS) {
                    // 1.2s ~ 3.0s：水柱托举升空，双臂缓缓举起法杖
                    float p = smooth((t - SluiceTowerRoxy.TRANSITION_KNEEL_END_MS)
                        / (float) Math.max(1L, SluiceTowerRoxy.TRANSITION_RISE_END_MS - SluiceTowerRoxy.TRANSITION_KNEEL_END_MS));
                    head.xRot = 0.28f * (1.0f - p) - 0.18f * p;
                    rightArm.xRot = -0.78f - 1.65f * p;
                    rightArm.zRot = 0.14f + 0.16f * p;
                    leftArm.xRot = -0.45f - 1.50f * p;
                    leftArm.zRot = -0.22f - 0.16f * p;
                    rightLeg.xRot = 0.22f * p;
                    leftLeg.xRot = -0.14f * p;
                } else if (t < SluiceTowerRoxy.TRANSITION_DESCEND_START_MS) {
                    // 3.0s ~ 10.8s：高空仰首长咏唱《豪雷积雨云》，双臂高擎法杖微颤聚能
                    float sway = (float) Math.sin(t * 0.008) * 0.08f;
                    float pulse = (float) Math.sin(t * 0.018) * 0.05f;
                    head.xRot = -0.24f + sway * 0.4f;
                    rightArm.xRot = -2.48f + pulse;
                    rightArm.zRot = 0.26f + sway;
                    leftArm.xRot = -2.18f - pulse;
                    leftArm.zRot = -0.34f - sway;
                    rightLeg.xRot = 0.24f + sway * 0.6f;
                    leftLeg.xRot = -0.12f - sway * 0.6f;
                } else {
                    // 10.8s ~ 12.0s：法杖奋力下挥引爆天雷降落
                    float p = smooth((t - SluiceTowerRoxy.TRANSITION_DESCEND_START_MS)
                        / (float) Math.max(1L, SluiceTowerRoxy.TRANSITION_TOTAL_MS - SluiceTowerRoxy.TRANSITION_DESCEND_START_MS));
                    rightArm.xRot = -2.48f + 1.75f * p;
                    rightArm.zRot = 0.26f;
                    leftArm.xRot = -2.18f + 1.25f * p;
                    leftArm.zRot = -0.38f;
                    rightLeg.xRot = 0.18f * (1.0f - p);
                    leftLeg.xRot = -0.10f * (1.0f - p);
                }
                return;
            }

            if ("storm_exhausted".equals(s.move)) {
                // 全破水核 / 台风眼落地后的魔力反噬喘息姿态
                float breath = (float) Math.sin(s.elapsedMs * 0.012) * 0.06f;
                head.xRot = 0.34f + breath;
                rightArm.xRot = -0.68f + breath;
                rightArm.zRot = 0.10f;
                leftArm.xRot = -0.42f;
                leftArm.yRot = -0.35f;
                return;
            }

            if (s.move.isEmpty()) {
                // 洛琪希端庄持杖待机姿态（二阶段略增浮空呼吸感）
                float breathe = (float) Math.sin(s.ageInTicks * 0.10f) * 0.04f;
                rightArm.xRot = -0.52f + breathe;
                rightArm.zRot = 0.08f;
                leftArm.xRot = -0.24f - breathe * 0.5f;
                leftArm.zRot = -0.06f;
                return;
            }

            switch (s.move) {
                case "water_barrage" -> {
                    // 右手高举蓝宝石法杖，左手平伸指挥背后双弧水枪齐射
                    float pulse = (float) Math.sin(s.elapsedMs * 0.020) * 0.12f;
                    rightArm.xRot = -1.95f + pulse;
                    rightArm.zRot = 0.22f;
                    leftArm.xRot = -1.48f - pulse * 0.6f;
                    leftArm.zRot = -0.18f;
                }
                case "aqua_blade" -> {
                    // 蓄力侧拉法杖 -> 水平横扫切出月牙高压水刃
                    if (s.progress < 0.69f) {
                        float u = smooth(s.progress / 0.69f);
                        rightArm.xRot = -1.35f - 0.35f * u;
                        rightArm.yRot = -0.85f * u;
                        rightArm.zRot = 0.45f * u;
                        leftArm.xRot = -0.80f;
                    } else {
                        float u = smooth((s.progress - 0.69f) / 0.31f);
                        rightArm.xRot = -1.70f + 0.65f * u;
                        rightArm.yRot = -0.85f + 1.80f * u;
                        rightArm.zRot = 0.45f - 0.55f * u;
                        leftArm.xRot = -0.65f;
                        leftArm.zRot = -0.35f * u;
                    }
                }
                case "geyser_eruption" -> {
                    // 高举法杖 -> 重重顿地引爆高压水柱
                    float slam = (float) Math.sin(Math.min(1.0f, s.progress * 1.4f) * Math.PI);
                    rightArm.xRot = -1.05f - 1.35f * slam;
                    rightArm.zRot = 0.14f;
                    leftArm.xRot = -0.95f - 0.95f * slam;
                    leftArm.zRot = -0.18f;
                }
                case "mirror_blink" -> {
                    // 轻盈后仰滑退，左手挥袖展开水镜
                    rightArm.xRot = -0.92f;
                    rightArm.zRot = 0.38f;
                    leftArm.xRot = -1.42f;
                    leftArm.zRot = -0.52f;
                    rightLeg.xRot = -0.32f;
                    leftLeg.xRot = 0.36f;
                }
                case "tidal_rings" -> {
                    // 先胸前聚魔 -> 双臂优雅大张推开双层潮汐环
                    if (s.progress < 0.35f) {
                        rightArm.xRot = -1.15f;
                        rightArm.yRot = 0.52f;
                        leftArm.xRot = -1.15f;
                        leftArm.yRot = -0.52f;
                    } else {
                        float wave = (float) Math.sin(s.elapsedMs * 0.008) * 0.14f;
                        rightArm.xRot = -1.42f + wave;
                        rightArm.zRot = 0.88f;
                        leftArm.xRot = -1.42f - wave;
                        leftArm.zRot = -0.88f;
                    }
                }
                case "thunder_cascade" -> {
                    // 右手法杖直指天穹积雨云连点引雷
                    float beat = (float) Math.sin(s.elapsedMs * 0.016) * 0.18f;
                    head.xRot = -0.22f;
                    rightArm.xRot = -2.62f + beat;
                    rightArm.zRot = 0.18f;
                    leftArm.xRot = -1.05f;
                    leftArm.zRot = -0.35f;
                }
                case "typhoon_eye" -> {
                    // 高空御风指挥《天穹雷狱·台风眼》：双臂大张斜擎法杖
                    float sway = (float) Math.sin(s.elapsedMs * 0.009) * 0.15f;
                    head.xRot = -0.12f;
                    rightArm.xRot = -2.20f + sway;
                    rightArm.zRot = 0.62f;
                    leftArm.xRot = -1.85f - sway;
                    leftArm.zRot = -0.78f;
                    rightLeg.xRot = 0.26f;
                    leftLeg.xRot = -0.16f;
                }
                default -> {}
            }
        }
    }

    /** 构建 64×64 专属 3D 宽檐尖顶大巫师帽 + 蓝宝石水龙法杖几何层（严格对齐 build_roxy_assets.py UV）。 */
    public static LayerDefinition gearLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 1. 专属 3D 宽檐微歪尖顶巫师帽（挂载至头部骨骼，紧贴发冠）
        PartDefinition hat = root.addOrReplaceChild("witch_hat", CubeListBuilder.create(), PartPose.ZERO);
        hat.addOrReplaceChild("brim",
            CubeListBuilder.create().texOffs(0, 0).addBox(-7.5f, -8.4f, -7.5f, 15, 1, 15),
            PartPose.ZERO);
        PartDefinition crownBase = hat.addOrReplaceChild("crown_base",
            CubeListBuilder.create().texOffs(0, 16).addBox(-4.5f, -4.0f, -4.5f, 9, 4, 9),
            PartPose.offset(0.0f, -8.2f, 0.0f));
        PartDefinition crownMid = crownBase.addOrReplaceChild("crown_mid",
            CubeListBuilder.create().texOffs(36, 16).addBox(-3.0f, -4.0f, -3.0f, 6, 4, 6),
            PartPose.offsetAndRotation(0.25f, -3.6f, 0.35f, -0.18f, 0.0f, 0.12f));
        crownMid.addOrReplaceChild("crown_tip",
            CubeListBuilder.create().texOffs(36, 26).addBox(-1.5f, -4.8f, -1.5f, 3, 5, 3),
            PartPose.offsetAndRotation(0.35f, -3.6f, 0.55f, -0.44f, 0.0f, 0.24f));
        hat.addOrReplaceChild("ribbon_l",
            CubeListBuilder.create().texOffs(48, 26).addBox(-0.5f, 0.0f, -0.5f, 1, 4, 1),
            PartPose.offsetAndRotation(-4.2f, -8.0f, 3.6f, 0.32f, 0.0f, 0.16f));
        hat.addOrReplaceChild("ribbon_r",
            CubeListBuilder.create().texOffs(48, 26).addBox(-0.5f, 0.0f, -0.5f, 1, 4, 1),
            PartPose.offsetAndRotation(-2.8f, -8.0f, 4.2f, 0.36f, 0.0f, 0.06f));

        // 2. 专属 3D 蓝宝石水龙法杖本体（挂载至右手）
        PartDefinition staff = root.addOrReplaceChild("roxy_staff", CubeListBuilder.create(), PartPose.ZERO);
        staff.addOrReplaceChild("shaft",
            CubeListBuilder.create().texOffs(0, 32).addBox(-1.0f, -14.0f, -1.0f, 2, 24, 2, new CubeDeformation(-0.22f)),
            PartPose.ZERO);
        staff.addOrReplaceChild("collar",
            CubeListBuilder.create().texOffs(8, 32).addBox(-1.5f, -15.6f, -1.5f, 3, 2, 3),
            PartPose.ZERO);
        PartDefinition prongL = staff.addOrReplaceChild("prong_l",
            CubeListBuilder.create().texOffs(20, 32).addBox(-0.5f, -4.0f, -0.5f, 1, 4, 1),
            PartPose.offsetAndRotation(-1.3f, -15.2f, 0.0f, 0.0f, 0.0f, -0.34f));
        prongL.addOrReplaceChild("prong_tip_l",
            CubeListBuilder.create().texOffs(24, 32).addBox(-0.5f, -3.0f, -0.5f, 1, 3, 1),
            PartPose.offsetAndRotation(0.0f, -3.7f, 0.0f, 0.0f, 0.0f, 0.58f));
        PartDefinition prongR = staff.addOrReplaceChild("prong_r",
            CubeListBuilder.create().texOffs(20, 32).addBox(-0.5f, -4.0f, -0.5f, 1, 4, 1),
            PartPose.offsetAndRotation(1.3f, -15.2f, 0.0f, 0.0f, 0.0f, 0.34f));
        prongR.addOrReplaceChild("prong_tip_r",
            CubeListBuilder.create().texOffs(24, 32).addBox(-0.5f, -3.0f, -0.5f, 1, 3, 1),
            PartPose.offsetAndRotation(0.0f, -3.7f, 0.0f, 0.0f, 0.0f, -0.58f));
        staff.addOrReplaceChild("tassel",
            CubeListBuilder.create().texOffs(28, 32).addBox(-0.5f, 0.0f, -0.5f, 1, 4, 1),
            PartPose.offsetAndRotation(0.0f, -13.6f, 0.9f, 0.25f, 0.0f, 0.0f));

        // 3. 杖顶全亮旋转蓝宝石魔导结晶核 + 半透明水光辉晕（独立全亮提交）
        PartDefinition gem = root.addOrReplaceChild("sapphire_gem", CubeListBuilder.create(), PartPose.ZERO);
        gem.addOrReplaceChild("sapphire_core",
            CubeListBuilder.create().texOffs(8, 40).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3, new CubeDeformation(-0.15f)),
            PartPose.ZERO);
        gem.addOrReplaceChild("sapphire_aura",
            CubeListBuilder.create().texOffs(8, 48).addBox(-2.0f, -2.0f, -2.0f, 4, 4, 4, new CubeDeformation(-0.25f)),
            PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static final class WitchHatModel extends Model<State> {
        private final ModelPart crownTip;
        private final ModelPart ribbonL;
        private final ModelPart ribbonR;

        WitchHatModel(ModelPart hatRoot) {
            super(hatRoot, RenderTypes::entityCutout);
            this.crownTip = hatRoot.getChild("crown_base").getChild("crown_mid").getChild("crown_tip");
            this.ribbonL = hatRoot.getChild("ribbon_l");
            this.ribbonR = hatRoot.getChild("ribbon_r");
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            crownTip.xRot = -0.44f - Math.abs(state.windSwayRad) * 0.55f;
            crownTip.zRot = 0.24f + state.windSwayRad * 0.65f;
            ribbonL.xRot = 0.32f + Math.abs(state.windSwayRad) * 1.4f;
            ribbonR.xRot = 0.36f + Math.abs(state.windSwayRad) * 1.25f;
        }
    }

    private static final class StaffModel extends Model<State> {
        private final ModelPart tassel;

        StaffModel(ModelPart staffRoot) {
            super(staffRoot, RenderTypes::entityCutout);
            this.tassel = staffRoot.getChild("tassel");
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            tassel.xRot = 0.25f + Math.abs(state.windSwayRad) * 1.35f;
        }
    }

    private static final class SapphireGemModel extends Model<State> {
        SapphireGemModel(ModelPart gemRoot) {
            super(gemRoot, RenderTypes::entityTranslucentEmissive);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
        }
    }

    private final WitchHatModel hatModel;
    private final StaffModel staffModel;
    private final SapphireGemModel gemModel;

    public RoxyBossRenderer(EntityRendererProvider.Context context) {
        super(context, new RoxyBodyModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.42f);
        ModelPart gearRoot = context.bakeLayer(GEAR_LAYER);
        this.hatModel = new WitchHatModel(gearRoot.getChild("witch_hat"));
        this.staffModel = new StaffModel(gearRoot.getChild("roxy_staff"));
        this.gemModel = new SapphireGemModel(gearRoot.getChild("sapphire_gem"));
        addLayer(new GearLayer(this));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DungeonEnemy entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, itemModelResolver);

        long now = ClientUi.current().serverNow();
        long duration = entity.moveDuration();
        long elapsed = now - entity.moveAt();
        boolean activeMove = duration > 0 && elapsed >= -250L && elapsed < duration;
        state.move = activeMove ? entity.move() : "";
        state.elapsedMs = activeMove ? Math.max(0L, elapsed) : 0L;
        state.progress = activeMove ? (float) Math.clamp(state.elapsedMs / (double) duration, 0.0, 1.0) : 0.0f;

        // 若正处于《豪雷积雨云》转场或《台风眼》大招且实体动作同步延迟，直接对齐客户端权威演出时间轴
        if (state.move.isEmpty()) {
            RoxyBossClient.TransitionVisual tv = RoxyBossClient.transitionVisual;
            if (tv != null && now >= tv.startAt && now < tv.endAt) {
                state.move = "cumulonimbus_chant";
                state.elapsedMs = now - tv.startAt;
                state.progress = (float) Math.clamp(state.elapsedMs / (double) Math.max(1L, tv.endAt - tv.startAt), 0.0, 1.0);
            } else {
                RoxyBossClient.TyphoonEyeVisual eye = RoxyBossClient.typhoonEye;
                if (eye != null && now >= eye.startAt && now < eye.endAt) {
                    state.move = "typhoon_eye";
                    state.elapsedMs = now - eye.startAt;
                    state.progress = (float) Math.clamp(state.elapsedMs / (double) Math.max(1L, eye.endAt - eye.startAt), 0.0, 1.0);
                }
            }
        }

        state.stormActive = RoxyBossClient.isStormActive();
        state.gemSpinRad = (float) ((now % 60_000L) * (state.stormActive ? 0.0042 : 0.0020));
        state.windSwayRad = (float) Math.sin(now * (state.stormActive ? 0.009 : 0.003)) * (state.stormActive ? 0.16f : 0.04f);

        // 转场初期半跪俯身 & 全破虚弱前倾身体偏移
        state.bodyTiltDeg = 0.0f;
        state.bodySinkOffset = 0.0f;
        if ("cumulonimbus_chant".equals(state.move) && state.elapsedMs < SluiceTowerRoxy.TRANSITION_KNEEL_END_MS) {
            float p = smooth(state.elapsedMs / (float) Math.max(1L, SluiceTowerRoxy.TRANSITION_KNEEL_END_MS));
            state.bodyTiltDeg = 16.0f * p;
            state.bodySinkOffset = -0.28f * p;
        } else if ("cumulonimbus_chant".equals(state.move) && state.elapsedMs < SluiceTowerRoxy.TRANSITION_RISE_END_MS) {
            float p = smooth((state.elapsedMs - SluiceTowerRoxy.TRANSITION_KNEEL_END_MS)
                / (float) Math.max(1L, SluiceTowerRoxy.TRANSITION_RISE_END_MS - SluiceTowerRoxy.TRANSITION_KNEEL_END_MS));
            state.bodyTiltDeg = 16.0f * (1.0f - p);
            state.bodySinkOffset = -0.28f * (1.0f - p);
        } else if ("storm_exhausted".equals(state.move)) {
            state.bodyTiltDeg = 12.0f;
            state.bodySinkOffset = -0.10f;
        }

        state.boundingBoxHeight = Math.max(state.boundingBoxHeight, 2.75f * state.scale);
        if (state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, 0.72 * state.scale, 0);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // 米格路德族娇小身形比例（0.94×），同时保留优雅舒展的大巫师帽与长法杖轮廓
        pose.scale(0.94f, 0.94f, 0.94f);
        if (Math.abs(state.bodySinkOffset) > 0.001f) {
            pose.translate(0.0f, state.bodySinkOffset, 0.0f);
        }
        if (Math.abs(state.bodyTiltDeg) > 0.01f) {
            pose.translate(0.0f, 0.75f, 0.0f);
            pose.mulPose(Axis.XP.rotationDegrees(state.bodyTiltDeg));
            pose.translate(0.0f, -0.75f, 0.0f);
        }
        super.submit(state, pose, collector, camera);
        pose.popPose();
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return BODY_TEXTURE;
    }

    private static float smooth(float x) {
        float c = Math.clamp(x, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }

    private static final class GearLayer extends RenderLayer<State, RoxyBodyModel> {
        private final RoxyBossRenderer owner;

        GearLayer(RoxyBossRenderer renderer) {
            super(renderer);
            this.owner = renderer;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yaw, float pitch) {
            if (state.isInvisible) return;
            var cutout = RenderTypes.entityCutout(GEAR_TEXTURE);
            var emissive = RenderTypes.entityTranslucentEmissive(GEAR_TEXTURE);

            // 1. 渲染宽檐尖顶大巫师帽（随头部转动 + 暴雨风力摇曳帽尖与帽带）
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            collector.submitModel(owner.hatModel, state, pose, cutout, light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
            pose.popPose();

            // 2. 渲染蓝宝石水龙法杖（右手持握）+ 全亮旋转蓝宝石晶核
            pose.pushPose();
            getParentModel().rightArm.translateAndRotate(pose);
            pose.translate(-0.04f, 0.56f, -0.08f);
            pose.mulPose(Axis.XP.rotationDegrees(42.0f));
            collector.submitModel(owner.staffModel, state, pose, cutout, light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);

            // 全亮蓝宝石晶核于双龙角爪中央缓慢自旋（位于杖身局部 y = -18.3 / 16 ≈ -1.145）
            pose.pushPose();
            pose.translate(0.0f, -1.145f, 0.0f);
            pose.mulPose(Axis.YP.rotation(state.gemSpinRad));
            pose.mulPose(Axis.ZP.rotationDegrees(22.0f));
            float gemScale = state.stormActive ? 1.25f : 1.12f;
            pose.scale(gemScale, gemScale, gemScale);
            collector.submitModel(owner.gemModel, state, pose, emissive, 0xF000F0, OverlayTexture.NO_OVERLAY, -1, null, 0, null);
            pose.popPose();

            pose.popPose();
        }
    }
}
