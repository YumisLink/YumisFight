package cn.yumis.fight.modules.skills.api;
import net.minecraft.world.phys.*;

/** A swept attack also hits when either end is already inside the expanded target. */
public final class SkillGeometry {
    private SkillGeometry(){}
    public static boolean intersects(AABB target,Vec3 from,Vec3 to){return target.contains(from)||target.contains(to)||target.clip(from,to).isPresent();}
}
