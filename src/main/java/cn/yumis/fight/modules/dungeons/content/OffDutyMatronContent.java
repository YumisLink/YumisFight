package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.RitualBossEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 35级歇工的圣母：发条倒带与退役取钱。 */
final class OffDutyMatronContent extends RitualBossContent {
    private final UUID[] springs = new UUID[7];
    private final boolean[] springAlive = new boolean[7];
    private int lit, choresIndex;
    private long nextLightAt, severanceUntil, pullUntil;
    private boolean halfSeen, replayBlocked;
    private String lastChores = "chores_broom";
    private Vec3 lastOrigin,lastFacing;
    private final Set<UUID> eyes = new LinkedHashSet<>();
    private UUID pullLine;

    OffDutyMatronContent(RitualEncounterService service) { super(service); }

    @Override public void begin(RitualEncounterService.Run run) {
        for (int index = 0; index < 7; index++) {
            double angle = Math.PI * 2 * index / 7;
            var at = run.origin.add(Math.cos(angle) * (run.radius - 3), 0, Math.sin(angle) * (run.radius - 3));
            springs[index] = spawn(run, "ritual_spring", 60, false, at);
            springAlive[index] = springs[index]!=null;
        }
        nextLightAt=System.currentTimeMillis()+catalog(run).windIntervalMs();
        service.hudLine(run, "springs", "发条 0/7");
        service.hudLine(run, "hint", "拆断发条可阻止倒带；半血后拆发条令她垂首");
    }

    @Override public void tick(RitualEncounterService.Run run, RitualBossEntity boss, long now) {
        if(pullLine!=null&&now>=pullUntil){service.removeMechanism(run,pullLine);pullLine=null;}
        if (severanceUntil > now) return;
        if(severanceUntil!=0){
            for(var eye:eyes)service.removeMechanism(run,eye);
            eyes.clear();severanceUntil=0;
        }
        int slots=catalog(run).windSlots();
        if (now >= nextLightAt && (lit<slots-1||!busy(run))) {
            nextLightAt = now + catalog(run).windIntervalMs();
            lit++;
            service.hudLine(run, "springs", "发条 " + Math.min(slots,lit) + "/" + slots);
            if (lit >= slots) {
                lit = 0;
                boolean intact = true;
                for (boolean alive : springAlive) if (!alive) { intact = false; break; }
                if (intact && !replayBlocked && lastOrigin!=null) start(run,lastChores,lastOrigin,lastFacing,null);
                replayBlocked=false;
            }
        }
        var target = nearest(run, boss.position());
        if (target == null) return;
        if (busy(run)) return;
        var facing = direction(boss.position(), target.position());
        String[] rotation = {"chores_broom", "chores_iron", "chores_ladle"};
        if (!cooling(run, rotation[choresIndex % 3], now)) {
            String chosen = rotation[choresIndex % 3];
            choresIndex++;
            lastChores = chosen;
            lastOrigin="chores_ladle".equals(chosen)?target.position():boss.position();
            lastFacing=facing;
            start(run,chosen,lastOrigin,lastFacing,null);
            return;
        }
        if (halfSeen && !cooling(run, "shift_handover", now)) {
            start(run, "shift_handover", target.position(), facing, target.getUUID());
            pullLine = spawn(run, "ritual_pull_line", 60, false,
                    boss.position().add(target.position()).scale(.5).add(0, .8, 0));
            pullUntil=now+move(run,"shift_handover").durationMs();
            return;
        }
        if (!cooling(run, "lament", now)) {
            start(run, "lament", boss.position(), facing, null);
            return;
        }
        walk(run, boss, target.position(), .9);
    }

    @Override public void damaged(RitualEncounterService.Run run, ServerPlayer player, double fraction, Target target) {
        if (!halfSeen && hp(run) <= .5) {
            halfSeen = true;
            service.hudLine(run, "hint", "半血：拆断任意发条，阻止倒带");
        }
    }

    @Override public void mechanismBroken(RitualEncounterService.Run run, String kind, UUID id, String breaker) {
        if (breaker != null && !breaker.isEmpty()) handlers.add(breaker);
        if(eyes.remove(id)){
            if(eyes.isEmpty())service.hudLine(run,"hint","两枚眼钱已松动，右键取下");
            return;
        }
        for (int index = 0; index < 7; index++) if (id.equals(springs[index])) {
            springAlive[index] = false;
            replayBlocked=true;
            if (halfSeen && severanceUntil == 0) {
                severanceUntil = System.currentTimeMillis() + 5000;
                var entity = service.entity(run, run.bossEntity);
                if (entity instanceof RitualBossEntity boss) {
                    for(String cast:new ArrayList<>(run.casts.keySet()))service.cancelMove(run,cast);
                    boss.getNavigation().stop();
                    boss.play("severance",5000);
                    Vec3 front=Vec3.directionFromRotation(0,boss.getYRot());
                    Vec3 side=new Vec3(-front.z,0,front.x);
                    Vec3 face=boss.position().add(front.scale(1.15)).add(0,1.25,0);
                    for(int edge:new int[]{-1,1}){
                        UUID eye=spawn(run,"ritual_eye_coin",40,false,face.add(side.scale(edge*.48)));
                        if(eye!=null)eyes.add(eye);
                    }
                }
                service.hudLine(run, "hint", "她垂首了：打中两枚眼钱，随后右键取下");
            }
            return;
        }
        if (id.equals(pullLine)) {pullLine=null;service.cancelMove(run,"shift_handover");}
    }

    @Override public boolean bossInteract(RitualEncounterService.Run run, ServerPlayer player, RitualBossEntity boss) {
        if (severanceUntil<=System.currentTimeMillis()) return false;
        if (!eyes.isEmpty()) {
            say(player, "先打开两枚眼钱。");
            return true;
        }
        handlers.add(player.getStringUUID());
        severanceUntil = 0;
        service.finishRun(run, "severance");
        return true;
    }
}
