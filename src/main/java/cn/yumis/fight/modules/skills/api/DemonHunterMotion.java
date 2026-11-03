package cn.yumis.fight.modules.skills.api;

import net.minecraft.server.level.ServerPlayer;

/** Owned movement lease; cosmetic visual packets cannot acquire it. */
public final class DemonHunterMotion {
    private static final String ACTIVE="ydnf_demon_motion_active",PREVIOUS="ydnf_demon_previous_gravity";
    private DemonHunterMotion(){}
    public static void begin(ServerPlayer p){var data=p.getPersistentData();if(!data.getBooleanOr(ACTIVE,false))data.putBoolean(PREVIOUS,p.isNoGravity());data.putBoolean(ACTIVE,true);p.setNoGravity(true);p.fallDistance=0;}
    public static void restore(ServerPlayer p){var data=p.getPersistentData();if(data.getBooleanOr(ACTIVE,false)){p.setNoGravity(data.getBooleanOr(PREVIOUS,false));data.remove(ACTIVE);data.remove(PREVIOUS);p.fallDistance=0;}}
}
