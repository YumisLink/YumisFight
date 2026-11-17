package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.combat.api.WorldVitalEntity;

/** A transient world actor supplies its initial encounter values to the existing life owner. */
public interface WorldTargetEntity extends WorldVitalEntity {
    Target initialCombatTarget();
}
