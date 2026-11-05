package cn.yumis.fight.modules.skills.api;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Content timing shared by approved movement, hit schedules and cosmetic sampling. Times are milliseconds. */
public final class DemonHunterChoreography {
    public static final long BEAM_RISE=200,BEAM_CHANNEL=2000,BEAM_INTERVAL=250;
    public static final int BEAM_HITS=8,HUNT_COMBO_HITS=8;
    public static final long HUNT_COMBO_INTERVAL=100;
    public static final long BEAM_DURATION=BEAM_RISE+BEAM_CHANNEL,HUNT_DURATION=4000;
    public static final double BEAM_HEIGHT=1,HUNT_SPEED=10;
    private DemonHunterChoreography(){}

    public static long beamHitAt(int index){return BEAM_RISE+(index+1)*BEAM_INTERVAL;}

    /** Recheck the current world as well as the approved snapshot; doors may close during the action. */
    public static Vec3 safePosition(LivingEntity actor,Vec3 from,Vec3 destination){
        Vec3 delta=destination.subtract(from),result=from;int steps=Math.max(1,(int)Math.ceil(delta.length()/.2));
        for(int i=1;i<=steps;i++){
            Vec3 point=from.add(delta.scale(i/(double)steps));var pos=BlockPos.containing(point);var box=actor.getBoundingBox().move(point.subtract(actor.position()));
            if(!actor.level().hasChunkAt(pos)||!actor.level().getWorldBorder().isWithinBounds(pos)||!actor.level().noCollision(actor,box))break;result=point;
        }
        return result;
    }

    public record Dance(double duration,int slashes) {
        public Dance {
            if(!Double.isFinite(duration)||duration<=0||slashes<1)throw new IllegalArgumentException("Invalid blade dance timing");
        }
        public double slot(){return duration/2/slashes;}
        public double slashDuration(){return Math.min(duration*.05,slot());}
        public double slashAt(int index){return index*slot();}
        public double slashHitAt(int index){return slashAt(index)+slashDuration();}
        public double fallDuration(){return duration*.075;}
        public double fallAt(){return duration-fallDuration();}
        public int slashIndex(double age){
            if(age<0||age>=duration/2)return -1;
            int index=Math.min(slashes-1,(int)(age/slot()));
            return age<slashHitAt(index)?index:-1;
        }
        public boolean falling(double age){return age>=fallAt()&&age<duration;}
        public boolean visible(double age){return slashIndex(age)>=0||falling(age);}
    }

    public record Segment(Vec3 from,Vec3 to) {
        public Vec3 point(double progress){return from.lerp(to,Math.clamp(progress,0,1));}
        public Vec3 direction(){return to.subtract(from);}
    }

    /** Positions have already been clipped against the caster's complete body on the server. */
    public record Scene(Dance timing,List<Segment> paths,Segment fall) {
        public Scene {paths=List.copyOf(paths);if(paths.size()!=timing.slashes())throw new IllegalArgumentException("Invalid blade dance paths");}
        public Vec3 point(double age){
            if(age>=timing.fallAt())return fall.point((age-timing.fallAt())/timing.fallDuration());
            int index=Math.clamp((int)(Math.max(0,age)/timing.slot()),0,paths.size()-1);
            return paths.get(index).point((age-timing.slashAt(index))/timing.slashDuration());
        }
        public Vec3 direction(double age){
            if(age>=timing.fallAt())return fall.direction();
            return paths.get(Math.clamp((int)(Math.max(0,age)/timing.slot()),0,paths.size()-1)).direction();
        }
        public double progress(double age){
            if(age>=timing.fallAt())return Math.clamp((age-timing.fallAt())/timing.fallDuration(),0,1);
            int index=timing.slashIndex(age);return index<0?0:Math.clamp((age-timing.slashAt(index))/timing.slashDuration(),0,1);
        }
        public JsonObject json(long at){
            var j=new JsonObject();j.addProperty("at",at);j.addProperty("danceDuration",timing.duration());var lines=new JsonArray();
            for(var path:paths){var line=new JsonArray();line.add(pointJson(path.from()));line.add(pointJson(path.to()));lines.add(line);}
            j.add("dancePaths",lines);j.add("fallFrom",pointJson(fall.from()));j.add("fallTo",pointJson(fall.to()));return j;
        }
        public static Scene read(JsonObject j){
            var lines=j.getAsJsonArray("dancePaths");if(lines.isEmpty()||lines.size()>4096)throw new IllegalArgumentException("Invalid blade dance count");
            var paths=new java.util.ArrayList<Segment>();for(var value:lines){var line=value.getAsJsonArray();paths.add(new Segment(readPoint(line.get(0).getAsJsonArray()),readPoint(line.get(1).getAsJsonArray())));}
            return new Scene(new Dance(j.get("danceDuration").getAsDouble(),paths.size()),paths,new Segment(readPoint(j.getAsJsonArray("fallFrom")),readPoint(j.getAsJsonArray("fallTo"))));
        }
    }
    private static JsonArray pointJson(Vec3 p){var j=new JsonArray();j.add(p.x);j.add(p.y);j.add(p.z);return j;}
    private static Vec3 readPoint(JsonArray j){
        var p=new Vec3(j.get(0).getAsDouble(),j.get(1).getAsDouble(),j.get(2).getAsDouble());
        if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z))throw new IllegalArgumentException("Invalid blade dance position");return p;
    }
}
