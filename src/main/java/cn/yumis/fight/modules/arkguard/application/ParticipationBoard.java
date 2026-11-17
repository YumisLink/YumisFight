package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.arkguard.api.ArkWar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Only registrants appear on the raised crystal-side board. */
final class ParticipationBoard {
    private final ArkDisplays.Label label=new ArkDisplays.Label();
    void update(ServerLevel level,List<Component> rows,int confirmed,int total,String counter,String detail){
        var text=Component.literal("方舟战争 · 参战名单").withColor(0xE8D39B).append(Component.literal("\n"+detail).withColor(0xCBD7DE));
        if(total==0)text.append(Component.literal("\n暂无报名者 · 水晶旁报名").withColor(0x9CA6AF));
        else text.append(Component.literal("\n"+counter+" "+confirmed+" / "+total).withColor(0x80D8FF));
        for(var row:rows)text.append(Component.literal("\n")).append(row);
        label.set(level,new Vec3(ArkWar.ORIGIN.getX()+.5,ArkWar.ORIGIN.getY()+7,ArkWar.ORIGIN.getZ()+87.5),180,text,.75f,300);
    }
    void clear(){label.clear();}
}
