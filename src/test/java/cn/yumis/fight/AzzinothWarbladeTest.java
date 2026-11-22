package cn.yumis.fight;
import cn.yumis.fight.modules.affixes.api.AffixText;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.NamedEquipment;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.skills.api.SkillPreview;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** 埃辛诺斯战刃：55级神话战刃，侵蚀累计与对侵蚀目标增伤共用正式元素积累与命中回执。 */
class AzzinothWarbladeTest {
    private Gear warblade(){return NamedEquipment.create("named/azzinoth",new Random(11));}
    private double affixValue(Gear g,String id){return g.affixes.stream().filter(a->a.id.equals(id)).findFirst().orElseThrow().value;}

    private boolean EquipmentTemplatesCreativeContainsMythicWarglaive(){
        return cn.yumis.fight.modules.equipment.api.EquipmentTemplates.creative(Rarity.MYTHIC).stream().anyMatch(x->x.template.equals("named/azzinoth"));
    }

    @Test void erosionBuildupConversionFeedsFasterAccumulation(){
        Gear g=warblade();
        Progress p=new Progress();p.level=55;p.job=JobCatalog.DEMON_HUNTER;
        EquipmentEffects.Snapshot effects=EquipmentEffects.resolve(p,List.of(g),0);
        double buildup=affixValue(g,"unique/erosion_buildup");
        assertEquals(buildup,effects.erosionBuildup,1e-9);
        assertEquals(affixValue(g,"unique/erosion_damage"),effects.erosionDamageBonus,1e-9);
        Runtime resources=new Runtime();resources.equipment=effects;
        Stats stats=SkillPreview.currentStats(new Stats(),resources,0);
        assertEquals(buildup,stats.modifiers.getOrDefault("elementAccumulation/3/ELEMENT_EROSION",0d),1e-9);
        Life life=new Life();life.target=UUID.randomUUID();
        double[] dark=ElementAccumulation.fractions(new double[]{0,0,0,0,.05},1);
        long now=System.currentTimeMillis();
        var plain=ElementAccumulation.advance(life.elemental,life,dark,1,null,false,true,now);
        assertTrue(plain.triggered().isEmpty());
        life=new Life();life.target=UUID.randomUUID();
        var converted=ElementAccumulation.advance(life.elemental,life,dark,1,stats,false,true,now);
        if(buildup>=1)assertTrue(converted.triggered().contains(ElementAccumulation.Kind.EROSION));
        else assertEquals(.05*(1+buildup),converted.state().buildup[ElementAccumulation.Kind.EROSION.ordinal()],1e-9);
    }

}
