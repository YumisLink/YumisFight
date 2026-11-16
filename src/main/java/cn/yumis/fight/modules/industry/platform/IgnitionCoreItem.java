package cn.yumis.fight.modules.industry.platform;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/** Iris discovers these optional held-item light methods without a hard dependency. */
public final class IgnitionCoreItem extends Item {
    public static final int LIGHT_LEVEL=15;
    private static final Vector3f LIGHT_COLOR=new Vector3f(1f,.78f,.46f);
    public IgnitionCoreItem(Properties properties){super(properties);}
    public int getLightEmission(Player player,ItemStack stack){return LIGHT_LEVEL;}
    public Vector3f getLightColor(Player player,ItemStack stack){return LIGHT_COLOR;}
}
