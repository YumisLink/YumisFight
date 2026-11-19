package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.RitualBossEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 45级催账人：停表取据、空秤盘清账。 */
final class DebtCollectorContent extends RitualBossContent {
    private final Map<String, Double> stash = new HashMap<>();
    private final boolean[] shellSteps = new boolean[3];
    private int debts, panSeconds;
    private boolean kneel;
    private long nextStopClockAt, fightStartAt, watchAt, watchUntil;
    private UUID watch, pan;

    DebtCollectorContent(RitualEncounterService service) { super(service); }

    @Override public void begin(RitualEncounterService.Run run) {
        fightStartAt = System.currentTimeMillis();
        nextStopClockAt=fightStartAt+move(run,"stop_clock").roundMs();
        var facing = direction(run.origin, run.origin.add(0, 0, 1));
        pan = spawn(run, "ritual_empty_pan", 1, true, edge(run, facing));
        service.hudLine(run, "pan", "已还 0/6 秒");
        service.hudLine(run, "hint", "停表后打裂表取借时，右键空秤盘投进去");
    }

    @Override public void tick(RitualEncounterService.Run run, RitualBossEntity boss, long now) {
        var target = run.bossTarget;
        if (target != null && !kneel && target.encounterLives == 1 && target.damageImmune) {
            kneel = true;
            for (String id : new ArrayList<>(run.casts.keySet())) service.cancelMove(run, id);
            service.hudLine(run, "hint", "账壳打空：停表窗口里取出剩余欠据，凑满六秒清账");
        }
        if(watch!=null&&now>=watchUntil){service.removeMechanism(run,watch);watch=null;}
        if(watchAt>0&&now>=watchAt){
            watchAt=0;
            if(debts>0&&now<watchUntil&&run.casts.containsKey("stop_clock")){
                var at=boss.position().add(direction(run.origin,boss.position()).scale(2.5));
                watch=spawn(run,"ritual_broken_watch",90,false,at);
            }
        }
        if (now >= nextStopClockAt && !busy(run)) {
            var stop=move(run,"stop_clock");
            nextStopClockAt = now + stop.roundMs();
            var viewer = nearest(run, boss.position());
            start(run, "stop_clock", boss.position(), viewer == null ? new Vec3(1, 0, 0) : direction(boss.position(), viewer.position()), null);
            watchAt = now + stop.contactMs()[0];
            watchUntil = watchAt + stop.exposeBrokenWatchMs();
            return;
        }
        if (kneel) return;
        if (busy(run)) return;
        var fighter = nearest(run, boss.position());
        if (fighter == null) return;
        var facing = direction(boss.position(), fighter.position());
        if (!cooling(run, "collect_interest", now)&&fighter.distanceToSqr(boss)<=25) {
            start(run, "collect_interest", boss.position(), facing, null);
            walk(run, boss, fighter.position(), 1.05);
            return;
        }
        if (fightStartAt > 0 && now - fightStartAt >= 90000 && !cooling(run, "bad_debt", now)) {
            start(run, "bad_debt", fighter.position(), facing, fighter.getUUID());
            return;
        }
        walk(run, boss, fighter.position(), 1);
    }

    @Override public void damaged(RitualEncounterService.Run run, ServerPlayer player, double fraction, Target target) {
        double health = hp(run);
        int[] steps = {75, 50, 25};
        for (int index = 0; index < 3; index++)
            if (!shellSteps[index] && health <= steps[index] / 100d) {
                shellSteps[index] = true;
                debts++;
                service.hudLine(run, "debts", "欠据 " + debts + "/3");
            }
    }

    @Override public void death(RitualEncounterService.Run run, Target target) {
        // The shell never publishes a death receipt: the account shell kneels, it does not die.
    }

    @Override public void mechanismBroken(RitualEncounterService.Run run, String kind, UUID id, String breaker) {
        if (!id.equals(watch)) return;
        watch = null;
        if (breaker == null || breaker.isEmpty() || debts<=0 || System.currentTimeMillis()>=watchUntil) return;
        handlers.add(breaker);
        debts--;
        double held = stash.getOrDefault(breaker, 0d) + 2;
        stash.put(breaker, held);
        service.hudLine(run, "debts", "欠据 " + debts + "/3");
        var player = service.playerByUuid(breaker);
        if (player != null) say(player, "借到 2 秒：右键空秤盘投进去。");
    }

    @Override public boolean mechanismInteract(RitualEncounterService.Run run, ServerPlayer player, String kind, UUID id) {
        if (!id.equals(pan)) return false;
        String key = player.getStringUUID();
        double held = stash.getOrDefault(key, 0d);
        if (held <= 0) {
            say(player, "先在停表窗口打裂表，拿到借时再来。");
            return true;
        }
        stash.remove(key);
        panSeconds += (int) held;
        handlers.add(key);
        service.clearDebt(player);
        service.hudLine(run, "pan", "已还 " + Math.min(6, panSeconds) + "/6 秒");
        if (panSeconds >= 6) {
            var entity = service.entity(run, run.bossEntity);
            if (entity instanceof RitualBossEntity boss) boss.play("settle_account", 2000);
            service.finishRun(run, "settle_account");
        } else {
            say(player, "已投 " + held + " 秒，还差 " + (6 - panSeconds) + " 秒。");
        }
        return true;
    }
}
