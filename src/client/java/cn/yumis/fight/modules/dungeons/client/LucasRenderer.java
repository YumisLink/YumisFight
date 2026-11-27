package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * 鲁卡斯本体与「鲁卡斯的幻影」骨骼渲染器：
 * - 共用 GblModel（dungeons/dragon_tower/lucas.json）；
 * - 本体使用深绿龙鳞+青铜金甲+紫晶雷核贴图；幻影使用半透明苍青紫雷光贴图以便一眼区分真假。
 */
public final class LucasRenderer extends MobRenderer<DungeonEnemy, GblEnemyRenderer.State, GblModel> {
    private static final Identifier MAIN_TEXTURE =
        Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/entity/dragon_tower/lucas.png");
    private static final Identifier CLONE_TEXTURE =
        Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "textures/entity/dragon_tower/lucas_clone.png");

    private final boolean clone;
    private final float scale;

    public LucasRenderer(EntityRendererProvider.Context context, boolean clone, float scale) {
        super(context, GblModel.load("dragon_tower/lucas"), clone ? 0.45f : 0.65f);
        this.clone = clone;
        this.scale = scale;
    }

    @Override
    protected void scale(GblEnemyRenderer.State state, PoseStack pose) {
        if (scale != 1.0f) pose.scale(scale, scale, scale);
    }

    @Override
    public GblEnemyRenderer.State createRenderState() {
        return new GblEnemyRenderer.State();
    }

    @Override
    public Identifier getTextureLocation(GblEnemyRenderer.State state) {
        return clone ? CLONE_TEXTURE : MAIN_TEXTURE;
    }

    @Override
    protected @Nullable RenderType getRenderType(GblEnemyRenderer.State state, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (clone) {
            return RenderTypes.entityTranslucent(CLONE_TEXTURE);
        }
        return super.getRenderType(state, bodyVisible, translucent, glowing);
    }

    @Override
    protected float getFlipDegrees() {
        return 0f;
    }

    @Override
    protected AABB getBoundingBoxForCulling(DungeonEnemy enemy) {
        return super.getBoundingBoxForCulling(enemy).inflate(2.5, 3.0, 2.5);
    }

    @Override
    public void extractRenderState(DungeonEnemy enemy, GblEnemyRenderer.State state, float partial) {
        super.extractRenderState(enemy, state, partial);
        state.move = enemy.move();
        state.elapsed = Math.max(0L, ClientUi.current().serverNow() - enemy.moveAt());
        state.duration = enemy.moveDuration();
        state.moving = Math.abs(enemy.getX() - enemy.xo) + Math.abs(enemy.getZ() - enemy.zo) > 0.002;
        state.hurt = enemy.hurtTime;
        state.dying = enemy.isDeadOrDying();
        state.deathPhase = (enemy.deathTime + partial) / 20f;
    }
}
