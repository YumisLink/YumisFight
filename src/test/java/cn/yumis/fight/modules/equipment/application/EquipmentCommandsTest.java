package cn.yumis.fight.modules.equipment.application;

import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.equipment.api.CraftingEssences;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentCommandsTest {
    /** Covers the admin force-craft entry: every essence writes onto any held gear while the normal
     * essence path keeps its artifact, slot and roll rules untouched. */
    @Test void forcedCraftWritesEveryEssenceWhileNormalApplyKeepsQualification(){
        assertTrue(CraftingEssences.all().size()>=37,"精华清单至少覆盖 32 可合成 + 5 仅投放");
        for(var essence:CraftingEssences.all()){
            var definition=AffixDefinitions.current().affixes.get(essence.affix());
            assertNotNull(definition,essence.id()+" 必须有词条定义");
            assertTrue(definition.active(),essence.id()+" 的词条定义必须开放");
            Gear held=new Gear();held.slot=Slot.RING;held.level=20;
            Gear updated=CraftingEssences.applyForced(held,essence,1);
            assertEquals(1,updated.affixes.size(),essence.id()+" 强制工艺必须追加词条");
            var affix=updated.affixes.getLast();
            assertEquals(essence.affix(),affix.id,essence.id());
            assertTrue(affix.crafted,essence.id()+" 必须是工艺词条");
            assertEquals(Rarity.LEGENDARY,updated.rarity,essence.id());
            assertEquals(1,updated.revision,essence.id()+" 版本号必须递增");
            assertEquals(0,held.affixes.size(),"强制工艺不得改写入参装备");
            assertEquals(Rarity.COMMON,held.rarity,"强制工艺不得改写入参装备");
            assertEquals(0,held.revision,"强制工艺不得改写入参装备");
            if(definition.minimum()!=null){
                Gear low=CraftingEssences.applyForced(held,essence,0),high=CraftingEssences.applyForced(held,essence,1);
                assertEquals(definition.minimum()*definition.scale(),low.affixes.getLast().value,1e-9,essence.id()+" 分位 0 落在区间下限");
                assertEquals(definition.maximum()*definition.scale(),high.affixes.getLast().value,1e-9,essence.id()+" 分位 1 落在区间上限");
            }
        }
        Gear artifact=new Gear();artifact.slot=Slot.RING;artifact.rarity=Rarity.ARTIFACT;artifact.level=20;
        var strength=CraftingEssences.find("essence_strength");
        Gear crafted=CraftingEssences.apply(artifact,strength,new Random(3));
        assertEquals(Rarity.LEGENDARY,crafted.rarity,"正常工艺仍把神器升为传说");
        assertEquals(1,crafted.affixes.size());
        assertTrue(crafted.affixes.getFirst().crafted);
        Gear common=new Gear();common.slot=Slot.RING;common.level=20;
        assertThrows(IllegalArgumentException.class,()->CraftingEssences.apply(common,strength,new Random(3)),"正常工艺仍拒绝非神器");
        assertThrows(IllegalArgumentException.class,()->CraftingEssences.apply(artifact,CraftingEssences.find("essence_pinocchio"),new Random(3)),"正常工艺仍检查部位资格");
        Gear weapon=new Gear();weapon.slot=Slot.WEAPON;weapon.weapon=Weapon.KATANA;weapon.rarity=Rarity.ARTIFACT;weapon.level=20;
        var pinocchio=CraftingEssences.find("essence_pinocchio");
        double value=CraftingEssences.apply(weapon,pinocchio,new Random(5)).affixes.getFirst().value;
        assertTrue(value>=18&&value<=25,"仅投放精华的正常工艺仍在 18～25 区间内随机");
        var speed=CraftingEssences.find("essence_movement_speed");
        Gear feet=new Gear();feet.slot=Slot.FEET;feet.rarity=Rarity.ARTIFACT;feet.level=20;
        double movement=CraftingEssences.apply(feet,speed,new Random(9)).affixes.getFirst().value;
        assertEquals(.1,movement,1e-9,"普通区间工艺仍取中位分位 0.5");
    }

    /** The vanilla Commands wrappers need the FML boot, so the command tree shape is verified with plain
     * brigadier builders exactly as production registers them: craft holds the permission, percent is
     * optional and bounded, and every essence id resolves through the shared catalog lookup. */
    @Test void craftCommandWiresPlayerEssenceAndOptionalPercent(){
        var dispatcher=new CommandDispatcher<Object>();
        LiteralArgumentBuilder<Object> root=LiteralArgumentBuilder.literal("ydnf");
        root.then(LiteralArgumentBuilder.<Object>literal("craft").requires(s->true)
            .then(RequiredArgumentBuilder.argument("player",StringArgumentType.word())
                .then(RequiredArgumentBuilder.argument("essence",StringArgumentType.word())
                    .executes(c->1)
                    .then(RequiredArgumentBuilder.argument("percent",IntegerArgumentType.integer(0,100))))));
        dispatcher.register(root);
        assertEquals(4,dispatcher.parse("ydnf craft Steve essence_strength",null).getContext().getNodes().size(),"省略百分比时直接执行");
        assertEquals(5,dispatcher.parse("ydnf craft Steve essence_strength 80",null).getContext().getNodes().size(),"百分比作为可选第五级参数");
        CommandNode<Object> craft=dispatcher.getRoot().getChild("ydnf").getChild("craft");
        assertNotNull(craft.getRequirement(),"craft 子节点自身持有管理员权限");
        assertNull(craft.getCommand(),"craft 不允许缺玩家与工艺参数执行");
        for(var essence:CraftingEssences.all())assertSame(essence,CraftingEssences.find(essence.id()),essence.id()+" 必须能按 ID 定位");
        assertNull(CraftingEssences.find("essence_unknown"),"未知工艺 ID 必须返回空而不是误配");
    }
}
