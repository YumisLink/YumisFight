package cn.yumis.fight.client;

import cn.yumis.fight.industry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;

/** Local, opt-in traces and placement previews. Never asks the server to load terrain. */
public final class IndustrialVisuals {
    private static int ticks;
    private static long expires;
    private static String beamDimension="";
    public record Beam(Vec3 from,Vec3 to){}
    private static java.util.List<Beam> beams=java.util.List.of();
    public static void receive(com.google.gson.JsonObject packet){
        var mc=Minecraft.getInstance();if(mc.level==null||!packet.get("dimension").getAsString().equals(mc.level.dimension().identifier().toString()))return;
        java.util.List<Beam> next=new java.util.ArrayList<>();for(var value:packet.getAsJsonArray("beams")){var line=value.getAsJsonObject();next.add(new Beam(Vec3.atCenterOf(BlockPos.of(line.get("from").getAsLong())),Vec3.atCenterOf(BlockPos.of(line.get("to").getAsLong()))));}
        beams=java.util.List.copyOf(next);beamDimension=packet.get("dimension").getAsString();expires=Math.min(packet.get("expires").getAsLong(),ClientState.INSTANCE.serverNow()+10000);
    }
    public static java.util.List<Beam> beams(){var mc=Minecraft.getInstance();if(mc.level==null||ClientState.INSTANCE.serverNow()>=expires||!mc.level.dimension().identifier().toString().equals(beamDimension)){beams=java.util.List.of();return beams;}return beams;}
    public static void clear(){beams=java.util.List.of();expires=0;}
    public static long remaining(){return beams().isEmpty()?0:Math.max(0,expires-ClientState.INSTANCE.serverNow());}
    private IndustrialVisuals(){}
    public static void tick(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||++ticks%10!=0)return;
        if(mc.player.containerMenu instanceof IndustrialMenu menu){
            if(menu.info.has("show_lines")&&menu.info.get("show_lines").getAsBoolean()&&menu.info.has("links")){Vec3 from=Vec3.atCenterOf(menu.machine.getBlockPos());for(var raw:menu.info.getAsJsonArray("links"))line(from,Vec3.atCenterOf(BlockPos.of(raw.getAsLong())),0x76dfdc);}
            if(menu.info.has("missing"))for(var raw:menu.info.getAsJsonArray("missing")){BlockPos p=BlockPos.of(raw.getAsLong());for(int x=0;x<=1;x++)for(int z=0;z<=1;z++)Effects.particle(new Vec3(p.getX()+x,p.getY()+.15,p.getZ()+z),0xf59a83,.45f);}
        }
        if(!(mc.player.getMainHandItem().getItem() instanceof ManaWand)||!(mc.hitResult instanceof BlockHitResult hit)||!mc.level.hasChunkAt(hit.getBlockPos())||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof MachineBlockEntity be))return;
        if(be.kind()==DeviceKind.BEACON){double radius=IndustrialRules.CURRENT.beaconRadii[be.tier()-1];Vec3 center=Vec3.atCenterOf(be.getBlockPos());for(int i=0;i<72;i++){double a=i*Math.PI/36,c=Math.cos(a)*radius,s=Math.sin(a)*radius;Effects.particle(center.add(c,s,0),0x88d8ef,.4f);Effects.particle(center.add(c,0,s),0x88d8ef,.4f);Effects.particle(center.add(0,c,s),0x88d8ef,.4f);}}
        else if(be.kind()==DeviceKind.HARVEST){double half=IndustrialRules.CURRENT.harvestSizes[be.tier()-1]/2d;Vec3 center=Vec3.atCenterOf(be.getBlockPos().below()).add(0,-.4,0);Vec3 a=center.add(-half,0,-half),b=center.add(half,0,-half),c=center.add(half,0,half),d=center.add(-half,0,half);line(a,b,0x8be5a4);line(b,c,0x8be5a4);line(c,d,0x8be5a4);line(d,a,0x8be5a4);}
    }
    private static void line(Vec3 a,Vec3 b,int color){double distance=a.distanceTo(b);int segments=Math.max(1,Math.min(128,(int)Math.ceil(distance*2)));for(int i=0;i<=segments;i++)Effects.particle(a.lerp(b,i/(double)segments),color,.42f);}
}
