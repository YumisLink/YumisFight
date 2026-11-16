package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.arkguard.api.ArkGuardRing;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.TagValueInput;
import java.util.List;

/** A live roster attached to the front-left pillar; page size never limits attendance. */
final class ParticipationPillar {
    static final int ROWS=8;
    private Board board;
    private Component last;
    private static final class Board extends Display.TextDisplay {
        Board(ServerLevel level){super(EntityTypes.TEXT_DISPLAY,level);}
        @Override public boolean shouldBeSaved(){return false;}
    }
    void update(ServerLevel level,List<Component> rows,int participating,long tick){
        var origin=ArkGuardRing.ORIGIN;
        if(!level.hasChunkAt(origin.offset(-3,2,2)))return;
        if(board==null||board.isRemoved()){
            board=new Board(level);last=null;
        }
        int pages=Math.max(1,(rows.size()+ROWS-1)/ROWS),page=(int)((tick/120)%pages);
        var text=Component.literal("方舟守卫圈").withColor(0xE8D39B)
            .append(Component.literal("\n已参加 "+participating+" / 到场 "+rows.size()).withColor(0xD3E0DE));
        if(rows.isEmpty())text.append(Component.literal("\n暂无玩家到场").withColor(0xA9B8BB));
        for(int i=page*ROWS;i<Math.min(rows.size(),(page+1)*ROWS);i++)text.append(Component.literal("\n")).append(rows.get(i));
        if(pages>1)text.append(Component.literal("\n第 "+(page+1)+" / "+pages+" 页").withColor(0xA9B8BB));
        if(text.equals(last))return;
        var tag=new CompoundTag();
        tag.put("Pos",doubles(origin.getX()-2.5,origin.getY()+1.15,origin.getZ()+2.04));
        tag.put("Rotation",floats(0,0));
        tag.put("text",ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE,text).getOrThrow());
        tag.putString("billboard","fixed");tag.putInt("line_width",180);
        tag.putInt("background",0xB0152327);tag.putBoolean("shadow",true);
        tag.putBoolean("Invulnerable",true);tag.putFloat("view_range",1);
        var brightness=new CompoundTag();brightness.putInt("block",15);brightness.putInt("sky",15);tag.put("brightness",brightness);
        var transform=new CompoundTag();transform.put("translation",floats(0,0,0));transform.put("scale",floats(.65f,.65f,.65f));
        transform.put("left_rotation",floats(0,0,0,1));transform.put("right_rotation",floats(0,0,0,1));tag.put("transformation",transform);
        board.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag));
        if(board.getId()==0||level.getEntity(board.getId())!=board){
            if(!level.addFreshEntity(board)){board=null;return;}
        }
        last=text.copy();
    }
    void clear(){if(board!=null)board.discard();board=null;last=null;}
    private static ListTag doubles(double... values){var out=new ListTag();for(double value:values)out.add(DoubleTag.valueOf(value));return out;}
    private static ListTag floats(float... values){var out=new ListTag();for(float value:values)out.add(FloatTag.valueOf(value));return out;}
}
