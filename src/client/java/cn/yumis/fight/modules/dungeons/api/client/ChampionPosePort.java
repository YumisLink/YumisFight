package cn.yumis.fight.modules.dungeons.api.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Pose port the skills module implements so champion bodies can ride the player skill animation pipeline. */
public final class ChampionPosePort {
    public interface Source {
        /** True when a pose was applied and the renderer should skip its generic casting stance. */
        boolean apply(DungeonEnemy enemy, EntityRenderState state, long now);
    }

    private static volatile Source source;

    private ChampionPosePort() {}

    public static void install(Source installed) {
        if (source != null) throw new IllegalStateException("Champion pose source already installed");
        source = installed;
    }

    public static boolean apply(DungeonEnemy enemy, EntityRenderState state, long now) {
        Source installed = source;
        return installed != null && installed.apply(enemy, state, now);
    }
}
