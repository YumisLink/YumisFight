package cn.yumis.fight.game;

import cn.yumis.fight.FightMod;
import net.minecraft.world.entity.player.Player;

/** Keeps the dimensional bag and a transaction view of Curios-equipped gear. */
public final class MenuHooks {
    private MenuHooks(){}
    public static ExtraInventory extra(Player player){return player.getData(FightMod.EXTRA);}
}
