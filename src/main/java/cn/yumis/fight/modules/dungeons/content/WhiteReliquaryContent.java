package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.RitualBossEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** 40级白圣髑：遗咏读条、重结晶与共振台活捉。 */
final class WhiteReliquaryContent extends RitualBossContent {
    private double chant;
    private long lastChantTick, stopUntil;
    private boolean step70, step40, pendingResonance;
    private UUID resonance;
    private Vec3 resonancePoint;

    WhiteReliquaryContent(RitualEncounterService service) { super(service); }

    @Override public void begin(RitualEncounterService.Run run) {
        var facing = direction(run.origin, run.origin.add(1, 0, 0));
        var at = edge(run, facing).add(0, 0, -4);
        var ward = service.entity(run, spawn(run, "ritual_anti_magic", 1, true, at));
        if (ward != null) service.addSafeSpot(run, "anti_magic", "anti", ward.getX(), ward.getY(), ward.getZ(), 2.4, Long.MAX_VALUE);
        lastChantTick=System.currentTimeMillis();
        service.hudLine(run, "chant", "遗咏 0.0%");
        service.hudLine(run, "hint", "持续输出压读条；读满时躲进反魔力阵");
    }

    @Override public void tick(RitualEncounterService.Run run, RitualBossEntity boss, long now) {
        var config = catalog(run);
        if(stopUntil>now)return;
        if(resonance!=null){service.removeMechanism(run,resonance);resonance=null;}
        if(pendingResonance){
            pendingResonance=false;recrystallize(run,now);
            resonance=spawn(run,"ritual_resonance",1,true,resonancePoint);
            service.hudLine(run,"hint","半页震出：重结晶时右键共振台完成活捉");
            return;
        }
        if (now - lastChantTick >= 1000 && stopUntil < now) {
            lastChantTick = now;
            chant = Math.min(100, chant + config.chantPerSecond());
            service.hudLine(run, "chant", "遗咏 " + String.format("%.1f", chant) + "%");
        }
        if (chant >= 100 && !busy(run)) {
            var viewer = nearest(run, boss.position());
            start(run, "lasting_chant", boss.position(), viewer == null ? new Vec3(1, 0, 0) : direction(boss.position(), viewer.position()), null);
            chant = 0;
            service.hudLine(run, "chant", "遗咏 0.0%");
            return;
        }
        var target = nearest(run, boss.position());
        if (target == null) return;
        if (busy(run)) return;
        var facing = direction(boss.position(), target.position());
        if (!cooling(run, "compress", now)) {
            var farthest = farthest(run, boss.position());
            start(run,"compress",farthest==null?boss.position():farthest.position(),facing,null);
            return;
        }
        if (!cooling(run, "crystal_ring", now)) {
            start(run, "crystal_ring", boss.position(), facing, null);
            return;
        }
        walk(run, boss, target.position(), .8);
    }

    @Override public void damaged(RitualEncounterService.Run run, ServerPlayer player, double fraction, Target target) {
        var config = catalog(run);
        if (fraction > 0 && config.chantDamageRegression() != null) {
            chant = Math.max(0, chant - config.chantDamageRegression() * fraction * 100);
            service.hudLine(run, "chant", "遗咏 " + String.format("%.1f", chant) + "%");
        }
        double health = hp(run);
        if (!step70 && health <= .70) {
            step70 = true;
            recrystallize(run, now());
        }
        if (!step40 && health <= .40) {
            step40 = true;
            pendingResonance = true;
            var facing = direction(run.origin, player.position());
            resonancePoint=run.origin.add(facing.x*4,0,facing.z*4);
        }
    }

    private long now() { return System.currentTimeMillis(); }

    private void recrystallize(RitualEncounterService.Run run, long now) {
        var move=move(run,"recrystallize");
        stopUntil = now + move.durationMs();
        chant = Math.min(100,chant+move.chantBoost());
        var entity = service.entity(run, run.bossEntity);
        if (entity instanceof RitualBossEntity boss) {
            boss.play("recrystallize", 4000);
            for (String id : new java.util.ArrayList<>(run.casts.keySet())) service.cancelMove(run, id);
        }
    }

    @Override public boolean mechanismInteract(RitualEncounterService.Run run, ServerPlayer player, String kind, UUID id) {
        if (!id.equals(resonance)||System.currentTimeMillis()>=stopUntil) return false;
        handlers.add(player.getStringUUID());
        service.finishRun(run, "surrender");
        return true;
    }
}
