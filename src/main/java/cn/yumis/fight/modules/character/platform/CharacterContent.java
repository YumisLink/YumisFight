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
    public static void register(){
        ticket(CharacterItems.CAREER_UNLOCK,CharacterActions.Ticket.CAREER_UNLOCK);ticket(CharacterItems.AWAKENING,CharacterActions.Ticket.AWAKENING);
        ticket(CharacterItems.PROFILE_EXPANSION,CharacterActions.Ticket.PROFILE_EXPANSION);
        ticket(CharacterItems.LEVEL_ONE,CharacterActions.Ticket.LEVEL_ONE);ticket(CharacterItems.LEVEL_TEN,CharacterActions.Ticket.LEVEL_TEN);ticket(CharacterItems.LEVEL_MAX,CharacterActions.Ticket.LEVEL_MAX);
    }
    private static void ticket(String id,CharacterActions.Ticket ticket){ItemRegistry.ALL.put(id,ItemRegistry.ITEMS.registerItem(id,properties->new Item(properties){
        @Override public InteractionResult use(Level level,Player player,InteractionHand hand){if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(player instanceof ServerPlayer server)CharacterActions.use(server,ticket);return InteractionResult.SUCCESS;}
    }));}
}
