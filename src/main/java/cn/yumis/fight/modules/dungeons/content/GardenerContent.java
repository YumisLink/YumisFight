package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.RitualBossEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** 30级无冠园丁：灯亮催花、诱剪接芽。 */
final class GardenerContent extends RitualBossContent {
    private int lanterns;
    private boolean halfSeen, baited;
    private UUID fakeBranch, knot, clone, sprout;
    private long pruneOpenAt, pruneOpenUntil, rootAt, rootsUntil;
    private Vec3 rootOrigin, rootFacing;
    private String sproutCarrier="";

    GardenerContent(RitualEncounterService service) { super(service); }

    @Override public void begin(RitualEncounterService.Run run) {
        var at = run.origin.add(2.5, 0, 0);
        fakeBranch = spawn(run, "ritual_fake_branch", 60, true, at);
        service.hudLine(run, "lanterns", "角灯 0/3");
        service.hudLine(run, "hint", "与假枝交互放饵，半血后诱剪低头时接上活芽");
    }

    @Override public void tick(RitualEncounterService.Run run, RitualBossEntity boss, long now) {
        if (pruneOpenAt > 0 && now >= pruneOpenAt) {
            pruneOpenAt = 0;
            if(run.casts.containsKey("prune")){
                pruneOpenUntil = now + 4000;
                baited = false;
                boss.play("prune_open", 4000);
            }
        }
        if(rootsUntil>0&&now>=rootsUntil){
            service.removeMechanism(run,knot);service.removeMechanism(run,clone);
            knot=clone=null;rootsUntil=0;
        }
        if(rootAt>0&&now>=rootAt){
            rootAt=0;
            if(run.casts.containsKey("layer_root")){
                knot=spawn(run,"ritual_root_knot",120,false,rootOrigin);
                clone=spawn(run,"ritual_hedge",300,false,edge(run,rootFacing));
            }
        }
        if (pruneOpenUntil > now) return;
        var target = nearest(run, boss.position());
        if (target == null) { boss.getNavigation().stop(); return; }
        if (busy(run)) return;
        var facing = direction(boss.position(), target.position());
        if (lanterns >= 3) {
            start(run, "force_bloom", boss.position(), facing, null);
            lanterns = 0;
            service.hudLine(run, "lanterns", "角灯 0/3");
            return;
        }
        var branch=baited?service.entity(run,fakeBranch):null;
        if (!cooling(run, "prune", now) && (branch!=null||target.distanceToSqr(boss)<=25)) {
            if (branch!=null) {
                double reach=move(run,"prune").shape().range();
                if(boss.distanceToSqr(branch)>reach*reach){walk(run,boss,branch.position(),1);return;}
                pruneOpenAt = now + move(run, "prune").contactMs()[0];
                facing=direction(boss.position(),branch.position());
            }
            start(run, "prune", boss.position(), facing, null);
            return;
        }
        if (!cooling(run, "spring_patrol", now)) {
            start(run, "spring_patrol", boss.position(), facing, null);
            walk(run,boss,boss.position().add(facing.scale(move(run,"spring_patrol").shape().length())),1.8);
            return;
        }
        if (!cooling(run, "layer_root", now)) {
            rootOrigin=target.position();rootFacing=facing;
            start(run, "layer_root", rootOrigin, rootFacing, target.getUUID());
            rootAt=now+move(run,"layer_root").contactMs()[0];
            rootsUntil=now+move(run,"layer_root").durationMs();
            return;
        }
        walk(run, boss, target.position(), 1);
    }

    @Override public void damaged(RitualEncounterService.Run run, ServerPlayer player, double fraction, Target target) {
        if (!halfSeen && hp(run) <= .5) {
            halfSeen = true;
            var facing = direction(run.origin, player.position());
            sprout = spawn(run, "ritual_sprout", 60, true, edge(run, facing));
            service.hudLine(run, "hint", "活芽已破土：诱剪低头时把场边活芽接上接口");
        }
    }

    @Override public void mechanismBroken(RitualEncounterService.Run run, String kind, UUID id, String breaker) {
        if (breaker != null && !breaker.isEmpty()) handlers.add(breaker);
        if (id.equals(knot)) {
            knot = null;
            service.cancelMove(run,"layer_root");
            service.removeMechanism(run,clone);
            clone=null;
        } else if (id.equals(clone)) {
            clone = null;
            lanterns = Math.min(3, lanterns + 1);
            service.hudLine(run, "lanterns", "角灯 " + lanterns + "/3");
        }
    }

    @Override public boolean mechanismInteract(RitualEncounterService.Run run, ServerPlayer player, String kind, UUID id) {
        if (id.equals(fakeBranch) && !baited) {
            baited = true;
            handlers.add(player.getStringUUID());
            say(player, "诱饵已架好，等它下一次闭剪。");
            return true;
        }
        if (id.equals(sprout)) {
            sproutCarrier=player.getStringUUID();
            say(player,"已带上活芽。园丁低头时右键它接枝。");
            handlers.add(player.getStringUUID());
            return true;
        }
        return false;
    }

    @Override public boolean bossInteract(RitualEncounterService.Run run,ServerPlayer player,RitualBossEntity boss) {
        if(pruneOpenUntil<=System.currentTimeMillis()||!sproutCarrier.equals(player.getStringUUID()))return false;
        handlers.add(player.getStringUUID());
        service.finishRun(run,"graft");
        return true;
    }
}
