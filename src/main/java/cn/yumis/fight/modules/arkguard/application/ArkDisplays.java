package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.presentation.api.ModText;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** Native display projections local to this scene. */
final class ArkDisplays {
    private ArkDisplays(){}
    private static final class Text extends Display.TextDisplay {Text(ServerLevel level){super(EntityTypes.TEXT_DISPLAY,level);}@Override public boolean shouldBeSaved(){return false;}}
    private static final class Block extends Display.BlockDisplay {Block(ServerLevel level){super(EntityTypes.BLOCK_DISPLAY,level);}@Override public boolean shouldBeSaved(){return false;}}
    static final class Label {
        private Text entity;private Component previous;private Vec3 position;
        void set(ServerLevel level,Vec3 point,float yaw,Component text,float scale,int width){
            text=ModText.decorate(text);
            if(entity!=null&&!entity.isRemoved()&&text.equals(previous)&&point.equals(position))return;
            boolean created=entity==null||entity.isRemoved();if(created)entity=new Text(level);
            var tag=base(point,yaw,scale);tag.put("text",ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE,text).getOrThrow());tag.putString("billboard","fixed");tag.putInt("line_width",width);tag.putInt("background",0xC0152327);tag.putBoolean("shadow",true);
            entity.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag));
            if(created&&!level.addFreshEntity(entity)){entity=null;return;}
            previous=text.copy();position=point;
        }
        void clear(){if(entity!=null)entity.discard();entity=null;previous=null;position=null;}
    }
    static Entity block(ServerLevel level,Vec3 point,String block,float scale){
        var entity=new Block(level);var tag=base(point,0,scale);var state=new CompoundTag();state.putString("Name",block);tag.put("block_state",state);
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag));
        if(!level.addFreshEntity(entity))throw new IllegalStateException("方舟展示实体创建失败");return entity;
    }
    private static CompoundTag base(Vec3 point,float yaw,float scale){
        var tag=new CompoundTag();tag.put("Pos",doubles(point.x,point.y,point.z));tag.put("Rotation",floats(yaw,0));tag.putBoolean("Invulnerable",true);tag.putFloat("view_range",2);
        var brightness=new CompoundTag();brightness.putInt("block",15);brightness.putInt("sky",15);tag.put("brightness",brightness);
        var transform=new CompoundTag();transform.put("translation",floats(0,0,0));transform.put("scale",floats(scale,scale,scale));transform.put("left_rotation",floats(0,0,0,1));transform.put("right_rotation",floats(0,0,0,1));tag.put("transformation",transform);return tag;
    }
    private static ListTag doubles(double... values){var list=new ListTag();for(double value:values)list.add(DoubleTag.valueOf(value));return list;}
    private static ListTag floats(float... values){var list=new ListTag();for(float value:values)list.add(FloatTag.valueOf(value));return list;}
}
