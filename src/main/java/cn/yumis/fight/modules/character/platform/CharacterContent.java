package cn.yumis.fight.modules.character.platform;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
public final class CharacterContent {
    private CharacterContent(){}
    public static void register(){for(String id:java.util.List.of(CharacterItems.PROFESSION_RESET,CharacterItems.CAREER_RESET))ItemRegistry.ALL.put(id,ItemRegistry.ITEMS.registerItem(id,p->new ResetTicket(p,id.equals(CharacterItems.PROFESSION_RESET))));}
    private static final class ResetTicket extends Item {
        private final boolean base;
        ResetTicket(Properties properties,boolean base){super(properties);this.base=base;}
        @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            if(player instanceof ServerPlayer server)CharacterActions.reset(server,base);
            return InteractionResult.SUCCESS;
        }
    }
}
