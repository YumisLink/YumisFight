package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.DeviceType;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.function.ToIntFunction;

/** One structure definition supplies validation, affected positions and the layer viewer. */
public final class IndustrialStructures {
    public record Definition(int size,ToIntFunction<MachineBlockEntity> materialTier){}
    public record Cell(BlockPos pos,int x,int y,int z,boolean air,boolean controller){}
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static {register(DeviceType.FORGE,new Definition(3,MachineBlockEntity::technologyTier));register(DeviceType.STAR,new Definition(5,be->6));}
    private IndustrialStructures(){}
    public static void register(DeviceType type,Definition definition){if(!type.needsStructure()||DEFINITIONS.putIfAbsent(type.id,definition)!=null)throw new IllegalArgumentException("无效或重复工业结构");}
    private static Definition definition(MachineBlockEntity be){var value=DEFINITIONS.get(be.device().id);if(value==null)throw new IllegalArgumentException("未登记结构");return value;}
    public static List<Cell> cells(MachineBlockEntity be){
        if(be.device()==DeviceType.STAR){
            var cells=new ArrayList<Cell>();int half=ArtificialStarPartBlock.HALF;
            for(int y=0;y<ArtificialStarPartBlock.HEIGHT;y++)for(int z=-half;z<=half;z++)for(int x=-half;x<=half;x++)
                cells.add(new Cell(be.getBlockPos().offset(x,y,z),x+half,y,z+half,ArtificialStarPartBlock.shape(x,y,z).isEmpty(),x==0&&y==0&&z==0));
            return cells;
        }
        if(!be.device().needsStructure())return List.of();int size=definition(be).size(),half=size/2;var facing=be.getBlockState().getValue(MachineBlock.FACING);var inward=facing.getOpposite();var right=facing.getClockWise();
        var cells=new ArrayList<Cell>();for(int y=0;y<size;y++)for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            var pos=be.getBlockPos().relative(right,x-half).relative(inward,z).above(y);
            cells.add(new Cell(pos,x,y,z,x>0&&x<size-1&&y>0&&y<size-1&&z>0&&z<size-1,pos.equals(be.getBlockPos())));
        }return cells;
    }
    public static Block structureMaterial(MachineBlockEntity be){return IndustrialContent.ALL_BLOCKS.get("machine_casing_t"+definition(be).materialTier.applyAsInt(be)).get();}
    public static boolean loaded(MachineBlockEntity be){return !be.device().needsStructure()||cells(be).stream().allMatch(cell->be.getLevel().hasChunkAt(cell.pos));}
    public static boolean contains(MachineBlockEntity be,BlockPos position){return cells(be).stream().anyMatch(cell->cell.pos.equals(position));}
    public static List<BlockPos> missing(MachineBlockEntity be){
        if(!be.device().needsStructure())return List.of();var material=structureMaterial(be);var missing=new ArrayList<BlockPos>();
        if(be.device()==DeviceType.STAR){
            for(var cell:cells(be))if(!cell.controller&&(!be.getLevel().hasChunkAt(cell.pos)||!ArtificialStarPartBlock.matches(be.getLevel().getBlockState(cell.pos),cell.x-ArtificialStarPartBlock.HALF,cell.y,cell.z-ArtificialStarPartBlock.HALF)))missing.add(cell.pos);
            return missing;
        }
        for(var cell:cells(be))if(!cell.controller&&(!be.getLevel().hasChunkAt(cell.pos)||(cell.air?!be.getLevel().getBlockState(cell.pos).isAir():!be.getLevel().getBlockState(cell.pos).is(material))))missing.add(cell.pos);
        return missing;
    }
    public static JsonObject description(MachineBlockEntity be){
        var definition=definition(be);JsonObject result=new JsonObject();result.addProperty("size",definition.size);result.addProperty("shell_count",definition.size*definition.size*definition.size-(definition.size-2)*(definition.size-2)*(definition.size-2)-1);JsonArray list=new JsonArray();
        if(be.device()==DeviceType.STAR){result.addProperty("automatic",true);result.addProperty("shell_count",0);}
        for(var cell:cells(be)){JsonObject item=new JsonObject();item.addProperty("position",cell.pos.asLong());item.addProperty("x",cell.x);item.addProperty("y",cell.y);item.addProperty("z",cell.z);item.addProperty("air",cell.air);item.addProperty("controller",cell.controller);list.add(item);}result.add("cells",list);return result;
    }
}
