package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import cn.yumis.fight.modules.skills.client.SkillVfx.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Registered client presentation handlers. No content ID branches belong in dispatch. */
public final class SkillVisualRegistry {
    public interface Handler {
        default boolean immediateEnd(){return false;}
        default boolean persistentEnd(){return false;}
        default boolean persistentEnd(SkillVisualEvent event){return persistentEnd();}
        default boolean cinematic(){return false;}
        default boolean restoreCamera(){return false;}
        default boolean blocksTurning(){return false;}
        default boolean blocksHotbar(){return false;}
        default boolean refreshable(){return false;}
        default long defaultDuration(){return 2500;}
        default int defaultColor(){return SkillVisualEvent.CYAN;}
        default long finishedUntil(Track track,SkillVisualEvent event){return event.at()+500;}
        default void tick(Track track,long time){}
        /** True when content consumes its own cosmetic motion snapshot. */
        default boolean motion(Track track,com.google.gson.JsonObject motion){return false;}
        default void render(LevelRenderContext context,Track track,long age,long time,List<Quad> quads,Vec3 camera){}
        default void play(SkillVisualEvent event){}
        default void animate(Track track,net.minecraft.world.entity.LivingEntity caster,EntityRenderState state,long time){}
        default void decorateAvatar(Track track,Player player,net.minecraft.client.renderer.entity.state.AvatarRenderState state,long time){}
        default int handOrbColor(){return 0;}
        /** Bone pose document id; default mirrors the skill id so shipping a player_animation asset opts in. */
        default String poseAnimation(Track track,long time){return track.start.skill();}
        default CameraType cameraType(Track track,long time){return CameraType.FIRST_PERSON;}
        default CameraPose camera(Track track,long time){return null;}
    }
    private static final Map<String,Handler> HANDLERS=new LinkedHashMap<>();
    private static final Handler NONE=new Handler(){};
    static {BuiltinSkillVisuals.install();CareerSkillVisuals.install();MageSkillVisuals.install();SunMoonSkillVisuals.install();OracleSkillVisuals.install();DemonHunterVisuals.install();}
    private SkillVisualRegistry(){}
    public static void register(String id,Handler handler){if(HANDLERS.putIfAbsent(id,Objects.requireNonNull(handler))!=null)throw new IllegalArgumentException("Duplicate skill visual: "+id);}
    public static Handler handler(String id){return HANDLERS.getOrDefault(id,NONE);}
}
