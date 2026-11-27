package cn.yumis.fight.modules.skills.api;

/** Connection/life-scoped Witch (魔道学者) runtime state synced via CareerState. */
public final class WitchState {
    public long revision;
    public long candyBuffUntil;
    public double candyDamageMut;
    public double candyCastSpeed;
    public double candyMoveSpeed;
    public boolean broomGliding;
    public String activeMachine = "";

    public boolean candyActive(long now) {
        return candyBuffUntil > now;
    }

    public WitchState copy() {
        var next = new WitchState();
        next.revision = revision;
        next.candyBuffUntil = candyBuffUntil;
        next.candyDamageMut = candyDamageMut;
        next.candyCastSpeed = candyCastSpeed;
        next.candyMoveSpeed = candyMoveSpeed;
        next.broomGliding = broomGliding;
        next.activeMachine = activeMachine == null ? "" : activeMachine;
        return next;
    }
}
