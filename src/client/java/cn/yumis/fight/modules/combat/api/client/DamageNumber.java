package cn.yumis.fight.modules.combat.api.client;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Client-only hit presentation. Critical is the original roll, never inferred from the amount. */
public record DamageNumber(double amount,long expires,int color,Vec3 position,String dimension,UUID target,boolean critical) {
    public static final long LIFETIME_MILLIS=1100;
}
