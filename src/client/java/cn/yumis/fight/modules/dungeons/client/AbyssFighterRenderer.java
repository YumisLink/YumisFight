package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.AbyssParty;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.client.ChampionPosePort;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

/** Existing humanoid silhouettes with distinct attack, leap and casting stances. */
public final class AbyssFighterRenderer extends HumanoidMobRenderer<DungeonEnemy, AbyssFighterRenderer.State, AbyssFighterRenderer.Model> {
    private static final Identifier BONE = Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
    private static final Identifier STEEL = Identifier.withDefaultNamespace("textures/entity/zombie/husk.png");
    private final AbyssParty.Enemy kind;

    public static final class State extends HumanoidRenderState {
        Identifier texture;
        String action = "";
        double progress;
    }

    public static final class Model extends HumanoidModel<State> {
        Model(ModelPart root) { super(root); }

        @Override public void setupAnim(State state) {
            super.setupAnim(state);
            if (state.action.isEmpty()) return;
            if (state.action.equals("abyss_leap")) {
                rightArm.xRot = leftArm.xRot = -2.75f;
                rightLeg.xRot = -.4f;
                leftLeg.xRot = .33f;
            } else if (state.action.equals("abyss_column")) {
                rightArm.xRot = -2.50f;
                leftArm.xRot = -1.55f;
            } else if (state.action.equals("abyss_basic")) {
                rightArm.xRot = state.progress < .65 ? -2.25f : -.78f;
                leftArm.xRot = -.64f;
            } else {
                float follow = (float) Math.sin(state.progress * Math.PI);
                rightArm.xRot = -1.35f - follow;
                leftArm.xRot = -1.10f - .6f * follow;
                rightArm.zRot = .15f;
                leftArm.zRot = -.15f;
            }
        }
    }

    public AbyssFighterRenderer(EntityRendererProvider.Context context, AbyssParty.Enemy kind) {
        super(context, new Model(context.bakeLayer(kind == AbyssParty.Enemy.SKELETON ? ModelLayers.SKELETON : ModelLayers.PLAYER)), .35f);
        this.kind = kind;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(DungeonEnemy entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.texture = switch (kind) {
            case SKELETON -> BONE;
            case GIANT -> STEEL;
            default -> DefaultPlayerSkin.get(entity.getUUID()).body().texturePath();
        };
        if (kind == AbyssParty.Enemy.GIANT) state.scale *= 1.83f;
        long now = ClientUi.current().serverNow();
        state.progress = entity.moveDuration() <= 0 ? 1 : (now - entity.moveAt()) / (double) entity.moveDuration();
        state.action = state.progress >= 0 && state.progress < 1 ? entity.move() : "";
        if (state.action.startsWith("abyss_skill/") && ChampionPosePort.apply(entity, state, now)) state.action = "";
    }

    @Override public Identifier getTextureLocation(State state) { return state.texture; }
}
