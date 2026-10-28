package cn.yumis.fight.modules.professions.application;
import net.minecraft.world.level.block.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import java.util.*;
public final class ProfessionEvents {
    private ProfessionEvents(){}
    private static boolean mature(net.minecraft.world.level.block.state.BlockState s){
        return s.getBlock() instanceof CropBlock crop&&crop.isMaxAge(s)||s.is(Blocks.NETHER_WART)&&s.getValue(NetherWartBlock.AGE)==3||s.is(Blocks.COCOA)&&s.getValue(CocoaBlock.AGE)==2||s.is(Blocks.SWEET_BERRY_BUSH)&&s.getValue(SweetBerryBushBlock.AGE)==3;
    }
    public static void register(java.util.function.BooleanSupplier ready,java.util.function.Supplier<ProfessionService> service){
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e)->{
            if(ready.getAsBoolean()&&!e.isCanceled()&&e.getBreaker() instanceof ServerPlayer p&&mature(e.getState())&&!e.getDrops().isEmpty())service.get().award(p.getUUID(),"farmer",10,"harvest/"+UUID.randomUUID(),false);
        }
        );
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent e)->{
            if(ready.getAsBoolean()&&!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p&&!e.getDrops().isEmpty())service.get().award(p.getUUID(),"fisherman",20,"fishing/"+e.getHookEntity().getUUID(),false);
        }
        );
    }
}
