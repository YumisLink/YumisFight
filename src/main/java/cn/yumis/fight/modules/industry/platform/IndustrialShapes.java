package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.IndustrialTechnology;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Fixed model bounds shared by selection and collision; animation never moves them. */
final class IndustrialShapes {
    private static final Map<String,VoxelShape[]> SHAPES=new HashMap<>();
    private IndustrialShapes(){}
    static VoxelShape machine(IndustrialContent.Spec spec,Direction facing){
        var shapes=SHAPES.computeIfAbsent(spec.id(),id->{
            VoxelShape[] values={Shapes.empty(),Shapes.empty(),Shapes.empty(),Shapes.empty()};
            for(var element:IndustrialTechnology.spec(id).getAsJsonArray("boxes")){
                var box=element.getAsJsonArray();double x0=box.get(0).getAsDouble(),y0=box.get(1).getAsDouble(),z0=box.get(2).getAsDouble(),x1=box.get(3).getAsDouble(),y1=box.get(4).getAsDouble(),z1=box.get(5).getAsDouble();
                for(int rotation=0;rotation<4;rotation++){
                    values[rotation]=Shapes.or(values[rotation],Block.box(x0,y0,z0,x1,y1,z1));
                    double nx0=16-z1,nz0=x0,nx1=16-z0,nz1=x1;x0=nx0;z0=nz0;x1=nx1;z1=nz1;
                }
            }return values;
        });return shapes[switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;}];
    }
    static VoxelShape windPart(int part,Direction facing){
        if(part==1)return Block.box(5,0,5,11,16,11);
        return switch(facing){case EAST->Block.box(4,0,4,13,16,12);case SOUTH->Block.box(4,0,4,12,16,13);case WEST->Block.box(3,0,4,12,16,12);default->Block.box(4,0,3,12,16,12);};
    }
}
