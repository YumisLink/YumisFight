package cn.yumis.fight.modules.skills.api;

/** Transient connection/life-scoped Oracle resources. Never a separate persistent business model. */
public final class OracleState {
    public int hermes,karma;
    public long hermesUntil,karmaUntil,nextInstruction,instructionUntil,instructionPausedLeft,weaponUntil,castingUntil,outOfCombatSince,hermesSurgeReadyAt,cycleResetReadyAt;
    public String instruction="",weapon="",cast="",nextForcedInstruction="";
    public boolean initialized,awakeningObserved,awakeningLearned,awakeningGrantPending;
}
