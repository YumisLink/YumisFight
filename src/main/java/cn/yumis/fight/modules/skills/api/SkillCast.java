package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;

import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Per-cast state and immutable parameter snapshot; owned by the skill application. */
public class SkillCast {
        public SkillDefinition skill;
        public SkillParameters.Snapshot parameters;
        public Stats stats;
        public cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment;
        public String weapon,id,dimension;
        public Vec3 origin,direction,center;
        public long start,stageStart,window;
        public int slot,segment=1,slashes,visualSequence=-1;
        public long visualTrail;
        public long landedAt;
        public long scheduledHitAt;
        public boolean charging,release,finisher;
        public double speed=1,moved;
        public int hitIndex,elementOverride=-2;
        public boolean dot,terminal;
        public double controlSeconds;
        public Set<UUID> hit=new HashSet<>();
    }
