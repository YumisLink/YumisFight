package cn.yumis.fight.modules.industry.client;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Industrial links have their own lifecycle and render pass, independent of skill effects. */
public final class IndustrialBeamRenderer {
    private record Frame(List<Quad> quads){}
    private static final net.minecraft.util.context.ContextKey<Frame> FRAME=new net.minecraft.util.context.ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","industrial_links"));
    private IndustrialBeamRenderer(){}
    public static void register(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(IndustrialBeamRenderer::extract);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(IndustrialBeamRenderer::submit);}
    private static void extract(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent event){List<Quad> quads=new ArrayList<>();for(var link:IndustrialVisuals.beams())beam(quads,link.from(),link.to());var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level!=null)for(var pos:cn.yumis.fight.modules.industry.platform.ManaWand.selections(ManaWandClient.held(),mc.level.dimension().identifier().toString()))if(mc.level.hasChunkAt(pos)){
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){double[] lo={pos.getX()-.015,pos.getY()-.015,pos.getZ()-.015},hi=lo.clone();lo[(axis+1)%3]+=a*1.03;lo[(axis+2)%3]+=b*1.03;hi=lo.clone();hi[axis]+=1.03;beam(quads,new Vec3(lo[0],lo[1],lo[2]),new Vec3(hi[0],hi[1],hi[2]));}
        }HarvestRangePreview.append(quads);event.getRenderState().setRenderData(FRAME,new Frame(List.copyOf(quads)));}
    private static void submit(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event){var frame=event.getLevelRenderState().getRenderData(FRAME);if(frame!=null)WorldQuads.submit(event,frame.quads());}
    private static void beam(List<Quad> out,Vec3 from,Vec3 to){WorldQuads.beam(out,from,to,0xd7ffff,.035,230,0x54d4e6,.11,75);}
}
