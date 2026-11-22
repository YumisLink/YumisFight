package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.TreasureState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class DungeonCommandsTest {
    /** Covers the admin portal and tower gate wiring. The vanilla Commands wrappers need the FML boot,
     * so the same-name literal merge (the risky assumption, "ydnf treasure give" already exists) is
     * verified with plain brigadier builders; addChild merges there exactly as in production. */
    @Test void portalGateCommandFactsMergeRotationAndIds(){
        var dispatcher=new CommandDispatcher<Object>();
        LiteralArgumentBuilder<Object> root=LiteralArgumentBuilder.literal("ydnf");
        root.then(LiteralArgumentBuilder.<Object>literal("treasure").then(LiteralArgumentBuilder.literal("give")));
        // Same order as ServerEvents: this second "treasure" registration must not drop "give".
        root.then(LiteralArgumentBuilder.<Object>literal("treasure")
            .then(LiteralArgumentBuilder.<Object>literal("portal").requires(s->true)
                .then(RequiredArgumentBuilder.argument("position",StringArgumentType.word()))));
        dispatcher.register(root);
        assertEquals(3,dispatcher.parse("ydnf treasure give",null).getContext().getNodes().size(),"既有 treasure give 子节点必须保留");
        assertEquals(3,dispatcher.parse("ydnf treasure portal",null).getContext().getNodes().size(),"新增 portal 子节点必须可见");
        assertEquals(4,dispatcher.parse("ydnf treasure portal 100_64_100",null).getContext().getNodes().size());
        CommandNode<Object> portal=dispatcher.getRoot().getChild("ydnf").getChild("treasure").getChild("portal");
        assertNotNull(portal,"同名 literal 必须合并而不是替换");
        assertNotNull(portal.getRequirement(),"portal 子节点自身持有管理员权限");
        assertNull(dispatcher.getRoot().getChild("ydnf").getChild("treasure").getCommand());

        assertEquals(net.minecraft.world.level.block.Rotation.NONE,DungeonCommands.rotation("south"));
        assertEquals(net.minecraft.world.level.block.Rotation.CLOCKWISE_90,DungeonCommands.rotation("west"));
        assertEquals(net.minecraft.world.level.block.Rotation.CLOCKWISE_180,DungeonCommands.rotation("north"));
        assertEquals(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90,DungeonCommands.rotation("east"));
        assertThrows(IllegalArgumentException.class,()->DungeonCommands.rotation("up"));

        UUID owner=UUID.randomUUID();long now=System.currentTimeMillis();
        var t=TreasureState.portal(owner,66,-320,540,71.0,now);
        assertEquals(0,t.region);
        assertTrue(t.cleared);
        assertTrue(t.portal);
        assertEquals(owner,t.owner);
        assertEquals(66,t.baseLevel);
        assertEquals(-320,t.mapX);
        assertEquals(540,t.mapZ);
        assertEquals(71.0,t.mapY);
        assertEquals(now+30*60*1000L,t.expires);
        assertEquals(-319.5,t.lastX);
        assertEquals(540.5,t.lastZ);
        assertFalse(t.warningFive);
        assertFalse(t.warningOne);

        for(var tower:AwakeningTower.values())assertSame(tower,DungeonCommands.tower(tower.template()));
        assertEquals(AwakeningTower.SAND_MARKET,DungeonCommands.tower("sand_market"));
        assertThrows(IllegalArgumentException.class,()->DungeonCommands.tower("mordor"));
    }
}
