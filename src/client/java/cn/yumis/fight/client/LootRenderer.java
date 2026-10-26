package cn.yumis.fight.client;
import cn.yumis.fight.game.PrivateLoot;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
public final class LootRenderer extends EntityRenderer<PrivateLoot,ItemEntityRenderState> {
    private final ItemModelResolver resolver;
    private final RandomSource random=RandomSource.create();
    public LootRenderer(EntityRendererProvider.Context c){
        super(c);
        resolver=c.getItemModelResolver();
        shadowRadius=.15f;
    }
    @Override public ItemEntityRenderState createRenderState(){
        return new ItemEntityRenderState();
    }
    @Override public void extractRenderState(PrivateLoot e,ItemEntityRenderState s,float a){
        super.extractRenderState(e,s,a);
        s.extractItemGroupRenderState(e,e.item(),resolver);
        s.shouldSpread=true;
        s.shouldBob=true;
    }
    @Override public void submit(ItemEntityRenderState s,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();
        pose.translate(0,.18+Math.sin(s.ageInTicks/10)*.08,0);
        pose.mulPose(Axis.YP.rotation(s.ageInTicks/20));
        ItemEntityRenderer.submitMultipleFromCount(pose,collector,s.lightCoords,s,random);
        pose.popPose();
        super.submit(s,pose,collector,camera);
    }
}
