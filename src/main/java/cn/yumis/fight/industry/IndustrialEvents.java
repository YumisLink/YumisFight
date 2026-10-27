package cn.yumis.fight.industry;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.*;

final class IndustrialEvents {
    static void register(){
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e)->cn.yumis.fight.server.IndustrialVerification.register(e));
        NeoForge.EVENT_BUS.addListener((BlockEvent.NeighborNotifyEvent e)->{if(e.getLevel() instanceof ServerLevel l)IndustrialWorld.get(l).changed(l,e.getPos(),true);});
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e)->{if(e.getLevel() instanceof ServerLevel l&&!e.isCanceled())IndustrialWorld.get(l).changed(l,e.getPos(),true);});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent e)->{if(e.getLevel() instanceof ServerLevel l&&!e.isCanceled())IndustrialWorld.get(l).changed(l,e.getPos(),true);});
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e)->{if(e.getLevel() instanceof ServerLevel l)IndustrialWorld.get(l).chunkChanged(l);});
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload e)->{if(e.getLevel() instanceof ServerLevel l)IndustrialWorld.get(l).chunkChanged(l);});
    }
}
