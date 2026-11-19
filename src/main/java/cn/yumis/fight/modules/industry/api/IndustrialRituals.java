package cn.yumis.fight.modules.industry.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Ritual altar encounters live in the dungeons module; the altar forwards confirmed player intents through this bootstrap wiring. */
public final class IndustrialRituals {
    /** Cross-module naming contract: ritual tokens are registered as <code>altar_token_&lt;bossId&gt;</code>. */
    public static final String TOKEN_PREFIX = "altar_token_";
    public interface Handler {
        /** A held ritual token was offered to the altar; the handler validates the boss, world unlock and operation. */
        void unlock(ServerPlayer player,ServerLevel level,BlockPos pos,ItemStack stack);
        /** Empty-hand side click: present the summon list with unlocked bosses, per-fight costs and the altar energy state. */
        void summonMenu(ServerPlayer player,ServerLevel level,BlockPos pos,long storedMana,boolean gridConnected,long gridAvailable);
    }
    /** Registry check only, so client prediction matches the server decision without module knowledge. */
    public static boolean token(ItemStack stack){
        return !stack.isEmpty()&&cn.yumis.fight.platform.ItemData.own(stack)&&cn.yumis.fight.platform.ItemData.id(stack).startsWith(TOKEN_PREFIX);
    }
    private static Handler handler;
    private IndustrialRituals(){}
    public static void install(Handler value){handler=value;}
    public static void clear(){handler=null;}
    public static boolean ready(){return handler!=null;}
    public static void unlock(ServerPlayer player,ServerLevel level,BlockPos pos,ItemStack stack){
        if(handler==null)throw new IllegalStateException("服务未就绪");
        handler.unlock(player,level,pos,stack);
    }
    public static boolean summonMenu(ServerPlayer player,ServerLevel level,BlockPos pos,long storedMana,boolean gridConnected,long gridAvailable){
        if(handler==null)return false;
        handler.summonMenu(player,level,pos,storedMana,gridConnected,gridAvailable);
        return true;
    }
}
