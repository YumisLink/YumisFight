package cn.yumis.fight.modules.professions.api;
import java.util.List;
/** 草药品种与出现地点：普通、稀有、神器各有多个品种，传说及以上单品种。 */
public final class HerbSpecies {
    private HerbSpecies(){}
    /** 普通：宁神花、银叶草、地根草、跌打草、皇血花、石南花。 */
    public static final List<String> COMMON=List.of(
        "herb_peacebloom","herb_silverleaf","herb_earthroot","herb_bruiseweed","herb_kingsblood","herb_heather"
    );
    /** 稀有：盲目草、黄金参、鼠鼠菇、冰灵花、墓地苔、魔力蓟。 */
    public static final List<String> RARE=List.of(
        "herb_blindweed","herb_goldthorn","herb_mushroom","herb_icebloom","herb_gravemoss","herb_manathistle"
    );
    /** 神器：泰罗果、黄金莲、噩梦藤、火蓟草、雪莲花、雪百合。 */
    public static final List<String> ARTIFACT=List.of(
        "herb_terocone","herb_golden_lotus","herb_nightmare_vine","herb_fire_thistle","herb_snow_lotus","herb_snow_lily"
    );
    /** 草原/森林的普通草药（不含地根草）。 */
    public static final List<String> PLAINS_COMMON=List.of(
        "herb_peacebloom","herb_silverleaf","herb_bruiseweed","herb_kingsblood","herb_heather"
    );
    /** 森林类的稀有草药（不含冰灵花/墓地苔）。 */
    public static final List<String> FOREST_RARE=List.of(
        "herb_blindweed","herb_goldthorn","herb_mushroom","herb_manathistle"
    );
    /** 平原/森林的神器草药。 */
    public static final List<String> PLAINS_ARTIFACT=List.of("herb_terocone","herb_golden_lotus");
    /** 冰雪地带的神器草药。 */
    public static final List<String> SNOWY_ARTIFACT=List.of("herb_snow_lotus","herb_snow_lily");
    /** 传说：星光木、月之花、海妖花、船锚珊瑚、虎耳草、元素花。 */
    public static final List<String> LEGENDARY=List.of(
        "herb_starlight_wood","herb_moonflower","herb_sirenflower","herb_anchor_coral","herb_saxifrage","herb_elemental_flower"
    );
    /** 风袭丘陵山顶。 */
    public static final List<String> WINDSWEPT_LEGENDARY=List.of("herb_starlight_wood","herb_moonflower");
    /** 海底。 */
    public static final List<String> OCEAN_LEGENDARY=List.of("herb_sirenflower","herb_anchor_coral");
    /** 热带草原。 */
    public static final List<String> SAVANNA_LEGENDARY=List.of("herb_saxifrage","herb_elemental_flower");
    /** 史诗：耀光草、岩苔、扭曲树皮、死亡花苞、龙血箭竹、黑石草。 */
    public static final List<String> EPIC=List.of(
        "herb_glowgrass","herb_rockmoss","herb_warped_bark","herb_deathbloom","herb_dragonblood_bamboo","herb_blackstone_grass"
    );
    public static final String ROCKMOSS="herb_rockmoss";
    public static final String DEATHBLOOM="herb_deathbloom";
    public static final String DRAGONBLOOD="herb_dragonblood_bamboo";
    public static final String BLACKSTONE_GRASS="herb_blackstone_grass";
    public static final String GLOWGRASS="herb_glowgrass";
    public static final String WARPED_BARK="herb_warped_bark";
    /** 神话：梦之花、风暴藤、虚空花、末日罂粟、愚人菇、涡流草。 */
    public static final List<String> MYTHIC=List.of(
        "herb_dreamflower","herb_stormvine","herb_voidflower","herb_doom_poppy","herb_fool_mushroom","herb_vortex_grass"
    );
    public static final List<String> END_MYTHIC=List.of("herb_voidflower","herb_doom_poppy");
    public static final String STORMVINE="herb_stormvine";
    public static final String DREAMFLOWER="herb_dreamflower";
    public static final String FOOL_MUSHROOM="herb_fool_mushroom";
    public static final String VORTEX_GRASS="herb_vortex_grass";
    public static final String EARTHROOT="herb_earthroot";
    public static final String BRUISEWEED="herb_bruiseweed";
    public static final String SILVERLEAF="herb_silverleaf";
    public static final String ICEBLOOM="herb_icebloom";
    public static final String GRAVEMOSS="herb_gravemoss";
    public static final String NIGHTMARE_VINE="herb_nightmare_vine";
    public static final String FIRE_THISTLE="herb_fire_thistle";

    /** 生长环境：由调用方按维度与生物群系判定后传入。 */
    public enum Habitat {PLAINS,MOUNTAIN,SWAMP,SNOWY,NETHER_FOREST,NETHER_WASTES,SOUL,WINDSWEPT,OCEAN,SAVANNA,PEAKS,BONE,OBSIDIAN,BAMBOO,DARK_FOREST,SUNFLOWER,END_HIGHLANDS,MUSHROOM,DEEP_DARK,STORM_CLIFF,MONUMENT}

    /** 按环境与档位挑具体物品。 */
    public static String pick(Habitat habitat,HerbTier tier,double random){
        if(tier==HerbTier.COMMON){
            return switch(habitat){
                case MOUNTAIN->EARTHROOT;
                case SWAMP,SOUL,NETHER_WASTES,NETHER_FOREST->BRUISEWEED;
                case SNOWY->SILVERLEAF;
                default->fromList(PLAINS_COMMON,random);
            };
        }
        if(tier==HerbTier.RARE){
            return switch(habitat){
                case SNOWY->ICEBLOOM;
                case SWAMP->GRAVEMOSS;
                default->fromList(FOREST_RARE,random);
            };
        }
        if(tier==HerbTier.ARTIFACT){
            return switch(habitat){
                case NETHER_FOREST->NIGHTMARE_VINE;
                case NETHER_WASTES,SOUL->FIRE_THISTLE;
                case SNOWY->fromList(SNOWY_ARTIFACT,random);
                default->fromList(PLAINS_ARTIFACT,random);
            };
        }
        if(tier==HerbTier.LEGENDARY){
            return switch(habitat){
                case WINDSWEPT->fromList(WINDSWEPT_LEGENDARY,random);
                case OCEAN->fromList(OCEAN_LEGENDARY,random);
                case SAVANNA->fromList(SAVANNA_LEGENDARY,random);
                default->fromList(PLAINS_ARTIFACT,random);
            };
        }
        if(tier==HerbTier.EPIC){
            return switch(habitat){
                case BONE->DEATHBLOOM;
                case OBSIDIAN->BLACKSTONE_GRASS;
                case PEAKS->ROCKMOSS;
                case BAMBOO->DRAGONBLOOD;
                case SUNFLOWER->GLOWGRASS;
                case DARK_FOREST->WARPED_BARK;
                default->fromList(PLAINS_ARTIFACT,random);
            };
        }
        if(tier==HerbTier.MYTHIC){
            return switch(habitat){
                case END_HIGHLANDS->fromList(END_MYTHIC,random);
                case MUSHROOM->FOOL_MUSHROOM;
                case DEEP_DARK->DREAMFLOWER;
                case STORM_CLIFF->STORMVINE;
                case MONUMENT->VORTEX_GRASS;
                default->fromList(PLAINS_ARTIFACT,random);
            };
        }
        return fromList(PLAINS_ARTIFACT,random);
    }
    private static String fromList(List<String> pool,double random){
        int index=(int)(Math.max(0,Math.min(0.999999,random))*pool.size());
        return pool.get(index);
    }
}
