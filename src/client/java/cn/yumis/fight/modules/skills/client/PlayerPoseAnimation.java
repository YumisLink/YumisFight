package cn.yumis.fight.modules.skills.client;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/** Keyframed bone pose for the vanilla humanoid model. Pure data, sampling and validation; no Minecraft types. */
public final class PlayerPoseAnimation {
    /** Bone slot order; the renderer maps each name to one vanilla model part. */
    public static final List<String> BONES=List.of("root","head","body","right_arm","left_arm","right_leg","left_leg","held_item");
    public static final int HELD_ITEM=7;
    public static final int AXES=6;
    public static final int VALUES=BONES.size()*AXES;
    private static final Set<String> EASINGS=Set.of("smooth","linear","in","out","inout","hold");
    public record Key(long time,float value,String easing){}
    public record Channel(List<Key> keys){
        float sample(long time){
            List<Key> keys=this.keys;
            if(keys.size()==1)return keys.get(0).value();
            if(time<=keys.get(0).time())return keys.get(0).value();
            Key last=keys.getLast();
            if(time>=last.time())return last.value();
            for(int i=1;i<keys.size();i++){
                Key upper=keys.get(i);
                if(time<upper.time()){
                    Key lower=keys.get(i-1);
                    float span=upper.time()-lower.time();
                    if(span<=0)return lower.value();
                    return ease(lower.easing(),lower.value(),upper.value(),(time-lower.time())/span);
                }
            }
            return last.value();
        }
    }
    public record Bone(Channel rotX,Channel rotY,Channel rotZ,Channel posX,Channel posY,Channel posZ){}
    /** Sampled pose: values hold per bone rotX..rotZ (degrees) then posX..posZ (model units); mask marks present bones. */
    public static final class Frame{
        public final float[] values=new float[VALUES];
        public int mask;
        public String heldModel="";
    }
    public final long duration;
    public final boolean loop;
    public final Bone[] bones;
    private final String heldModel;
    public PlayerPoseAnimation(long duration,boolean loop,Bone[] bones){
        this(duration,loop,bones,"");
    }
    private PlayerPoseAnimation(long duration,boolean loop,Bone[] bones,String heldModel){
        this.duration=duration;
        this.loop=loop;
        this.bones=bones.clone();
        this.heldModel=heldModel;
    }
    public Frame frame(long time){
        long sampleTime=loop&&duration>0?Math.floorMod(time,duration):time;
        Frame frame=new Frame();
        frame.heldModel=heldModel;
        for(int i=0;i<bones.length;i++){
            Bone bone=bones[i];
            if(bone==null)continue;
            frame.mask|=1<<i;
            Channel[] channels={bone.rotX(),bone.rotY(),bone.rotZ(),bone.posX(),bone.posY(),bone.posZ()};
            int base=i*AXES;
            for(int axis=0;axis<AXES;axis++){
                Channel channel=channels[axis];
                frame.values[base+axis]=channel==null?0:channel.sample(sampleTime);
            }
        }
        return frame;
    }
    public static float ease(String kind,float from,float to,float u){
        float delta=to-from;
        if(u<=0)return from;
        if(u>=1)return to;
        return switch(kind){
            case "linear"->from+delta*u;
            case "in"->from+delta*u*u;
            case "out"->from+delta*(1-(1-u)*(1-u));
            case "inout"->from+delta*(.5f-.5f*(float)Math.cos(Math.PI*u));
            case "hold"->from;
            default->from+delta*u*u*(3-2*u);
        };
    }
    /** Parses and validates one animation document; unknown bones, empty channels, duplicated times or non-finite values are rejected. */
    public static PlayerPoseAnimation parse(JsonObject json){
        if(json==null)throw new IllegalArgumentException("空的动作配置");
        boolean loop=json.has("loop")&&json.get("loop").getAsBoolean();
        if(!json.has("bones")||!json.get("bones").isJsonObject())throw new IllegalArgumentException("动作缺少 bones");
        JsonObject bonesJson=json.getAsJsonObject("bones");
        Bone[] bones=new Bone[BONES.size()];
        long latest=0;
        for(Map.Entry<String,JsonElement> entry:bonesJson.entrySet()){
            int index=BONES.indexOf(entry.getKey());
            if(index<0)throw new IllegalArgumentException("未知骨骼："+entry.getKey());
            if(!entry.getValue().isJsonObject())throw new IllegalArgumentException("骨骼不是对象："+entry.getKey());
            JsonObject boneJson=entry.getValue().getAsJsonObject();
            Bone bone=new Bone(channel(boneJson,"rot","x"),channel(boneJson,"rot","y"),channel(boneJson,"rot","z"),
                channel(boneJson,"pos","x"),channel(boneJson,"pos","y"),channel(boneJson,"pos","z"));
            for(Channel channel:new Channel[]{bone.rotX(),bone.rotY(),bone.rotZ(),bone.posX(),bone.posY(),bone.posZ()})
                if(channel!=null)latest=Math.max(latest,channel.keys().getLast().time());
            bones[index]=bone;
        }
        boolean empty=true;
        for(Bone bone:bones)if(bone!=null)empty=false;
        if(empty)throw new IllegalArgumentException("动作没有任何骨骼通道");
        long duration=json.has("duration")?json.get("duration").getAsLong():latest;
        if(duration<=0)throw new IllegalArgumentException("动作时长必须为正毫秒");
        String heldModel=json.has("heldModel")?json.get("heldModel").getAsString():"";
        if(!heldModel.isEmpty()&&!heldModel.matches("effect/[a-z0-9_]+"))throw new IllegalArgumentException("无效的演出手持模型");
        return new PlayerPoseAnimation(duration,loop,bones,heldModel);
    }
    private static Channel channel(JsonObject boneJson,String group,String axis){
        if(!boneJson.has(group)||!boneJson.get(group).isJsonObject())return null;
        JsonObject groupJson=boneJson.getAsJsonObject(group);
        if(!groupJson.has(axis))return null;
        JsonArray raw=groupJson.getAsJsonArray(axis);
        List<Key> keys=new ArrayList<>();
        for(JsonElement element:raw){
            JsonArray key=element.getAsJsonArray();
            if(key.size()<2)throw new IllegalArgumentException("关键帧至少需要 [毫秒, 数值]："+group+"/"+axis);
            long time=key.get(0).getAsLong();
            float value=key.get(1).getAsFloat();
            if(time<0)throw new IllegalArgumentException("关键帧时间不能为负："+group+"/"+axis);
            if(!Float.isFinite(value))throw new IllegalArgumentException("关键帧数值无效："+group+"/"+axis);
            String easing=key.size()>2?key.get(2).getAsString():"smooth";
            if(!EASINGS.contains(easing))throw new IllegalArgumentException("未知缓动："+easing);
            keys.add(new Key(time,value,easing));
        }
        if(keys.isEmpty())throw new IllegalArgumentException("通道没有关键帧："+group+"/"+axis);
        keys.sort(Comparator.comparingLong(Key::time));
        for(int i=1;i<keys.size();i++)if(keys.get(i).time()<=keys.get(i-1).time())
            throw new IllegalArgumentException("关键帧时间必须严格递增："+group+"/"+axis);
        return new Channel(List.copyOf(keys));
    }
}
