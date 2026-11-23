package cn.yumis.fight.modules.skills.client;

/**
 * Carrier the render-state mixin implements so bone poses computed at extraction time
 * reach every humanoid model pass (body, armor, cape) during the same frame.
 */
public interface PlayerPoseCarrier {
    PlayerPoseAnimation.Frame ydnf$skillPose();

    void ydnf$skillPose(PlayerPoseAnimation.Frame frame);
}
