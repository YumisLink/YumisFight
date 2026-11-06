package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** The approved native cuboids and pixel atlas, driven by synchronized content actions. */
public final class GblEnemyRenderer extends MobRenderer<DungeonEnemy,GblEnemyRenderer.State,GblModel> {
    public static final class State extends LivingEntityRenderState {
        String move="";long elapsed,duration;boolean moving,dying;int hurt;float deathPhase;
    }
    private final GblEnemyKind kind;
    public GblEnemyRenderer(EntityRendererProvider.Context context,GblEnemyKind kind){super(context,GblModel.load(kind.model),kind.width*.45f);this.kind=kind;}
    @Override public State createRenderState(){return new State();}
    @Override public Identifier getTextureLocation(State state){return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/entity/gbl/"+kind.model+".png");}
    @Override protected float getFlipDegrees(){return 0;}
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(DungeonEnemy enemy){return super.getBoundingBoxForCulling(enemy).inflate(2,2,2);}
    @Override public void extractRenderState(DungeonEnemy enemy,State state,float partial){
        super.extractRenderState(enemy,state,partial);state.move=enemy.move();state.elapsed=Math.max(0,ClientUi.current().serverNow()-enemy.moveAt());state.duration=enemy.moveDuration();state.moving=Math.abs(enemy.getX()-enemy.xo)+Math.abs(enemy.getZ()-enemy.zo)>.002;state.hurt=enemy.hurtTime;state.dying=enemy.isDeadOrDying();state.deathPhase=(enemy.deathTime+partial)/20f;
    }
}
