package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.RitualBossCatalog;
import cn.yumis.fight.modules.dungeons.api.RitualBossEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 55级千室告解：木构耐久、十三晶片与听者窗口。 */
final class ThousandfoldConfessionContent extends RitualBossContent {
    private final boolean[] shellSteps = new boolean[3];
    private final Set<String> shielded = new LinkedHashSet<>();
    private final Set<String> confessed = new LinkedHashSet<>();
    private UUID confessional, pullLine;
    private String marked;
    private int chipsPlanned, chipsSpawned, chipsBroken;
    private boolean openingPending;
    private long listenerWindowUntil,nextListenerAt,repairAt,pullUntil;

    ThousandfoldConfessionContent(RitualEncounterService service) { super(service); }

    @Override public void begin(RitualEncounterService.Run run) {
        var facing = direction(run.origin, run.origin.add(1, 0, 0));
        confessional = spawn(run, "ritual_confessional", 1, true, edge(run, facing));
        service.hudLine(run, "chips", "晶片 0/13");
        service.hudLine(run, "hint", "右键告解台备好陈述可抵消下一次落印；拆碎晶片削弱修复");
    }

    @Override public void tick(RitualEncounterService.Run run, RitualBossEntity boss, long now) {
        if(pullLine!=null&&now>=pullUntil){service.removeMechanism(run,pullLine);pullLine=null;}
        if(repairAt>0&&now>=repairAt){
            repairAt=0;
            if(run.casts.containsKey("litany"))service.repair(run,catalog(run).chip().repairPercent()/100*(13-chipsBroken));
        }
        openChips(run);
        if (chipsBroken >= 13) {
            if (listenerWindowUntil == 0&&now>=nextListenerAt) {
                boss.getNavigation().stop();
                listenerWindowUntil = now + 8000;
                boss.play("listener_rises", 2000);
                for (String id : new ArrayList<>(run.casts.keySet())) service.cancelMove(run, id);
                service.hudLine(run, "hint", "听者起身：入内右键告解，再右键亲口赦免");
                var listener = nearest(run, boss.position());
                if (listener != null) say(listener, "听者把帘子掀开了一条缝。");
            } else if (listenerWindowUntil>0&&now>=listenerWindowUntil) {
                listenerWindowUntil=0;nextListenerAt=now+8000;
                confessed.clear();
                burst(run, boss);
            }
            return;
        }
        if (listenerWindowUntil > 0) listenerWindowUntil = 0;
        if(openingPending&&!busy(run)){
            openingPending=false;
            start(run,"chamber_open",boss.position(),null,null);
            return;
        }
        var target = nearest(run, boss.position());
        if (target == null) return;
        if (busy(run)) return;
        var facing = direction(boss.position(), target.position());
        if (marked != null) {
            var victim = service.playerByUuid(marked);
            String mark=marked;marked=null;
            if (victim == null || !fighters(run).contains(victim)) { marked = null; return; }
            if(shielded.remove(mark)){
                service.hudLine(run,"marked","");
                say(victim,"预设陈述抵消了这一次落印。");
                handlers.add(mark);
                return;
            }
            start(run, "absolve", boss.position(), direction(boss.position(), victim.position()), victim.getUUID());
            pullLine = spawn(run, "ritual_pull_line", 60, false,
                    boss.position().add(victim.position()).scale(.5).add(0, .8, 0));
            pullUntil=now+move(run,"absolve").contactMs()[0];
            return;
        }
        if (!cooling(run, "read_guilt", now)) {
            var victim = any(run);
            if (victim == null) return;
            String key = victim.getStringUUID();
            marked = key;
            start(run,"read_guilt",boss.position(),facing,victim.getUUID());
            service.hudLine(run,"marked","落印者："+victim.getGameProfile().name());
            return;
        }
        if (!cooling(run, "litany", now)) {
            start(run, "litany", boss.position(), facing, null);
            repairAt=now+move(run,"litany").contactMs()[0];
            return;
        }
        walk(run, boss, target.position(), .75);
    }

