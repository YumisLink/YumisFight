package cn.yumis.fight.modules.skills.client;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Pure-math world-space bone socket extractor matching vanilla HumanoidModel + PlayerPoseAnimation hierarchy.
 * Enables weapon-tip trails, charge orbs, and slash origins to track the exact animated hand/blade position.
 */
public final class PlayerBoneAnchor {
    private static final int ROOT = 0;
    private static final int RIGHT_ARM = 3;
    private static final int LEFT_ARM = 4;
    private static final int HELD_ITEM = PlayerPoseAnimation.HELD_ITEM;
    private static final float PLAYER_SCALE = 0.9375f;

    private PlayerBoneAnchor() {}

    /** Exact world position of the caster's main-hand or off-hand palm under the active skill pose. */
    public static Vec3 hand(Player player, PlayerPoseAnimation.Frame pose, boolean mainHand) {
        if (player == null) return Vec3.ZERO;
        Vec3 base = PlayerVisualAnchor.position(player);
        boolean rightArm = mainHand ? player.getMainArm() == HumanoidArm.RIGHT : player.getMainArm() == HumanoidArm.LEFT;
        return transformPoint(base, bodyYaw(player, pose), pose, rightArm, false, 0f);
    }

    /** Exact world position along the held weapon axis (weaponLength in blocks from hand grip to tip). */
    public static Vec3 weaponTip(Player player, PlayerPoseAnimation.Frame pose, double weaponLength) {
        if (player == null) return Vec3.ZERO;
        Vec3 base = PlayerVisualAnchor.position(player);
        boolean rightArm = player.getMainArm() == HumanoidArm.RIGHT;
        return transformPoint(base, bodyYaw(player, pose), pose, rightArm, true, (float) Math.max(0.1, weaponLength));
    }

    /** World position of the player's torso center including root bone translation/flip. */
    public static Vec3 bodyCenter(Player player, PlayerPoseAnimation.Frame pose) {
        if (player == null) return Vec3.ZERO;
        Vec3 base = PlayerVisualAnchor.position(player);
        Matrix4f m = rootMatrix(base, bodyYaw(player, pose), pose);
        Vector4f p = new Vector4f(0f, 6f / 16f, 0f, 1f).mul(m);
        return new Vec3(p.x, p.y, p.z);
    }

    /** Pure-math entry point (no Minecraft entity needed) for deterministic testing and offline sampling. */
    public static Vec3 sampleWeaponTip(Vec3 entityPos, float bodyYawDeg, boolean rightArm, PlayerPoseAnimation.Frame pose, double weaponLength) {
        return transformPoint(entityPos, bodyYawDeg, pose, rightArm, true, (float) Math.max(0.0, weaponLength));
    }

    private static float bodyYaw(Player player, PlayerPoseAnimation.Frame pose) {
        // Mirror AvatarRenderer: during an active pose the model aligns with body/look heading
        Vec3 forward = PlayerVisualAnchor.forward(player);
        return PlayerPoseChannel.yaw(forward);
    }

    private static Matrix4f rootMatrix(Vec3 entityPos, float bodyYawDeg, PlayerPoseAnimation.Frame pose) {
        Matrix4f m = new Matrix4f();
        m.translate((float) entityPos.x, (float) entityPos.y, (float) entityPos.z);
        m.rotateY((float) Math.toRadians(180f - bodyYawDeg));
        m.scale(-PLAYER_SCALE, -PLAYER_SCALE, PLAYER_SCALE);
        m.translate(0f, -1.501f, 0f);
        if (pose != null && (pose.mask & (1 << ROOT)) != 0) {
            applyBone(m, pose, ROOT, 0f, 0f, 0f);
        }
        return m;
    }

    private static Vec3 transformPoint(
        Vec3 entityPos, float bodyYawDeg, PlayerPoseAnimation.Frame pose,
        boolean rightArm, boolean weaponTip, float weaponLength
    ) {
        Matrix4f m = rootMatrix(entityPos, bodyYawDeg, pose);
        int armBone = rightArm ? RIGHT_ARM : LEFT_ARM;
        float pivotX = rightArm ? -5.0f : 5.0f;
        float pivotY = 2.0f;
        float pivotZ = 0.0f;
        if (pose != null && (pose.mask & (1 << armBone)) != 0) {
            applyBone(m, pose, armBone, pivotX, pivotY, pivotZ);
        } else {
            m.translate(pivotX / 16f, pivotY / 16f, pivotZ / 16f);
        }
        // Standard humanoid hand socket at the bottom of the arm (10 model units below shoulder pivot)
        m.translate((rightArm ? -1.0f : 1.0f) / 16f, 10.0f / 16f, 0f);
        if (weaponTip) {
            if (pose != null && (pose.mask & (1 << HELD_ITEM)) != 0) {
                int off = HELD_ITEM * PlayerPoseAnimation.AXES;
                m.rotateX((float) Math.toRadians(pose.values[off]));
                m.rotateY((float) Math.toRadians(pose.values[off + 1]));
                m.rotateZ((float) Math.toRadians(pose.values[off + 2]));
            }
            // In humanoid model space (-Z faces forward after 180-yaw and (-1,-1,1) scale), held blades extend forward along -Z
            Vector4f p = new Vector4f(0f, 0f, -weaponLength, 1f).mul(m);
            return new Vec3(p.x, p.y, p.z);
        }
        Vector4f p = new Vector4f(0f, 0f, 0f, 1f).mul(m);
        return new Vec3(p.x, p.y, p.z);
    }

    private static void applyBone(Matrix4f m, PlayerPoseAnimation.Frame pose, int bone, float baseX, float baseY, float baseZ) {
        int off = bone * PlayerPoseAnimation.AXES;
        float rx = (float) Math.toRadians(pose.values[off]);
        float ry = (float) Math.toRadians(pose.values[off + 1]);
        float rz = (float) Math.toRadians(pose.values[off + 2]);
        float px = (baseX + pose.values[off + 3]) / 16f;
        float py = (baseY + pose.values[off + 4]) / 16f;
        float pz = (baseZ + pose.values[off + 5]) / 16f;
        m.translate(px, py, pz);
        // Minecraft ModelPart.translateAndRotate order: Z -> Y -> X
        if (rz != 0f) m.rotateZ(rz);
        if (ry != 0f) m.rotateY(ry);
        if (rx != 0f) m.rotateX(rx);
    }
}
