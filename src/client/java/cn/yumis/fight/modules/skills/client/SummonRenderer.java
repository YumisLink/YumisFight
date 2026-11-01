package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
/** All nine summons use their own native cuboid skeleton and pixel atlas. */
public final class SummonRenderer extends MobRenderer<SummonedCreature,SummonRenderer.State,EntityModel<SummonRenderer.State>> {
    public static final class State extends LivingEntityRenderState {
        String move="";long elapsed,duration;boolean moving;
    }
    private final SummonKind kind;
    public SummonRenderer(EntityRendererProvider.Context context,SummonKind kind){
        super(context,SummonCuboidModel.load(kind.model),(float)(kind.width*.45));this.kind=kind;
    }
    @Override public State createRenderState(){return new State();}
    @Override protected int getBlockLightLevel(SummonedCreature entity,net.minecraft.core.BlockPos position){return kind==SummonKind.FIRE||kind==SummonKind.LIGHT||kind==SummonKind.KING?15:super.getBlockLightLevel(entity,position);}
    @Override public void extractRenderState(SummonedCreature entity,State state,float partial){
        super.extractRenderState(entity,state,partial);state.move=entity.move();state.elapsed=Math.max(0,SkillsClient.current().serverNow()-entity.actionAt());state.duration=entity.actionDuration();state.moving=Math.abs(entity.getX()-entity.xo)+Math.abs(entity.getZ()-entity.zo)>.002;
    }
    @Override public Identifier getTextureLocation(State state){return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/entity/summons/"+kind.model+".png");}
}
