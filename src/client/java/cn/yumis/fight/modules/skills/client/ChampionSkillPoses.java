package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.client.ChampionPosePort;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Champion casts reuse the player skill pose pipeline; avatar-only choreography stays with players. */
public final class ChampionSkillPoses {
    private ChampionSkillPoses() {
    }

    public static void install() {
        ChampionPosePort.install(ChampionSkillPoses::pose);
    }

    private static boolean pose(DungeonEnemy enemy, EntityRenderState raw, long now) {
        var tracks = Effects.VFX.tracksFor(enemy.getUUID());
        if (tracks.isEmpty() || !(raw instanceof HumanoidRenderState state)) return false;
        PlayerPoseAnimation.Frame frame = null;
        for (var track : tracks) {
            float before = state.attackTime;
            SkillVisualRegistry.handler(track.start.skill()).animate(track, enemy, raw, now);
            if (state.attackTime == before)
                state.attackTime = (float) Math.clamp((now - track.start.startedAt()) / 600.0, 0, 1);
            PlayerPoseAnimation.Frame candidate = PlayerPoseChannel.pose(track, now);
            if (candidate != null) frame = candidate;
        }
        if (raw instanceof PlayerPoseCarrier carrier) carrier.ydnf$skillPose(frame);
        return true;
    }
}
