package cn.yumis.fight.modules.dungeons.client;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
public final class EnemyRenderer extends HumanoidMobRenderer<DungeonEnemy,EnemyRenderer.State,EnemyModel> {
    private final String kind;
    public static class State extends HumanoidRenderState {
        public String kind="goblin";
        public boolean preparing;
    }
    public static ModelLayerLocation layer(String kind){
        return new ModelLayerLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath(cn.yumis.fight.platform.ItemData.NS,"enemy/"+kind),"main");
    }
    public EnemyRenderer(EntityRendererProvider.Context context,String kind){
        super(context,new EnemyModel(context.bakeLayer(layer(kind.startsWith("goblin")?"goblin":kind.contains("wyvern")?"wyvern":"dragonman")),kind.contains("wyvern")),.35f);
        this.kind=kind;
    }
    @Override public State createRenderState(){
        return new State();
    }
    @Override public void extractRenderState(DungeonEnemy entity,State state,float partial){
        super.extractRenderState(entity,state,partial);
        state.kind=kind;
        state.preparing=entity.isAggressive();
    }
    @Override public Identifier getTextureLocation(State state){
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(cn.yumis.fight.platform.ItemData.NS,"textures/entity/"+state.kind+".png");
    }
}
