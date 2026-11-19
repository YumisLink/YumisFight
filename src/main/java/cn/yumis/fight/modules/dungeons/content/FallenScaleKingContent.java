package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.RitualBossEntity;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 50级坠鳞的君王：燃痕灰迹、落鳞避险、读碑告白。 */
final class FallenScaleKingContent extends RitualBossContent {
    private final UUID[] steles = new UUID[3];
    private final Set<UUID> readSteles = new HashSet<>();
    private final Set<String> readers = new HashSet<>();
    private String lastReader;
    private long lastBurnEndAt, landAt, groundedUntil;
    private boolean first35Done, rainPending;
    private Vec3 landing,burnOrigin,burnFacing;

    FallenScaleKingContent(RitualEncounterService service) { super(service); }

    @Override public void begin(RitualEncounterService.Run run) {
        var fire=service.entity(run,spawn(run,"ritual_campfire",1,true,run.origin.add(-3,0,0)));
        if(fire!=null)service.addSafeSpot(run,"bonfire","bonfire",fire.getX(),fire.getY(),fire.getZ(),2.2,Long.MAX_VALUE);
        for (int index = 0; index < 3; index++) {
            double angle = Math.PI * 2 * (index + 1) / 4;
            var at = run.origin.add(Math.cos(angle) * (run.radius - 3), 0, Math.sin(angle) * (run.radius - 3));
            steles[index] = spawn(run, "ritual_stele", 1, true, at);
        }
        service.hudLine(run, "steles", "战绩 0/3");
        service.hudLine(run, "hint", "读满三块碑；它俯冲伏地时，读碑人右键龙首告白");
    }

    @Override public void tick(RitualEncounterService.Run run, RitualBossEntity boss, long now) {
        if(landAt>0&&now>=landAt){
            landAt=0;
            if(run.casts.containsKey("falling_step")){
                if(boss.level().noCollision(boss,boss.getBoundingBox().move(landing.subtract(boss.position())))){
                    boss.setPos(landing.x,landing.y,landing.z);boss.setDeltaMovement(Vec3.ZERO);
                }
                groundedUntil=now+move(run,"falling_step").groundedMs();
            }
        }
        if (groundedUntil > now) return;
        if (rainPending && now >= lastBurnEndAt && !busy(run)) {
            rainPending = false;
            var burn=move(run,"burn_script");
            Vec3 side=new Vec3(-burnFacing.z,0,burnFacing.x).scale(burn.shape().spacing()*.5);
            for(int lane:new int[]{-1,1})for(int step=1;step<=5;step++){
                Vec3 ash=service.floorNear(run,burnOrigin.add(side.scale(lane)).add(burnFacing.scale(step*2)));
                if(ash!=null)service.addSafeSpot(run,"ash","ash/"+lane+"/"+step,ash.x,ash.y,ash.z,1.4,now+burn.ashSeconds()*1000L);
            }
            start(run,"scale_rain",run.origin,new Vec3(1,0,0),null);
            return;
        }
        var target = nearest(run, boss.position());
        if (target == null) return;
        if (busy(run)) return;
        var facing = direction(boss.position(), target.position());
        if (first35Done && !cooling(run, "falling_step", now)) {
            var victim = lastReader != null ? service.playerByUuid(lastReader) : null;
            if (victim == null || !fighters(run).contains(victim)) victim = target;
            lastReader = null;
            landing=victim.position();
            landAt=now+move(run,"falling_step").contactMs()[0];
            start(run,"falling_step",landing,direction(boss.position(),landing),null);
            return;
        }
        if (!cooling(run, "burn_script", now)) {
            var burn=move(run,"burn_script");
            burnOrigin=boss.position();burnFacing=facing;
            start(run,"burn_script",burnOrigin,burnFacing,null);
            lastBurnEndAt=now+burn.contactMs()[0]+burn.linger().ms();
            rainPending = true;
            return;
        }
        walk(run, boss, target.position(), 1.35);
    }

    @Override public void damaged(RitualEncounterService.Run run, ServerPlayer player, double fraction, Target target) {
        if (!first35Done && hp(run) <= .35) {
            first35Done = true;
            service.hudLine(run, "hint", "它盯上了读碑人：被点名者把落点引开，读碑人准备告白");
        }
    }

    @Override public boolean mechanismInteract(RitualEncounterService.Run run, ServerPlayer player, String kind, UUID id) {
        for (UUID stele : steles) {
            if (!id.equals(stele)) continue;
            String key = player.getStringUUID();
            readers.add(key);
            readSteles.add(id);
            lastReader = key;
            handlers.add(key);
            service.hudLine(run,"steles","战绩 "+readSteles.size()+"/3");
            String[] lines = {"第一段战绩：它并非坠亡，而是择地而落。", "第二段战绩：鳞落之处火线尽熄，灰下有余温。", "第三段战绩：最后一位目击者在碑前留了名。"};
            for (int index = 0; index < 3; index++) if (id.equals(steles[index])) say(player, lines[index]);
            return true;
        }
        return false;
    }

    @Override public boolean bossInteract(RitualEncounterService.Run run, ServerPlayer player, RitualBossEntity boss) {
        if (groundedUntil < System.currentTimeMillis()) return false;
        String key = player.getStringUUID();
        if (readSteles.size()<3||!readers.contains(key)) {
            say(player, "读过战绩碑的人才能告别，三块碑都需要有人读过。");
            return true;
        }
        handlers.add(key);
        say(player, "「我看见它落下来的。」");
        service.finishRun(run, "farewell");
        return true;
    }
}
