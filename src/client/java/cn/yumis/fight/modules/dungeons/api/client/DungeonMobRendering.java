package cn.yumis.fight.modules.dungeons.api.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.AbyssParty;
import cn.yumis.fight.modules.dungeons.client.AbyssFighterRenderer;
import cn.yumis.fight.modules.dungeons.client.AbyssPillarRenderer;
import cn.yumis.fight.modules.dungeons.client.GoblinRenderer;
import cn.yumis.fight.modules.dungeons.client.DragonRenderer;
import net.minecraft.client.renderer.entity.*;
import java.util.List;

/** Existing goblin/dragon models may also present scene-owned subclasses. */
public final class DungeonMobRendering {
    private DungeonMobRendering(){}
    @SuppressWarnings("unchecked")
    public static <T extends DungeonEnemy> EntityRenderer<T,?> basic(EntityRendererProvider.Context context,String kind){
        AbyssParty.Enemy abyss=AbyssParty.Enemy.find(kind);
        if(abyss!=null)return (EntityRenderer<T,?>)(abyss==AbyssParty.Enemy.PILLAR?new AbyssPillarRenderer(context):new AbyssFighterRenderer(context,abyss));
        EntityRenderer<?,?> renderer=(kind.startsWith("goblin")||"treasure_golden_goblin".equals(kind))?new GoblinRenderer(context):
            new DragonRenderer(context,Math.max(0,List.of("dragonman","blue_dragonman","wyvern","blue_wyvern","lucas").indexOf(kind)));
        return (EntityRenderer<T,?>)renderer;
    }
}
