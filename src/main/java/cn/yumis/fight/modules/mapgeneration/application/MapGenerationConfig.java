package cn.yumis.fight.modules.mapgeneration.application;

import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Native config registration; the server reads these settings only for a freshly created world. */
public final class MapGenerationConfig {
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue GENERATE_STARTER_VILLAGE;
    private static final ModConfigSpec.ConfigValue<String> STARTER_VILLAGE_BLUEPRINT;
    static {
        var builder=new ModConfigSpec.Builder();
        builder.comment("野外建筑与出生点地图生成设置。多人游戏以服务器的配置为准。").push("mapgeneration");
        GENERATE_STARTER_VILLAGE=builder
            .comment("是否在新世界首次创建时生成出生新手村；不补建旧世界，不重建玩家已修改的村庄。")
            .worldRestart().define("generateStarterVillage",true);
        STARTER_VILLAGE_BLUEPRINT=builder
            .comment("出生新手村使用的蓝图资源 ID，对应 data/<命名空间>/map_blueprint/<路径>.json。")
            .worldRestart().define("starterVillageBlueprint","yumis_dungeon_and_fight:starter_village",
                value->value instanceof String text&&Identifier.tryParse(text)!=null);
        builder.pop();SPEC=builder.build();
    }
    private MapGenerationConfig() {}
    public static void register(ModContainer container) {container.registerConfig(ModConfig.Type.COMMON,SPEC,"ydcore.toml");}
    public static boolean generateStarterVillage() {return GENERATE_STARTER_VILLAGE.get();}
    public static Identifier starterVillageBlueprint() {return Identifier.parse(STARTER_VILLAGE_BLUEPRINT.get());}
}
