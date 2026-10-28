package cn.yumis.fight.bootstrap;

import cn.yumis.fight.architecture.RuleStore;
import cn.yumis.fight.modules.equipment.api.EquipmentLayout;
import cn.yumis.fight.modules.attributes.api.AttributeNames;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import com.google.gson.JsonObject;
import java.util.Objects;

/** Application composition of module rules; publishes one complete validated snapshot. */
public final class ApplicationRules {
    public static final int DATA_VERSION=8;
    public static final class Bundle {
        private final MaterialCatalog materials;
        private final DungeonCatalog dungeons;
        private final AttributeNames names;
        private final EquipmentDefinitions equipment;
        private final AffixDefinitions affixes;
        private final EquipmentLayout layout;
        private final IndustrialRules industry;
        private Bundle(MaterialCatalog materials,DungeonCatalog dungeons,AttributeNames names,EquipmentDefinitions equipment,AffixDefinitions affixes,EquipmentLayout layout,IndustrialRules industry){
            this.materials=Objects.requireNonNull(materials);this.dungeons=Objects.requireNonNull(dungeons);this.names=Objects.requireNonNull(names);
            this.equipment=Objects.requireNonNull(equipment);this.affixes=Objects.requireNonNull(affixes);this.layout=Objects.requireNonNull(layout);this.industry=Objects.requireNonNull(industry);
        }
        public MaterialCatalog materials(){return materials;}
        public DungeonCatalog dungeons(){return dungeons;}
        public AttributeNames names(){return names;}
        public EquipmentDefinitions equipment(){return equipment;}
        public AffixDefinitions affixes(){return affixes;}
        public EquipmentLayout layout(){return layout;}
        public IndustrialRules industry(){return industry;}
        public static Bundle validated(MaterialCatalog materials,DungeonCatalog dungeons,AttributeNames names,EquipmentDefinitions equipment,AffixDefinitions affixes,EquipmentLayout layout,IndustrialRules industry){
            var result=new Bundle(MaterialCatalog.parse(ApplicationJson.JSON.toJson(materials)),DungeonCatalog.parse(ApplicationJson.JSON.toJson(dungeons)),
                AttributeNames.parse(ApplicationJson.JSON.toJson(names)),EquipmentDefinitions.parse(ApplicationJson.JSON.toJson(equipment)),AffixDefinitions.parse(ApplicationJson.JSON.toJson(affixes)),
                EquipmentLayout.parse(ApplicationJson.JSON.toJson(layout)),IndustrialRules.parse(ApplicationJson.JSON.toJson(industry)));
            for(var dungeon:result.dungeons.dungeons)for(String material:dungeon.materials())if(!result.materials.materials.containsKey(material))throw new IllegalArgumentException("Unknown dungeon material: "+material);
            return result;
        }
    }
    private static final RuleStore<Bundle> STORE=new RuleStore<>(new Bundle(MaterialCatalog.current(),DungeonCatalog.current(),AttributeNames.current(),EquipmentDefinitions.current(),AffixDefinitions.current(),EquipmentLayout.current(),IndustrialRules.current()),Objects::requireNonNull);
    private ApplicationRules(){}
    public static void install(){
        MaterialCatalog.source(()->current().materials());DungeonCatalog.source(()->current().dungeons());AttributeNames.source(()->current().names());
        EquipmentDefinitions.source(()->current().equipment());AffixDefinitions.source(()->current().affixes());EquipmentLayout.source(()->current().layout());IndustrialRules.source(()->current().industry());
    }
    public static Bundle current(){return STORE.snapshot().value();}
    public static void publish(Bundle candidate){STORE.publish(candidate);}
    public static void receive(JsonObject packet){
        install();var before=current();
        publish(Bundle.validated(
            packet.has("materialCatalog")?MaterialCatalog.parse(packet.get("materialCatalog").toString()):before.materials(),
            packet.has("dungeonDefinitions")?DungeonCatalog.parse(packet.get("dungeonDefinitions").toString()):before.dungeons(),
            packet.has("attributeNames")?AttributeNames.parse(packet.get("attributeNames").toString()):before.names(),
            packet.has("equipmentDefinitions")?EquipmentDefinitions.parse(packet.get("equipmentDefinitions").toString()):before.equipment(),
            packet.has("affixDefinitions")?AffixDefinitions.parse(packet.get("affixDefinitions").toString()):before.affixes(),
            packet.has("equipmentLayout")?EquipmentLayout.parse(packet.get("equipmentLayout").toString()):before.layout(),
            packet.has("industrialRules")?IndustrialRules.parse(packet.get("industrialRules").toString()):before.industry()));
    }
}
