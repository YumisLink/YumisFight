package cn.yumis.fight.modules.npc.api.client;

import cn.yumis.fight.modules.npc.api.NpcEntity;
import cn.yumis.fight.modules.npc.client.NpcRenderer;
import net.minecraft.client.renderer.entity.*;

/** Public humanoid presentation for NPC-derived world actors. */
public final class NpcRendering {
    private NpcRendering(){}
    public static <T extends NpcEntity> EntityRenderer<T,?> human(EntityRendererProvider.Context context){return new NpcRenderer<T>(context);}
}
