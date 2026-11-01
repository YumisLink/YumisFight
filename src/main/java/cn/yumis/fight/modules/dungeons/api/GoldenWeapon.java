package cn.yumis.fight.modules.dungeons.api;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
/** Shared movement semantics; rendering never determines damage or item contents. */
public final class GoldenWeapon {
    public static final double SPEED=5,RANGE=50,RADIUS=4;
    public static final long WINDUP=1000,FUSE=3000;
    public static final List<String> MODELS=EquipmentVisualItems.weaponModels().stream().filter(m->!m.item().equals("staff")&&!m.item().equals("club")).map(EquipmentVisualItems.WeaponModel::id).toList();
    public UUID id=UUID.randomUUID(),boss,target;
    public String instance,model,phase="AIM";
    public long created,launched,landed;
    public double x,y,z,dx,dy,dz,distance;
    public Set<UUID> struck=new HashSet<>();
    public Vec3 position(){return new Vec3(x,y,z);}
    public Vec3 direction(){return new Vec3(dx,dy,dz);}
    public void position(Vec3 p){x=p.x;y=p.y;z=p.z;}
    public void aim(Vec3 p){Vec3 d=p.subtract(position()).normalize();dx=d.x;dy=d.y;dz=d.z;}
}
