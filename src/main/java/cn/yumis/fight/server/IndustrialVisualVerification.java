package cn.yumis.fight.server;

import cn.yumis.fight.industry.*;
import com.google.gson.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated-server checks for the three-cell wind generator lifecycle. */
public final class IndustrialVisualVerification {
    private static final JsonArray results=new JsonArray();
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
    private static void check(String name,Runnable test){JsonObject result=new JsonObject();result.addProperty("name",name);try{test.run();result.addProperty("passed",true);}catch(Throwable t){result.addProperty("passed",false);result.addProperty("error",t.toString());}results.add(result);}
    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ydnf").then(Commands.literal("visualverify").requires(s->Boolean.getBoolean("ydnf.verify")&&s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).executes(c->{run(c.getSource().getLevel());return 1;})));
    }
    private static void place(ServerLevel level,BlockPos pos){
        var stack=IndustrialContent.stack("wind_generator",1);
        ((BlockItem)stack.getItem()).place(new DirectionalPlaceContext(level,pos,Direction.DOWN,stack,Direction.UP));
    }
    private static void clear(ServerLevel level,BlockPos p){
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<5;y++)level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        level.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(4)).forEach(ItemEntity::discard);
    }
    private static void run(ServerLevel level){
        results.asList().clear();BlockPos p=new BlockPos(256,100,256);level.getChunk(p);
        check("base placement reserves exactly three vertical cells",()->{clear(level,p);place(level,p);require(WindGeneratorPartBlock.complete(level,p),"three-cell footprint missing");require(level.getBlockState(p.above(3)).isAir(),"occupied a fourth cell");require(WindGeneratorPartBlock.base(level,p.above(1)).equals(p)&&WindGeneratorPartBlock.base(level,p.above(2)).equals(p),"upper cells resolve to a different base");});
        check("middle or top obstruction rejects placement without consuming the item",()->{for(int obstruction=1;obstruction<=2;obstruction++){clear(level,p);level.setBlock(p.above(obstruction),Blocks.STONE.defaultBlockState(),3);ItemStack stack=IndustrialContent.stack("wind_generator",1);((BlockItem)stack.getItem()).place(new DirectionalPlaceContext(level,p,Direction.DOWN,stack,Direction.UP));require(level.getBlockState(p).isAir()&&stack.getCount()==1,"obstructed placement changed base or item");require(level.getBlockState(p.above(obstruction)).is(Blocks.STONE),"obstruction overwritten");}});
        check("world-height limit rejects the entire footprint",()->{BlockPos high=new BlockPos(256,level.getMaxY()-1,256);ItemStack stack=IndustrialContent.stack("wind_generator",1);var context=new DirectionalPlaceContext(level,high,Direction.DOWN,stack,Direction.UP);require(((MachineBlock)IndustrialContent.ALL_BLOCKS.get("wind_generator").get()).getStateForPlacement(context)==null,"crossed build height");});
        check("breaking any section drops one empty machine and its contents and removes all sections",()->{for(int section=0;section<3;section++){clear(level,p);place(level,p);var be=(MachineBlockEntity)level.getBlockEntity(p);be.setItem(14,new ItemStack(Items.COAL,7));level.destroyBlock(p.above(section),true);for(int i=0;i<3;i++)require(level.getBlockState(p.above(i)).isAir(),"orphan section "+i);var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(4)).stream().filter(e->e.getItem().is(IndustrialContent.ALL_ITEMS.get("wind_generator").get())).toList();require(drops.size()==1&&drops.get(0).getItem().getCount()==1,"machine duplicated or missing");require(!drops.get(0).getItem().has(DataComponents.BLOCK_ENTITY_DATA),"machine still packs contents");require(level.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(4)).stream().filter(e->e.getItem().is(Items.COAL)).mapToInt(e->e.getItem().getCount()).sum()==7,"contents duplicated or missing");}});
        check("creative destruction of an upper section does not drop a second machine",()->{clear(level,p);place(level,p);var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);BlockPos upper=p.above(2);level.getBlockState(upper).getBlock().playerWillDestroy(level,upper,level.getBlockState(upper),player);for(int i=0;i<3;i++)require(level.getBlockState(p.above(i)).isAir(),"creative orphan");require(level.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(4)).isEmpty(),"creative drop");player.setGameMode(GameType.SURVIVAL);});
        check("old single-cell machines migrate without overwriting an occupied cell",()->{clear(level,p);level.setBlock(p,IndustrialContent.ALL_BLOCKS.get("wind_generator").get().defaultBlockState(),3);require(WindGeneratorPartBlock.ensure(level,p)&&WindGeneratorPartBlock.complete(level,p),"legacy wind did not migrate");clear(level,p);level.setBlock(p.above(2),Blocks.DIAMOND_BLOCK.defaultBlockState(),3);level.setBlock(p,IndustrialContent.ALL_BLOCKS.get("wind_generator").get().defaultBlockState(),3);require(!WindGeneratorPartBlock.ensure(level,p),"blocked legacy machine migrated");require(level.getBlockState(p.above(2)).is(Blocks.DIAMOND_BLOCK),"legacy migration overwrote player blocks");});
        check("upper tower does not block wind generation's own sky check",()->{clear(level,p);place(level,p);IndustrialWorld.tick(ServerRuntime.get());var be=(MachineBlockEntity)level.getBlockEntity(p);require(be.currentPower>0&&be.getBlockState().getValue(MachineBlock.ACTIVE),"three-cell tower self-blocks wind generation");});
        check("extended blades have no collision while the tower remains solid",()->{clear(level,p);place(level,p);BlockPos head=p.above(2);var shape=level.getBlockState(head).getCollisionShape(level,head);require(!shape.isEmpty(),"tower lost its collision");var bounds=shape.bounds();require(bounds.minX>=0&&bounds.minY>=0&&bounds.minZ>=0&&bounds.maxX<=1&&bounds.maxY<=1&&bounds.maxZ<=1,"collision extends outside the reserved cell");for(var bladePlane:List.of(Shapes.box(0,0,0,1,.6,.25),Shapes.box(0,0,.75,1,.6,1),Shapes.box(0,0,0,.25,.6,1),Shapes.box(.75,0,0,1,.6,1)))require(!Shapes.joinIsNotEmpty(shape,bladePlane,BooleanOp.AND),"visual blade plane has collision");});
        clear(level,p);Path out=level.getServer().getServerDirectory().resolve("verification/industrial-v2-wind.json");try{Files.createDirectories(out.getParent());Files.writeString(out,results.toString());}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
