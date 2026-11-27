package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.*;

/**
 * Pure-client frozen-pose player afterimages.
 * Captures the caster's exact PlayerPoseAnimation.Frame, position, facing, and skin at trigger time
 * and fades the frozen silhouette out via cached PlayerModel + NearEffectRendering.
 */
public final class PlayerAfterimages {
    private static final int MAX_IMAGES = 64;
    private static final float PLAYER_SCALE = 0.9375f;

    public record Snapshot(
        Vec3 position,
        float bodyRot,
        float headYawOffset,
        float headPitch,
        Identifier skinTexture,
        boolean slim,
        PlayerPoseAnimation.Frame frozenPose,
        int rgb,
        int startAlpha,
        boolean additive,
        long startedAt,
        long durationMs
    ) {}

    public record RenderItem(Snapshot snapshot, int alpha) {}

    private static final List<Snapshot> IMAGES = new ArrayList<>();
    private static PlayerModel wideModel;
    private static PlayerModel slimModel;

    private PlayerAfterimages() {}

    /** Captures a frozen afterimage of the given player at the current moment. */
    public static void capture(Player player, int rgb, int startAlpha, long durationMs, boolean additive) {
        if (player == null || startAlpha <= 0 || durationMs <= 0) return;
        long now = ClientUi.now();
        Vec3 pos = PlayerVisualAnchor.position(player);
        var activePose = Effects.VFX.playerPose(player.getUUID(), now);
        PlayerPoseAnimation.Frame frozen = activePose == null ? null : copyFrame(activePose.frame());
        float bodyYaw = activePose != null && activePose.lockedAim() != null
            ? PlayerPoseChannel.yaw(activePose.lockedAim())
            : PlayerPoseChannel.yaw(PlayerVisualAnchor.forward(player));
        float headYaw = Mth.wrapDegrees(player.getYHeadRot() - bodyYaw);
        float headPitch = player.getXRot();

        Identifier skinTex = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
        boolean slim = false;
        if (player instanceof AbstractClientPlayer clientPlayer) {
            var skin = clientPlayer.getSkin();
            if (skin != null) {
                if (skin.body() != null && skin.body().texturePath() != null) {
                    skinTex = skin.body().texturePath();
                }
                slim = skin.model() == PlayerModelType.SLIM;
            }
        }
        add(new Snapshot(pos, bodyYaw, headYaw, headPitch, skinTex, slim, frozen,
            rgb & 0xFFFFFF, Math.clamp(startAlpha, 1, 255), additive, now, Math.clamp(durationMs, 100L, 2500L)));
    }

    /** Captures a frozen afterimage at an explicit world position (useful for blink/dash departure points). */
    public static void captureAt(Player player, Vec3 pos, Vec3 facing, int rgb, int startAlpha, long durationMs, boolean additive) {
        if (player == null || pos == null || startAlpha <= 0 || durationMs <= 0) return;
        long now = ClientUi.now();
        var activePose = Effects.VFX.playerPose(player.getUUID(), now);
        PlayerPoseAnimation.Frame frozen = activePose == null ? null : copyFrame(activePose.frame());
        float bodyYaw = facing != null && facing.horizontalDistanceSqr() > 1e-4
            ? PlayerPoseChannel.yaw(facing)
            : PlayerPoseChannel.yaw(PlayerVisualAnchor.forward(player));
        Identifier skinTex = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
        boolean slim = false;
        if (player instanceof AbstractClientPlayer clientPlayer) {
            var skin = clientPlayer.getSkin();
            if (skin != null) {
                if (skin.body() != null && skin.body().texturePath() != null) skinTex = skin.body().texturePath();
                slim = skin.model() == PlayerModelType.SLIM;
            }
        }
        add(new Snapshot(pos, bodyYaw, 0f, 0f, skinTex, slim, frozen,
            rgb & 0xFFFFFF, Math.clamp(startAlpha, 1, 255), additive, now, Math.clamp(durationMs, 100L, 2500L)));
    }

    static void add(Snapshot snapshot) {
        synchronized (IMAGES) {
            if (IMAGES.size() >= MAX_IMAGES) IMAGES.removeFirst();
            IMAGES.add(snapshot);
        }
    }

    public static void clear() {
        synchronized (IMAGES) {
            IMAGES.clear();
        }
    }

    public static int activeCount() {
        synchronized (IMAGES) {
            return IMAGES.size();
        }
    }

    static List<RenderItem> extract(long now) {
        synchronized (IMAGES) {
            if (IMAGES.isEmpty()) return List.of();
            List<RenderItem> out = new ArrayList<>(IMAGES.size());
            for (var it = IMAGES.iterator(); it.hasNext(); ) {
                Snapshot s = it.next();
                long age = now - s.startedAt();
                if (age < 0) continue;
                if (age >= s.durationMs()) {
                    it.remove();
                    continue;
                }
                double t = age / (double) s.durationMs();
                int alpha = (int) Math.round(s.startAlpha() * (1.0 - t) * (1.0 - 0.35 * t));
                if (alpha > 2) out.add(new RenderItem(s, alpha));
            }
            return List.copyOf(out);
        }
    }

    static void submit(SubmitCustomGeometryEvent event, List<RenderItem> items, boolean nearFade) {
        if (items.isEmpty() || event.getLevelRenderState().cameraRenderState == null) return;
        ensureModels();
        if (wideModel == null || slimModel == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack pose = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();

        for (RenderItem item : items) {
            Snapshot s = item.snapshot();
            PlayerModel model = s.slim() ? slimModel : wideModel;
            AvatarRenderState state = new AvatarRenderState();
            state.bodyRot = s.bodyRot();
            state.yRot = s.headYawOffset();
            state.xRot = s.headPitch();
            state.showHat = true;
            state.showJacket = true;
            state.showLeftPants = true;
            state.showRightPants = true;
            state.showLeftSleeve = true;
            state.showRightSleeve = true;
            state.showCape = false;
            if (state instanceof PlayerPoseCarrier carrier) {
                carrier.ydnf$skillPose(s.frozenPose());
            }

            pose.pushPose();
            pose.translate(s.position().x - camera.x, s.position().y - camera.y, s.position().z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180f - s.bodyRot()));
            pose.scale(-PLAYER_SCALE, -PLAYER_SCALE, PLAYER_SCALE);
            pose.translate(0f, -1.501f, 0f);
            int argb = (item.alpha() << 24) | s.rgb();
            var renderType = NearEffectRendering.effect(s.skinTexture(), s.additive(), nearFade);
            collector.submitModel(model, state, pose, renderType, 0xF000F0, OverlayTexture.NO_OVERLAY, argb, null, 0, null);
            pose.popPose();
        }
    }

    private static void ensureModels() {
        if (wideModel != null && slimModel != null) return;
        var models = Minecraft.getInstance().getEntityModels();
        if (models == null) return;
        wideModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
        slimModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    static PlayerPoseAnimation.Frame copyFrame(PlayerPoseAnimation.Frame src) {
        if (src == null) return null;
        PlayerPoseAnimation.Frame copy = new PlayerPoseAnimation.Frame();
        System.arraycopy(src.values, 0, copy.values, 0, copy.values.length);
        copy.mask = src.mask;
        copy.heldModel = src.heldModel;
        return copy;
    }
}
