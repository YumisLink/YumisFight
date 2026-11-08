package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** sea_train content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class SeaTrain {
    public static final String ID="sea_train";
    public static final ClassicEnemyKind BOSS=SEA_TRAIN_BODIR;
    public static final String[] AREAS={"海贼登车口","鳄鱼货舱","人鱼甲板","西部线车厢","黑鳞船长室","人鱼侧舷","烈焰盾的车头"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{SEA_TRAIN_PHILY,SEA_TRAIN_MALE_MERMAN,SEA_TRAIN_MALE_MERMAN,SEA_TRAIN_MALE_MERMAN,SEA_TRAIN_MALE_MERMAN,SEA_TRAIN_MALE_MERMAN,SEA_TRAIN_MALE_MERMAN,SEA_TRAIN_MERMAN_SHAMAN,SEA_TRAIN_MERMAN_SHAMAN,SEA_TRAIN_MERMAN_SHAMAN},
        new ClassicEnemyKind[]{SEA_TRAIN_GREATFANG,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_BITER,SEA_TRAIN_CROC_BITER,SEA_TRAIN_CROC_BITER,SEA_TRAIN_CROC_BITER},
        new ClassicEnemyKind[]{SEA_TRAIN_SHANA,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_MAGE,SEA_TRAIN_MERMAID_MAGE,SEA_TRAIN_MERMAID_MAGE,SEA_TRAIN_MERMAID_MAGE},
        new ClassicEnemyKind[]{SEA_TRAIN_MASACCIO,SEA_TRAIN_TURTLE,SEA_TRAIN_TURTLE,SEA_TRAIN_TURTLE,SEA_TRAIN_TURTLE,SEA_TRAIN_TURTLE_SPINNER,SEA_TRAIN_TURTLE_SPINNER,SEA_TRAIN_TURTLE_SPINNER,SEA_TRAIN_TURTLE_GUNNER,SEA_TRAIN_TURTLE_GUNNER},
        new ClassicEnemyKind[]{SEA_TRAIN_MOBENI,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_SPEARMAN,SEA_TRAIN_CROC_BITER,SEA_TRAIN_CROC_BITER,SEA_TRAIN_CROC_BITER,SEA_TRAIN_TURTLE_GUNNER,SEA_TRAIN_TURTLE_GUNNER,SEA_TRAIN_TURTLE_GUNNER},
        new ClassicEnemyKind[]{SEA_TRAIN_KONGKONGYI,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_WARRIOR,SEA_TRAIN_MERMAID_MAGE,SEA_TRAIN_MERMAID_MAGE,SEA_TRAIN_MERMAID_MAGE,SEA_TRAIN_TURTLE_SPINNER,SEA_TRAIN_TURTLE_SPINNER,SEA_TRAIN_TURTLE_SPINNER}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(SEA_TRAIN_MALE_MERMAN,List.of(
        new ClassicMove(SEA_TRAIN_MALE_MERMAN,"throw","投掷",1200,3200,"DART",16.0,0.4,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MALE_MERMAN,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_MERMAN_SHAMAN,List.of(
        new ClassicMove(SEA_TRAIN_MERMAN_SHAMAN,"water","水柱／波浪",1450,2350,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(SEA_TRAIN_MERMAN_SHAMAN,"heal","恢复施法",1450,10000,"NONE",2.6,1.0,0,false,-1,"heal",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_MERMAID_WARRIOR,List.of(
        new ClassicMove(SEA_TRAIN_MERMAID_WARRIOR,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MERMAID_WARRIOR,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_MERMAID_MAGE,List.of(
        new ClassicMove(SEA_TRAIN_MERMAID_MAGE,"bubble","泡沫施法",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MERMAID_MAGE,"water","水柱／波浪",1450,2350,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.42,0.54,0.66})
        ));
        moves.put(SEA_TRAIN_CROC_SPEARMAN,List.of(
        new ClassicMove(SEA_TRAIN_CROC_SPEARMAN,"throw","投掷",1200,3200,"DART",16.0,0.4,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_CROC_SPEARMAN,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_CROC_SPEARMAN,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_CROC_BITER,List.of(
        new ClassicMove(SEA_TRAIN_CROC_BITER,"bite","咬击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_CROC_BITER,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_TURTLE,List.of(
        new ClassicMove(SEA_TRAIN_TURTLE,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_TURTLE,"bite","咬击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_TURTLE_SPINNER,List.of(
        new ClassicMove(SEA_TRAIN_TURTLE_SPINNER,"guard","防御姿态",1450,10000,"NONE",2.6,1.0,0,false,-1,"guard",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_TURTLE_SPINNER,"spin","旋转攻击",1600,2500,"CIRCLE",4,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_TURTLE_GUNNER,List.of(
        new ClassicMove(SEA_TRAIN_TURTLE_GUNNER,"cannon","手炮连发",1400,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(SEA_TRAIN_TURTLE_GUNNER,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_PHILY,List.of(
        new ClassicMove(SEA_TRAIN_PHILY,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_PHILY,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_PHILY,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_GREATFANG,List.of(
        new ClassicMove(SEA_TRAIN_GREATFANG,"bite","咬击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_GREATFANG,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_GREATFANG,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_GREATFANG,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_SHANA,List.of(
        new ClassicMove(SEA_TRAIN_SHANA,"bubble","泡沫施法",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_SHANA,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_SHANA,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_MOBENI,List.of(
        new ClassicMove(SEA_TRAIN_MOBENI,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MOBENI,"cannon","手炮连发",1400,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(SEA_TRAIN_MOBENI,"totem","插图腾",1500,2400,"NONE",2.6,1.0,0,false,-1,"totem",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MOBENI,"spin","旋转攻击",1600,2500,"CIRCLE",4,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MOBENI,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MOBENI,"flash","闪光弹",1450,3250,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MOBENI,"cooldown","周期反制蓄势",1450,10000,"NONE",2.6,1.0,0,false,-1,"cooldown",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MOBENI,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_KONGKONGYI,List.of(
        new ClassicMove(SEA_TRAIN_KONGKONGYI,"water","水柱／波浪",1450,2350,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(SEA_TRAIN_KONGKONGYI,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_KONGKONGYI,"spin","旋转攻击",1600,2500,"CIRCLE",6.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_KONGKONGYI,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_KONGKONGYI,"fall_bomb","俯地炮击反应",1450,2350,"NONE",2.6,1.0,0,false,-1,"fall_bomb",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_KONGKONGYI,"fall_heal","俯地恢复",1450,2350,"NONE",2.6,1.0,0,false,-1,"fall_heal",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_KONGKONGYI,"cute","卖萌姿态",1450,2350,"NONE",2.6,1.0,0,false,-1,"cute",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_MASACCIO,List.of(
        new ClassicMove(SEA_TRAIN_MASACCIO,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MASACCIO,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MASACCIO,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_MASACCIO,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_BODIR,List.of(
        new ClassicMove(SEA_TRAIN_BODIR,"punch","体术连拳",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.425,0.675}),
        new ClassicMove(SEA_TRAIN_BODIR,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_BODIR,"spin","旋转攻击",1600,2500,"CIRCLE",4,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_BODIR,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_BODIR,"fire","火焰施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(SEA_TRAIN_BODIR,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_BODIR,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_TOOTHPICK_BIRD,List.of(
        new ClassicMove(SEA_TRAIN_TOOTHPICK_BIRD,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_JELLYFISH,List.of(
        new ClassicMove(SEA_TRAIN_JELLYFISH,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.46})
        ));
        moves.put(SEA_TRAIN_CROCODILE_TOTEM,List.of(
        new ClassicMove(SEA_TRAIN_CROCODILE_TOTEM,"core","核心暴露／打断",1450,10000,"NONE",2.6,1.0,0,false,-1,"core",0,new double[]{0.46}),
        new ClassicMove(SEA_TRAIN_CROCODILE_TOTEM,"explode","自爆准备",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private SeaTrain(){}
}
