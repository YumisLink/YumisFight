package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.arkguard.api.ArkTraits;
import cn.yumis.fight.modules.arkguard.api.ArkWar;
import cn.yumis.fight.modules.combat.api.LifeRules;

import java.util.ArrayList;
import java.util.List;

import static cn.yumis.fight.modules.arkguard.application.ArkBattle.Actor;
import static cn.yumis.fight.modules.arkguard.api.ArkTraits.SANCTUARY;

final class ArkHealing {
    static final double SANCTUARY_RADIUS=2;
    static final double SANCTUARY_DAMAGE_TAKEN=.7;
    private static final long PROTECTION_MILLIS=10_000;
    private final ArkBattle battle;

    ArkHealing(ArkBattle battle){this.battle=battle;}

    List<Actor> friends(Actor source){
        if(!source.transferred)return battle.friends(source.seat).stream().filter(ally->!ally.transferred).toList();
        return battle.friends().stream().filter(ally->ally.transferred&&ArkWar.coreArea(ally.position())).toList();
    }

    List<Actor> restore(Actor source,List<Actor> targets,double amount,long now){
        var restored=new ArrayList<Actor>();
        if(amount<=0||targets.isEmpty())return restored;
        boolean sanctuary=source.unit.has(SANCTUARY)&&ArkTraits.active(SANCTUARY,source.seat.counts,battle.rules);
        int layers=source.seat.layers.getOrDefault(SANCTUARY,0);
        boolean protect=sanctuary&&ArkTraits.sanctuaryProtection(layers);

        var centers=targets.stream().filter(Actor::alive).toList();
        for(var target:centers)if(apply(target,amount,protect,now))restored.add(target);
        if(sanctuary){
            double amountPerBurst=amount*ArkTraits.sanctuaryHealing(layers);
            for(var center:centers){
                battle.effects.sanctuary(center,now);
                for(var ally:friends(center))if(ArkCombat.distanceSqr(center.position(),ally.position())<=SANCTUARY_RADIUS*SANCTUARY_RADIUS)
                    apply(ally,amountPerBurst,protect,now);
            }
        }
        return restored;
    }

    private boolean apply(Actor target,double amount,boolean protect,long now){
        double maximum=target.target.health(1);
        double restored=LifeRules.apply(target.target.life,amount/maximum,true).actual()*maximum;
        if(restored<=0)return false;
        if(protect)target.sanctuaryUntil=now+PROTECTION_MILLIS;
        battle.changed(target);
        battle.effects.heal(target,restored,now);
        return true;
    }
}
