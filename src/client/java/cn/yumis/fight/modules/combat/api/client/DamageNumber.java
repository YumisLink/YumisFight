package cn.yumis.fight.modules.combat.api.client;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Client-only hit presentation. Critical is the original roll, never inferred from the amount. */
public record DamageNumber(double amount,long expires,int color,Vec3 position,String dimension,UUID target,boolean critical) {
    public static final long LIFETIME_MILLIS=1100;
    /** Scene-owned hits share the ordinary combat font, critical styling and stacking rules. */
    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics,java.util.List<DamageNumber> numbers){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.level!=null)cn.yumis.fight.modules.combat.client.DamageNumbers.render(graphics,mc,
            cn.yumis.fight.modules.presentation.api.client.ClientUi.now(),mc.level.dimension().identifier().toString(),numbers);
    }
}
