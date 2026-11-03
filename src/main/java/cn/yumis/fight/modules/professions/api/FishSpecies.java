package cn.yumis.fight.modules.professions.api;
import java.util.List;
/** 六个稀有度档位里，普通~神话都是物种集合；神器按维度、传说按地形、史诗按水域结构、神话按虚空与珊瑚分池。 */
public final class FishSpecies {
    private FishSpecies(){}
    public static final List<String> COMMON=List.of(
        "fish_common","fish_sick","fish_puffer_polluted","fish_salmon","fish_eel","fish_cod"
    );
    public static final List<String> RARE=List.of(
        "fish_rare","fish_eel_electric","fish_squid_winter","fish_mutant",
        "fish_angelfish_spore","fish_goldscale","fish_glacier_salmon"
    );
    public static final List<String> ARTIFACT_OVERWORLD=List.of(
        "fish_artifact","fish_phoenix_tail","fish_koi","fish_king_salmon","fish_prism"
    );
    public static final List<String> ARTIFACT_NETHER=List.of(
        "fish_magma_squid","fish_flame_ammonite"
    );
    public static final List<String> ARTIFACT_ALL=List.of(
        "fish_artifact","fish_phoenix_tail","fish_koi","fish_king_salmon","fish_prism",
        "fish_magma_squid","fish_flame_ammonite"
    );
    public static final List<String> LEGENDARY_OVERWORLD=List.of(
        "fish_legendary","fish_time_bream","fish_storm_mackerel"
    );
    public static final List<String> LEGENDARY_GEODE=List.of(
        "fish_crystal_catfish","fish_crystal_fin"
    );
    public static final List<String> LEGENDARY_HIGH=List.of(
        "fish_star_ray","fish_moon_snowfish"
    );
    public static final List<String> LEGENDARY_ALL=List.of(
        "fish_legendary","fish_time_bream","fish_storm_mackerel",
        "fish_crystal_catfish","fish_crystal_fin",
        "fish_star_ray","fish_moon_snowfish"
    );
    public static final List<String> EPIC_HAUNTED=List.of("fish_ghost_bream","fish_ghost_eel");
    public static final List<String> EPIC_DEEP=List.of("fish_abyss_catfish","fish_abyss_trout");
    public static final List<String> EPIC_FROZEN=List.of("fish_dragon_ray","fish_icefin");
    public static final List<String> EPIC_ALL=List.of(
        "fish_ghost_bream","fish_ghost_eel",
        "fish_abyss_catfish","fish_abyss_trout",
        "fish_dragon_ray","fish_icefin"
    );
    /** 神话档·虚空（世界底部以下）。 */
    public static final List<String> MYTHIC_VOID=List.of("fish_void_ray","fish_void_eel");
    /** 神话档·珊瑚区。 */
    public static final List<String> MYTHIC_CORAL=List.of("fish_algae_catfish","fish_coral_bream");
    /** 神话档·无地形要求。 */
    public static final List<String> MYTHIC_DEFAULT=List.of("fish_mythic","fish_starry_ray","fish_crystal_fish");
    public static final List<String> MYTHIC_ALL=List.of(
        "fish_mythic","fish_starry_ray","fish_crystal_fish",
        "fish_void_ray","fish_void_eel",
        "fish_algae_catfish","fish_coral_bream"
    );
    /** 传说以上档位依赖环境；其余档位只需要维度。 */
    public record Context(boolean nether,boolean high,boolean geode,boolean soulSand,boolean monument,boolean snowy,boolean inVoid,boolean coral){
        public static final Context PLAIN=new Context(false,false,false,false,false,false,false,false);
        public static Context plain(boolean nether){return new Context(nether,false,false,false,false,false,false,false);}
        /** 史诗档只在特殊水域/结构/雪原真正出现，其它地方会降回传说档。 */
        public boolean epicAvailable(){return soulSand||monument||snowy;}
    }
    public static List<String> pool(FishTier tier,Context context){
        return switch(tier){
            case COMMON->COMMON;
            case RARE->RARE;
            case ARTIFACT->context.nether()?ARTIFACT_NETHER:ARTIFACT_OVERWORLD;
            case LEGENDARY->context.geode()?LEGENDARY_GEODE:context.high()?LEGENDARY_HIGH:LEGENDARY_OVERWORLD;
            case EPIC->context.soulSand()?EPIC_HAUNTED:context.monument()?EPIC_DEEP:context.snowy()?EPIC_FROZEN:EPIC_HAUNTED;
            case MYTHIC->context.inVoid()?MYTHIC_VOID:context.coral()?MYTHIC_CORAL:MYTHIC_DEFAULT;
            default->List.of(tier.itemId());
        };
    }
    public static String itemFor(FishTier tier,double random,Context context){
        List<String> pool=pool(tier,context);
        double clamped=Math.max(0,Math.min(0.999999,random));
        return pool.get((int)(clamped*pool.size()));
    }
}