    private void burst(RitualEncounterService.Run run, RitualBossEntity boss) {
        var base = move(run, "listener_rises");
        var miss = base.missDamage();
        if (miss == null) return;
        var synthesized = new RitualBossCatalog.Move(base.id(), base.animation(), base.trigger(), base.phase(),
                base.cooldownMs(), 1000, new long[]{1000}, base.target(),
                new RitualBossCatalog.Shape("circle", miss.radius()==null?12:miss.radius(), 0, 0, 0, 0, 0), 1,
                new RitualBossCatalog.Damage(miss.coefficient(), miss.type(), miss.element()),
                base.roundMs(), base.slowSeconds(), base.rootSeconds(), base.spawnClone(), base.extinguishLanterns(),
                base.healDownPercent(), base.healDownSeconds(), base.debtSlowPercent(), base.debtSlowStacks(), base.debtSlowSeconds(),
                base.freezeSeconds(), base.exposeBrokenWatchMs(), base.groundedMs(), base.ashSeconds(), base.safeSpots(),
                base.sitSeconds(), base.saveSeconds(), base.windowSeconds(), null, base.chantBoost(), base.steps(), base.group(),
                base.blocksBehindCover(), base.replayGroup(), base.spawnPullLine(), base.linger());
        service.startMove(run, synthesized, boss.position(), new Vec3(1, 0, 0), null);
    }

    @Override public void damaged(RitualEncounterService.Run run, ServerPlayer player, double fraction, Target target) {
        double health = hp(run);
        var shell=catalog(run).shell();
        int[] steps=shell.steps(), counts=shell.chipsPerStep();
        for (int index = 0; index < 3; index++) {
            if (shellSteps[index] || health > steps[index] / 100d) continue;
            shellSteps[index] = true;
            chipsPlanned+=counts[index];
            openingPending=true;
        }
    }

    private void openChips(RitualEncounterService.Run run) {
        while(chipsSpawned<chipsPlanned){
            double angle=Math.PI*2*(chipsSpawned+1)/13;
            var at=run.origin.add(Math.cos(angle)*(run.radius-2.5),0,Math.sin(angle)*(run.radius-2.5));
            double health=maxHealth(run)*catalog(run).chip().durabilityPercent()/100;
            if(spawn(run,"ritual_shard",health,false,at)==null)break;
            chipsSpawned++;
            service.hudLine(run,"chips","晶片 "+chipsBroken+"/13（已开放 "+chipsSpawned+"）");
        }
    }

    private double maxHealth(RitualEncounterService.Run run) {
        return run.bossTarget == null ? 1000 : run.bossTarget.health(catalog(run).level());
    }

    @Override public void death(RitualEncounterService.Run run, Target target) {
        // 木构见底转入晶片轮次，永不发布普通死亡。
    }

    @Override public void mechanismBroken(RitualEncounterService.Run run, String kind, UUID id, String breaker) {
        if (breaker != null && !breaker.isEmpty()) handlers.add(breaker);
        if (kind.equals("ritual_shard")) {
            chipsBroken++;
            service.hudLine(run, "chips", "晶片 " + chipsBroken + "/13");
        }
        if (id.equals(pullLine)) {pullLine=null;service.cancelMove(run,"absolve");}
    }

    @Override public boolean mechanismInteract(RitualEncounterService.Run run, ServerPlayer player, String kind, UUID id) {
        if (!id.equals(confessional)) return false;
        String key = player.getStringUUID();
        shielded.add(key);
        say(player, "告解台的预设陈述已备好：下一次落印会被抵消。");
        return true;
    }

    @Override public boolean bossInteract(RitualEncounterService.Run run, ServerPlayer player, RitualBossEntity boss) {
        if (listenerWindowUntil<=System.currentTimeMillis()||player.distanceToSqr(boss)>6.25) return false;
        String key = player.getStringUUID();
        handlers.add(key);
        if (!confessed.contains(key)) {
            confessed.add(key);
            say(player, "「我在第十三间门前，把没说完的话说完了。」");
            return true;
        }
        say(player, "「我赦免你，也请你放过这些房间。」");
        service.finishRun(run, "dismissal");
        return true;
    }
}
