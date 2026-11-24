package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.ActionContext;
import cn.yumis.fight.modules.combat.api.ActionPhase;
import cn.yumis.fight.modules.combat.api.ActionPower;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Per-cast state and immutable parameter snapshot; owned by the skill application. */
public class SkillCast {
    public SkillDefinition skill;
    public SkillParameters.Snapshot parameters;
    public Stats stats;
    public cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment;
    public String weapon, id, dimension, session = "", job = "", instance = "";
    public long generation;
    public long cooldownMillis;
    public Vec3 towerStart, towerDestination;
    public String summonSource = "", summonMove = "", visualWeapon = "", eyeGear = "";
    public double sweepDegrees;
    public boolean summonSlow;
    public boolean passiveDamage, suppressEquipment, previousGravity;
    public Vec3 origin, direction, center;
    public long start, stageStart, window;
    public int slot, segment = 1, slashes, visualSequence = -1;
    public int timelineCursor;
    public ActionPhase phase = ActionPhase.IDLE;
    public boolean canBreak;
    public long visualTrail;
    public long landedAt;
    public long scheduledHitAt;
    public boolean charging, release, finisher;
    public double speed = 1, moved;
    public int hitIndex, elementOverride = -2;
    public int[] elementsOverride;
    public double chargeProgress = 1.0;
    public String damagePhase = "", contactGroup = "";
    public boolean dot, terminal, effectContinues;
    public double controlSeconds;
    public Set<UUID> hit = new HashSet<>();

    public boolean running() {
        return phase.running();
    }

    /** Adapts this player cast snapshot into the actor-agnostic combat ActionContext. */
    public ActionContext toActionContext(UUID actorId, boolean magic, int[] elements) {
        double damage = parameters == null ? (skill == null ? 0 : skill.coefficient) : parameters.get("damage", skill == null ? 0 : skill.coefficient);
        ActionPower power = ActionPower.fromStats(stats, damage, magic, elements, speed)
            .withControl(controlSeconds);
        return ActionContext.forPlayer(
            id, actorId, dimension, instance, origin, direction, center, power,
            parameters == null ? Map.of() : parameters.values(), start
        );
    }
}
