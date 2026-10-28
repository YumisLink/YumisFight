package cn.yumis.fight;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.skills.api.SkillSchedule;
import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.attributes.api.ParameterModifier;

import cn.yumis.fight.modules.skills.api.SkillCatalog;

import cn.yumis.fight.modules.skills.api.SkillParameters;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.attributes.api.Stats;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class SkillPreviewTest {
    @Test void sharedCalculationKeepsExtraRollsBeforeTheSingleCriticalRoll(){
        Stats stats=new Stats();stats.add(Stat.PHYSICAL_ATTACK,100);stats.modifiers.put("crit",.5);
        Affix first=new Affix();first.value=10;first.maximum=30;first.element=0;
        Affix second=new Affix();second.value=20;second.maximum=60;second.element=0;
        stats.extras.add(new cn.yumis.fight.modules.attributes.api.ExtraDamage(first.element,first.value,first.maximum));stats.extras.add(new cn.yumis.fight.modules.attributes.api.ExtraDamage(second.element,second.value,second.maximum));
        int[] calls={0};double[] rolls={.25,.75,.1};
        Random sequence=new Random(){@Override public double nextDouble(){return rolls[calls[0]++];}};
        var actual=CombatRules.calculate(stats,0,new double[4],false,new int[0],2,true,sequence);
        assertEquals(3,calls[0]);
        assertEquals(300,actual.main());
        assertEquals(97.5,actual.extra());
        assertEquals(397.5,actual.total());
        var panel=CombatRules.panel(stats,false,new int[0],2,true,true);
        assertEquals(290,panel.total());
        assertEquals(3,calls[0]);
    }
    @Test void currentBuffExpiresWithoutMutatingEquipmentSnapshot(){
        Stats equipment=new Stats();equipment.add(Stat.PHYSICAL_ATTACK,100);
        cn.yumis.fight.modules.combat.api.Runtime resources=new cn.yumis.fight.modules.combat.api.Runtime();resources.buffs.put("yumis_dungeon_and_fight:overdrive",1000L);resources.absorption.put("overdrivePower",.3);
        assertEquals(130,SkillPreview.currentStats(equipment,resources,999).get(Stat.PHYSICAL_ATTACK));
        assertEquals(100,SkillPreview.currentStats(equipment,resources,1000).get(Stat.PHYSICAL_ATTACK));
        assertEquals(100,equipment.get(Stat.PHYSICAL_ATTACK));
    }
    @Test void panelRangeBracketsTheActualNoncriticalZeroDefenseFormula(){
        Progress progress=new Progress();Stats stats=new Stats();
        stats.add(Stat.MAGICAL_ATTACK,70);stats.add(Stat.INTELLIGENCE,40);stats.add(Stat.DARK,60);stats.add(Stat.FIRE,30);
        stats.modifiers.put("final",.25);stats.modifiers.put("skillProduct",1.2);
        Affix affix=new Affix();affix.value=10;affix.maximum=30;affix.element=1;stats.extras.add(new cn.yumis.fight.modules.attributes.api.ExtraDamage(affix.element,affix.value,affix.maximum));
        var preview=SkillPreview.damage(progress,stats,SkillCatalog.GHOST,2,false,new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot());
        Random midpoint=new Random(){@Override public double nextDouble(){return .5;}};
        double actual=CombatRules.calculate(stats,0,new double[4],true,new int[]{3},2,true,midpoint).total();
        assertEquals((preview.minimum()+preview.maximum())/2,actual,1e-8);
    }
    @Test void addedDanceSlashesFinishWithinDurationWithoutChangingDefaultRhythm(){
        var normal=SkillSchedule.dance(SkillParameters.resolve(SkillCatalog.DANCE,1,List.of()));
        assertEquals(125,normal.intervalMillis());
        assertEquals(1,normal.due(0));
        assertEquals(9,normal.due(1124));
        assertEquals(10,normal.due(1125));
        var extra=SkillSchedule.dance(SkillParameters.resolve(SkillCatalog.DANCE,1,List.of(new ParameterModifier("gear",SkillCatalog.DANCE.id,"slashes",2,1))));
        assertEquals(12,extra.slashes());
        assertEquals(1250,extra.durationMillis());
        assertEquals(12,extra.due(1250));
        assertEquals(12,extra.due(2000));
        int executed=0;
        for(long time:new long[]{0,60,125,500,1249,1900}){
            int newlyDue=extra.due(time)-executed;
            assertTrue(newlyDue>=0);
            executed+=newlyDue;
        }
        assertEquals(12,executed);
    }

    @Test void ghostUsesMagicBeforePromotionAndPhysicalAfterward(){
        Progress progress=new Progress();Stats stats=new Stats();
        stats.add(Stat.MAGICAL_ATTACK,50);stats.add(Stat.PHYSICAL_ATTACK,100);stats.add(Stat.DARK,60);
        assertEquals(150,SkillPreview.damage(progress,stats,SkillCatalog.GHOST,2,false,new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot()).minimum());
        progress.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);
        assertEquals(300,SkillPreview.damage(progress,stats,SkillCatalog.GHOST,2,false,new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot()).minimum());
    }
    @Test void liveParameterChangesAndFinisherAreReflectedInTotals(){
        Progress progress=new Progress();progress.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);progress.skills.put("yumis_dungeon_and_fight:dance",1);
        Stats stats=new Stats();stats.add(Stat.PHYSICAL_ATTACK,100);stats.ranks.put("SWORD",2);
        var params=SkillParameters.resolve(SkillCatalog.DANCE,3,List.of(new ParameterModifier("gear",SkillCatalog.DANCE.id,"slashes",2,1)));
        var preview=SkillPreview.resolve(progress,stats,SkillCatalog.DANCE,params,new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot());
        assertEquals(3,preview.rank());assertEquals(12,preview.slashes());
        assertEquals(preview.hit().minimum()*12+preview.finisher().minimum(),preview.total().minimum(),1e-8);
        stats.modifiers.put("cooldownRecovery",.5);
        assertEquals(20,SkillPreview.cooldown(SkillCatalog.DANCE,stats));
    }
}
