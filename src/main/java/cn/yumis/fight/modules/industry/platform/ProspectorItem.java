package cn.yumis.fight.modules.industry.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

public final class ProspectorItem extends Item {
    private final int tier;
    public ProspectorItem(int tier,Properties properties){super(properties);this.tier=tier;}
    public static int tier(ItemStack stack){return stack.getItem() instanceof ProspectorItem tool?tool.tier:0;}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,net.minecraft.world.item.context.UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer server){
            if(!player.isShiftKeyDown())try{IndustrialDeposits.get(server.level()).start(server,tier);}catch(IllegalArgumentException e){server.sendOverlayMessage(Component.literal(e.getMessage()));}
            else server.openMenu(new net.minecraft.world.MenuProvider(){
                public Component getDisplayName(){return Component.literal("区块探矿地图");}
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,net.minecraft.world.entity.player.Inventory inventory,Player p){return new ProspectorMenu(id,inventory,tier);}
            },buf->buf.writeInt(tier));
        }
        return InteractionResult.SUCCESS;
    }
}
