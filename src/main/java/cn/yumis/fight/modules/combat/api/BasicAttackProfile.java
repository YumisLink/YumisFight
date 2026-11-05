package cn.yumis.fight.modules.combat.api;

/** Resolved attack timing supplied by content; combat validates cycles without interpreting skill definitions. */
public record BasicAttackProfile(double interval,int slashes,double damageScale,double contactOffset) {
    /** contactOffset is a fraction of the complete cycle, added to each half-cycle strike. */
    public BasicAttackProfile {if(!Double.isFinite(interval)||interval<=0||slashes<1||!Double.isFinite(damageScale)||damageScale<0||!Double.isFinite(contactOffset)||contactOffset<0||contactOffset>=.5)throw new IllegalArgumentException("Invalid attack profile");}
}
