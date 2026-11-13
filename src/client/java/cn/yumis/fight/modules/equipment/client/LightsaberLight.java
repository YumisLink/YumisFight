package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.equipment.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.*;
import java.util.*;

/** Render-only local light. No world light values, blocks, AI or spawning are changed. */
public final class LightsaberLight {
    private static volatile Map<Long,Integer> light=Map.of();
    private static ClientLevel world;
    private static BlockPos origin;
    private LightsaberLight(){}
    public static boolean saber(ItemStack stack){var gear=EquipmentItems.view(stack);return gear!=null&&gear.weapon()==Weapon.LIGHTSABER;}
    public static boolean localModel(net.minecraft.world.entity.LivingEntity owner,ItemDisplayContext context){var mc=Minecraft.getInstance();return owner!=null&&owner==mc.player&&(context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);}
    public static void tick(){
        var mc=Minecraft.getInstance();var level=mc.level;var player=mc.player;
        boolean enabled=level!=null&&player!=null&&player.isAlive()&&(saber(player.getMainHandItem())||saber(player.getOffhandItem()));
        BlockPos next=enabled?BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(.4))):null;
        if(world==level&&Objects.equals(origin,next)&&(!enabled||level.getGameTime()%5!=0))return;
        Map<Long,Integer> updated=new HashMap<>();
        if(enabled){
            var queue=new ArrayDeque<BlockPos>();updated.put(next.asLong(),4);queue.add(next);
            while(!queue.isEmpty()){
                var pos=queue.removeFirst();int current=updated.get(pos.asLong());if(current<=1)continue;
                for(Direction direction:Direction.values()){
                    var neighbor=pos.relative(direction);if(!level.hasChunkAt(neighbor))continue;
                    int value=current-Math.max(1,level.getBlockState(neighbor).getLightDampening());
                    if(value>updated.getOrDefault(neighbor.asLong(),0)){updated.put(neighbor.asLong(),value);queue.add(neighbor);}
                }
            }
        }
        boolean changed=world!=level||!light.equals(updated);
        var previous=origin;var previousWorld=world;light=Map.copyOf(updated);origin=next;world=level;
        if(changed){if(previous!=null&&previousWorld==level)dirty(level,previous);if(next!=null)dirty(level,next);}
    }
    private static void dirty(ClientLevel level,BlockPos pos){level.setSectionRangeDirty(SectionPos.blockToSectionCoord(pos.getX()-4),SectionPos.blockToSectionCoord(pos.getY()-4),SectionPos.blockToSectionCoord(pos.getZ()-4),SectionPos.blockToSectionCoord(pos.getX()+4),SectionPos.blockToSectionCoord(pos.getY()+4),SectionPos.blockToSectionCoord(pos.getZ()+4));}
    public static int block(int original,BlockPos position){return Math.max(original,light.getOrDefault(position.asLong(),0));}
    public static int packed(int original,BlockPos position){return LightCoordsUtil.pack(block(LightCoordsUtil.block(original),position),LightCoordsUtil.sky(original));}
}
