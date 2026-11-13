package cn.yumis.fight.modules.mapgeneration.client;

import cn.yumis.fight.modules.mapgeneration.api.WorldDecorations;
import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import java.util.List;

public final class WorldDecorationClient {
    private WorldDecorationClient(){}
    public static void register(IEventBus bus){
        bus.addListener((EntityRenderersEvent.RegisterRenderers event)->event.registerBlockEntityRenderer(WorldDecorations.FLOATING_SHARD_ENTITY.get(),FloatingShardRenderer::new));
        bus.addListener((RegisterColorHandlersEvent.BlockTintSources event)->WorldDecorations.leafColors().forEach((block,color)->event.register(List.of(BlockTintSources.constant(color)),block)));
    }
}
