package cn.yumis.fight;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;

import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.equipment.api.Gem;
import cn.yumis.fight.modules.equipment.api.Gear;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class EquipmentTest {

    @Test void socketsRespectPartSourceLevelDuplicatesAndOverwrite(){
        Random r=new Random(1);
        Gear g=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,20,r);
        g=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"socket_stone",r);
        g=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"socket_stone",r);
        Gear finalGear=g;
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(finalGear),"socket_stone",r));
        Gem sample=new Gem();
        sample.group="WEAPON";
        sample.level=20;
        sample.affixes.add(new Affix("sample",Stat.STRENGTH,5,false));
        Gear one=EquipmentRules.socket(g,sample,0);
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.socket(one,sample,1));
        Gem replacement=new Gem();
        replacement.group="all";
        replacement.affixes.add(new Affix("replacement",Stat.STRENGTH,7,false));
        Gear two=EquipmentRules.socket(one,replacement,0);
        assertEquals("replacement",two.gems.getFirst().affixes.getFirst().id);
        sample.level=21;
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.socket(two,sample,1));
    }

}
