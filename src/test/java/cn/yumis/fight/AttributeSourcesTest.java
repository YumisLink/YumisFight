package cn.yumis.fight;
import cn.yumis.fight.api.AttributeSources;
import cn.yumis.fight.api.AttributeSources.*;
import cn.yumis.fight.rules.Rules.Stat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class AttributeSourcesTest {
    @Test void itemStageDoesNotMultiplyCharacterBaseAndCustomNamesRemainUsable(){
        var sources=new AttributeSources();
        sources.replace(1,List.of(new Source("test:stages",1,List.of(new Value(Stat.PHYSICAL_ATTACK,1,Operation.BASE_RATIO,Stage.ITEM),new Value("other:luck",3,Operation.ADD,Stage.CHARACTER)))));
        var progress=new cn.yumis.fight.rules.Models.Progress();
        var gear=cn.yumis.fight.rules.EquipmentRules.create(cn.yumis.fight.rules.Rules.Slot.WEAPON,cn.yumis.fight.rules.Rules.Weapon.KATANA,cn.yumis.fight.rules.Rules.Rarity.COMMON,1,new Random(0));
        var stats=cn.yumis.fight.rules.EquipmentRules.stats(progress,List.of(gear),sources,ignored->{
        }
        );
        assertEquals(35,stats.get(Stat.PHYSICAL_ATTACK),1e-9);
        assertEquals(5,sources.applyNamed(Map.of("other:luck",2d),Stage.CHARACTER).get("other:luck"));
        assertThrows(IllegalArgumentException.class,()->new Value("not_namespaced",1,Operation.ADD,Stage.ITEM));
    }
    @Test void sameRevisionIsIdempotentAndDifferentContentConflicts(){
        var a=new AttributeSources();
        var source=new Source("test",1,List.of(new Value(Stat.HEALTH,10,Operation.ADD,Stage.CHARACTER)));
        assertTrue(a.replace(1,List.of(source)));
        assertFalse(a.replace(1,List.of(source)));
        assertThrows(IllegalArgumentException.class,()->a.replace(2,List.of(new Source("test",1,List.of(new Value(Stat.HEALTH,20,Operation.ADD,Stage.CHARACTER))))));
    }
    @Test void removalLeavesNoContributionAndOldSourceCannotRevive(){
        var a=new AttributeSources();
        var source=new Source("test",4,List.of(new Value(Stat.HEALTH,10,Operation.ADD,Stage.CHARACTER)));
        a.replace(1,List.of(source));
        assertEquals(110,a.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH));
        a.replace(2,List.of());
        assertEquals(100,a.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH));
        assertThrows(IllegalArgumentException.class,()->a.replace(3,List.of(source)));
        a.replace(3,List.of(new Source("test",5,source.values())));
        assertEquals(110,a.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH));
    }
    @Test void percentageStagesUseBaseThenTotal(){
        var a=new AttributeSources();
        a.replace(1,List.of(new Source("test",1,List.of(new Value(Stat.HEALTH,20,Operation.ADD,Stage.CHARACTER),new Value(Stat.HEALTH,.1,Operation.BASE_RATIO,Stage.CHARACTER),new Value(Stat.HEALTH,.2,Operation.TOTAL_RATIO,Stage.CHARACTER)))));
        assertEquals(156,a.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH),1e-9);
    }
}
