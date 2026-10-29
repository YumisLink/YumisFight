package cn.yumis.fight.modules.skills.api;

import net.minecraft.world.phys.Vec3;
import java.util.Map;

/** Authored endpoint and parabola are part of the accepted skill snapshot, never a global projectile model. */
public record FireWavePath(Vec3 origin,Vec3 destination,double height,double millis) {
    public static FireWavePath from(SkillParameters.Snapshot parameters){
        return new FireWavePath(new Vec3(parameters.get("flightX",0),parameters.get("flightY",0),parameters.get("flightZ",0)),new Vec3(parameters.get("flightEndX",0),parameters.get("flightEndY",0),parameters.get("flightEndZ",0)),parameters.get("flightHeight",1.5),parameters.get("flightMillis",1));
    }
    public Vec3 point(double progress){double u=Math.clamp(progress,0,1);return origin.lerp(destination,u).add(0,4*height*u*(1-u),0);}
    public void write(Map<String,Double> values){values.put("flightX",origin.x);values.put("flightY",origin.y);values.put("flightZ",origin.z);values.put("flightEndX",destination.x);values.put("flightEndY",destination.y);values.put("flightEndZ",destination.z);values.put("flightHeight",height);values.put("flightMillis",millis);}
}
