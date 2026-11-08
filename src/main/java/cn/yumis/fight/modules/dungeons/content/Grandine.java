package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** grandi content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class Grandine {
    public static final String ID="grandi";
    public static final ClassicEnemyKind BOSS=GRANDI_FURMAN;
    public static final String[] AREAS={"格莱尼输送带","岩浆冷却口","第一供能区","熔岩车间","虚空投影区","核心供能区","弗曼的虚空炉心"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE},
        new ClassicEnemyKind[]{GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_MAGMA_SLIME,GRANDI_MAGMA_SLIME,GRANDI_MAGMA_SLIME,GRANDI_MAGMA_SLIME,GRANDI_MAGMA_SLIME},
        new ClassicEnemyKind[]{GRANDI_POTURA,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_LAVA_SPIDER,GRANDI_LAVA_SPIDER,GRANDI_LAVA_SPIDER,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS},
        new ClassicEnemyKind[]{GRANDI_POTURA,GRANDI_MAGMA_SLIME,GRANDI_MAGMA_SLIME,GRANDI_MAGMA_SLIME,GRANDI_LAVA_SPIDER,GRANDI_LAVA_SPIDER,GRANDI_LAVA_SPIDER,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS},
        new ClassicEnemyKind[]{GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_LAVA_SPIDER,GRANDI_LAVA_SPIDER,GRANDI_LAVA_SPIDER,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS},
        new ClassicEnemyKind[]{GRANDI_POTURA,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_ASSAULT_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_FLYING_GRANITE,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS,GRANDI_MAGMA_OCTOPUS}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(GRANDI_ASSAULT_GRANITE,List.of(
        new ClassicMove(GRANDI_ASSAULT_GRANITE,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(GRANDI_ASSAULT_GRANITE,"claw","爪击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(GRANDI_FLYING_GRANITE,List.of(
        new ClassicMove(GRANDI_FLYING_GRANITE,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(GRANDI_FLYING_GRANITE,"takeoff","起飞与空中转向",2200,8000,"NONE",2.6,1.0,0,false,-1,"takeoff",0,new double[]{0.46})
        ));
        moves.put(GRANDI_MAGMA_SLIME,List.of(
        new ClassicMove(GRANDI_MAGMA_SLIME,"burrow","钻地",1650,8000,"NONE",2.6,1.0,0,false,-1,"burrow",0,new double[]{0.8})
        ));
        moves.put(GRANDI_LAVA_SPIDER,List.of(
        new ClassicMove(GRANDI_LAVA_SPIDER,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(GRANDI_MAGMA_OCTOPUS,List.of(
        new ClassicMove(GRANDI_MAGMA_OCTOPUS,"tentacle","触须抽击",1450,2350,"FAN",6.0,1.4,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(GRANDI_POTURA,List.of(
        new ClassicMove(GRANDI_POTURA,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.2,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(GRANDI_POTURA,"powered_guard","供能保护姿态",1450,10000,"NONE",4.0,1.0,0,false,-1,"powered_guard",0,new double[]{0.46})
        ));
        moves.put(GRANDI_FURMAN,List.of(
        new ClassicMove(GRANDI_FURMAN,"inhale","吸附吸血",1450,2350,"CIRCLE",8.0,1.0,0.8,true,3,"inhale",0,new double[]{0.46}),
        new ClassicMove(GRANDI_FURMAN,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.2,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(GRANDI_FURMAN,"burrow","钻地",1650,8000,"NONE",4.0,1.0,0,false,-1,"burrow",0,new double[]{0.8}),
        new ClassicMove(GRANDI_FURMAN,"summon","召唤",1800,10000,"NONE",4.0,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(GRANDI_FURMAN,"quake","粉碎时空",2500,9000,"WAVE",8,1.2,1.2,false,-1,"quake",12,new double[]{0.46}),
        new ClassicMove(GRANDI_FURMAN,"ore_burst","脚下矿石爆炸",1450,2350,"CIRCLE",2.5,1.2,1.2,true,1,"",0,new double[]{0.46})
        ));
        moves.put(GRANDI_FURMAN_PHANTOM,List.of(
        new ClassicMove(GRANDI_FURMAN_PHANTOM,"summon","召唤",1800,10000,"NONE",4.0,1.0,0,false,-1,"summon",0,new double[]{0.46})
        ));
        moves.put(GRANDI_GENERATOR,List.of(
        new ClassicMove(GRANDI_GENERATOR,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.46}),
        new ClassicMove(GRANDI_GENERATOR,"core","核心暴露／打断",1450,10000,"NONE",2.6,1.0,0,false,-1,"core",0,new double[]{0.46})
        ));
        moves.put(GRANDI_VOID_CORE,List.of(
        new ClassicMove(GRANDI_VOID_CORE,"core","核心暴露／打断",1450,10000,"NONE",2.6,1.0,0,false,-1,"core",0,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private Grandine(){}
}
