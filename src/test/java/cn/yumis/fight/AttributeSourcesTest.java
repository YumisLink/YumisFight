package cn.yumis.fight;
import cn.yumis.fight.modules.equipment.api.EquipmentAttributes;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.AttributeSources.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class AttributeSourcesTest {
    @Test void itemStageDoesNotMultiplyCharacterBaseAndCustomNamesRemainUsable(){
        var sources=new AttributeSources();
        sources.replace(1,List.of(new Source("test:stages",1,List.of(new Value(Stat.PHYSICAL_ATTACK,1,Operation.BASE_RATIO,Stage.ITEM),new Value("other:luck",3,Operation.ADD,Stage.CHARACTER)))));
        var progress=new cn.yumis.fight.modules.character.api.Progress();
        var gear=cn.yumis.fight.modules.equipment.api.EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,1,new Random(0));
        var stats=cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(progress,List.of(gear),sources,0,ignored->{
        }
        );
        assertEquals(29,stats.get(Stat.PHYSICAL_ATTACK),1e-9);
        assertEquals(5,sources.applyNamed(Map.of("other:luck",2d),Stage.CHARACTER).get("other:luck"));
        assertThrows(IllegalArgumentException.class,()->new Value("not_namespaced",1,Operation.ADD,Stage.ITEM));
    }
    @Test void replaceIsIdempotentConflictingAndRemovalNeverRevives(){
        var idempotent=new AttributeSources();
        var source=new Source("test",1,List.of(new Value(Stat.HEALTH,10,Operation.ADD,Stage.CHARACTER)));
        assertTrue(idempotent.replace(1,List.of(source)));
        assertFalse(idempotent.replace(1,List.of(source)));
        assertThrows(IllegalArgumentException.class,()->idempotent.replace(2,List.of(new Source("test",1,List.of(new Value(Stat.HEALTH,20,Operation.ADD,Stage.CHARACTER))))));
        var removal=new AttributeSources();
        var retired=new Source("test",4,List.of(new Value(Stat.HEALTH,10,Operation.ADD,Stage.CHARACTER)));
        removal.replace(1,List.of(retired));
        assertEquals(110,removal.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH));
        removal.replace(2,List.of());
        assertEquals(100,removal.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH));
        assertThrows(IllegalArgumentException.class,()->removal.replace(3,List.of(retired)));
        removal.replace(3,List.of(new Source("test",5,retired.values())));
        assertEquals(110,removal.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH));
    }
    @Test void percentageStagesUseBaseThenTotal(){
        var a=new AttributeSources();
        a.replace(1,List.of(new Source("test",1,List.of(new Value(Stat.HEALTH,20,Operation.ADD,Stage.CHARACTER),new Value(Stat.HEALTH,.1,Operation.BASE_RATIO,Stage.CHARACTER),new Value(Stat.HEALTH,.2,Operation.TOTAL_RATIO,Stage.CHARACTER)))));
        assertEquals(156,a.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH),1e-9);
    }
}
