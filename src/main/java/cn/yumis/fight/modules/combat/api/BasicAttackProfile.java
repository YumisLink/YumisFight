package cn.yumis.fight.modules.combat.api;

/** Resolved attack timing supplied by content; combat validates cycles without interpreting skill definitions. */
public record BasicAttackProfile(double interval,int slashes,double damageScale,double eyeMut) {
    public BasicAttackProfile {if(!Double.isFinite(interval)||interval<=0||slashes<1||!Double.isFinite(damageScale)||damageScale<0||!Double.isFinite(eyeMut)||eyeMut<0)throw new IllegalArgumentException("Invalid attack profile");}
}
