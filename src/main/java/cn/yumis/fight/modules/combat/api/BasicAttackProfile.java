package cn.yumis.fight.modules.combat.api;

/** Resolved attack timing supplied by content; combat validates cycles without interpreting skill definitions. */
public record BasicAttackProfile(double interval,int slashes) {
    public BasicAttackProfile {if(!Double.isFinite(interval)||interval<=0||slashes<1)throw new IllegalArgumentException("Invalid attack profile");}
}
