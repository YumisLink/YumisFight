package cn.yumis.fight.modules.combat.client;

import cn.yumis.fight.modules.combat.api.Life;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Connection-owned presentation of the most recently damaged enemy, including unloaded targets. */
public final class TargetHudFocus {
    private static final long RETAIN_MILLIS=15000,DEATH_RETAIN_MILLIS=3000;
    private static final double RELEASE_DISTANCE_SQUARED=64*64;
    private UUID target;
    private long generation,lastDamageAt,deathAt=-1;
    private String name="";
    private Vec3 position=Vec3.ZERO;

    public UUID target(){return target;}
    public String name(){return name;}
    public boolean matches(Life life){return target!=null&&target.equals(life.target)&&generation==life.generation;}
    public void damage(Life life,String name,Vec3 position,long now){
        if(!matches(life))deathAt=-1;
        target=life.target;generation=life.generation;lastDamageAt=now;
        if(name!=null)this.name=name;if(position!=null)this.position=position;
    }
    public void update(Life life,String name,Vec3 position,Vec3 observer,long now,boolean boss){
        if(!matches(life)){clear();return;}
        if(name!=null)this.name=name;if(position!=null)this.position=position;
        if(life.dead){
            if(deathAt<0)deathAt=now;
            if(now-deathAt>=DEATH_RETAIN_MILLIS)clear();
            return;
        }
        deathAt=-1;
        boolean expired=now-lastDamageAt>=RETAIN_MILLIS,distant=observer.distanceToSqr(this.position)>RELEASE_DISTANCE_SQUARED;
        if(boss?expired&&distant:expired||distant)clear();
    }
    public void clear(){target=null;generation=lastDamageAt=0;deathAt=-1;name="";position=Vec3.ZERO;}
}
