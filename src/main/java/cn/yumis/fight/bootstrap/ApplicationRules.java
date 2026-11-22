package cn.yumis.fight.bootstrap;

import cn.yumis.fight.architecture.RuleStore;
import cn.yumis.fight.modules.equipment.api.EquipmentLayout;
import cn.yumis.fight.modules.attributes.api.AttributeNames;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.economy.api.JarDefinitions;
import cn.yumis.fight.modules.arkguard.api.ArkRules;
import com.google.gson.JsonObject;
import java.util.Objects;

/** Application composition of module rules; publishes one complete validated snapshot. */
public final class ApplicationRules {
    public static final int DATA_VERSION=19;
    public static final class Bundle {
        private final MaterialCatalog materials;
        private final DungeonCatalog dungeons;
        private final AttributeNames names;
        private final EquipmentDefinitions equipment;
        private final AffixDefinitions affixes;
        private final EquipmentLayout layout;
        private final IndustrialRules industry;
        private final cn.yumis.fight.modules.skills.api.SkillBalanceRules skills;
        private final cn.yumis.fight.modules.economy.api.ChestDefinitions chests;
        private final JarDefinitions jars;
        private final cn.yumis.fight.modules.economy.api.ShopDefinitions shops;
        private final cn.yumis.fight.modules.npc.api.NpcDefinitions npcs;
        private final cn.yumis.fight.modules.quests.api.QuestDefinitions quests;
        private final ArkRules arkWar;
        private final cn.yumis.fight.modules.dungeons.api.RitualBossCatalog ritualBosses;
        private final cn.yumis.fight.modules.talents.api.TalentCatalog talents;
        public cn.yumis.fight.modules.dungeons.api.RitualBossCatalog ritualBosses(){return ritualBosses;}
        public cn.yumis.fight.modules.talents.api.TalentCatalog talents(){return talents;}
        public ArkRules arkWar(){return arkWar;}
        public cn.yumis.fight.modules.economy.api.ShopDefinitions shops(){return shops;}
        public cn.yumis.fight.modules.npc.api.NpcDefinitions npcs(){return npcs;}
        public cn.yumis.fight.modules.quests.api.QuestDefinitions quests(){return quests;}
        public cn.yumis.fight.modules.economy.api.ChestDefinitions chests(){return chests;}
        public JarDefinitions jars(){return jars;}
        private Bundle(MaterialCatalog materials,DungeonCatalog dungeons,AttributeNames names,EquipmentDefinitions equipment,AffixDefinitions affixes,EquipmentLayout layout,IndustrialRules industry,cn.yumis.fight.modules.skills.api.SkillBalanceRules skills,cn.yumis.fight.modules.economy.api.ChestDefinitions chests,JarDefinitions jars,cn.yumis.fight.modules.economy.api.ShopDefinitions shops,cn.yumis.fight.modules.npc.api.NpcDefinitions npcs,cn.yumis.fight.modules.quests.api.QuestDefinitions quests,ArkRules arkWar,cn.yumis.fight.modules.dungeons.api.RitualBossCatalog ritualBosses,cn.yumis.fight.modules.talents.api.TalentCatalog talents){
            this.materials=Objects.requireNonNull(materials);this.dungeons=Objects.requireNonNull(dungeons);this.names=Objects.requireNonNull(names);
            this.equipment=Objects.requireNonNull(equipment);this.affixes=Objects.requireNonNull(affixes);this.layout=Objects.requireNonNull(layout);this.industry=Objects.requireNonNull(industry);
            this.skills=Objects.requireNonNull(skills);
            this.chests=Objects.requireNonNull(chests);
            this.jars=Objects.requireNonNull(jars);this.shops=Objects.requireNonNull(shops);this.npcs=Objects.requireNonNull(npcs);
            this.quests=Objects.requireNonNull(quests);
            this.arkWar=Objects.requireNonNull(arkWar);
            this.ritualBosses=Objects.requireNonNull(ritualBosses);this.talents=Objects.requireNonNull(talents);
        }
        public MaterialCatalog materials(){return materials;}
        public DungeonCatalog dungeons(){return dungeons;}
        public AttributeNames names(){return names;}
        public EquipmentDefinitions equipment(){return equipment;}
        public AffixDefinitions affixes(){return affixes;}
        public EquipmentLayout layout(){return layout;}
        public IndustrialRules industry(){return industry;}
        public cn.yumis.fight.modules.skills.api.SkillBalanceRules skills(){return skills;}
        public static Bundle validated(MaterialCatalog materials,DungeonCatalog dungeons,AttributeNames names,EquipmentDefinitions equipment,AffixDefinitions affixes,EquipmentLayout layout,IndustrialRules industry,cn.yumis.fight.modules.skills.api.SkillBalanceRules skills,cn.yumis.fight.modules.economy.api.ChestDefinitions chests,JarDefinitions jars,cn.yumis.fight.modules.economy.api.ShopDefinitions shops,cn.yumis.fight.modules.npc.api.NpcDefinitions npcs,cn.yumis.fight.modules.quests.api.QuestDefinitions quests,ArkRules arkWar,cn.yumis.fight.modules.dungeons.api.RitualBossCatalog ritualBosses,cn.yumis.fight.modules.talents.api.TalentCatalog talents){
            var result=new Bundle(MaterialCatalog.parse(ApplicationJson.JSON.toJson(materials)),DungeonCatalog.parse(ApplicationJson.JSON.toJson(dungeons)),
                AttributeNames.parse(ApplicationJson.JSON.toJson(names)),EquipmentDefinitions.parse(ApplicationJson.JSON.toJson(equipment)),AffixDefinitions.parse(ApplicationJson.JSON.toJson(affixes)),
                EquipmentLayout.parse(ApplicationJson.JSON.toJson(layout)),IndustrialRules.parse(ApplicationJson.JSON.toJson(industry)),cn.yumis.fight.modules.skills.api.SkillBalanceRules.parse(ApplicationJson.JSON.toJson(skills)),cn.yumis.fight.modules.economy.api.ChestDefinitions.parse(ApplicationJson.JSON.toJson(chests)),JarDefinitions.parse(ApplicationJson.JSON.toJson(jars)),cn.yumis.fight.modules.economy.api.ShopDefinitions.parse(ApplicationJson.JSON.toJson(shops)),cn.yumis.fight.modules.npc.api.NpcDefinitions.parse(ApplicationJson.JSON.toJson(npcs)),cn.yumis.fight.modules.quests.api.QuestDefinitions.parse(ApplicationJson.JSON.toJson(quests)),ArkRules.parse(ApplicationJson.JSON.toJson(arkWar)),cn.yumis.fight.modules.dungeons.api.RitualBossCatalog.parse(ApplicationJson.JSON.toJson(ritualBosses)),cn.yumis.fight.modules.talents.api.TalentCatalog.parse(ApplicationJson.JSON.toJson(talents)));
            for(var dungeon:result.dungeons.dungeons)for(String material:dungeon.materials())if(!result.materials.materials.containsKey(material))throw new IllegalArgumentException("Unknown dungeon material: "+material);
            for(var boss:result.ritualBosses.ordered()){
                if(cn.yumis.fight.modules.economy.api.CurrencyRules.find(boss.token())==null)throw new IllegalArgumentException("魔王祭坛信物未登记通货："+boss.token());
                if(cn.yumis.fight.modules.economy.api.CurrencyRules.find(boss.material())==null)throw new IllegalArgumentException("魔王祭坛专材未登记通货："+boss.material());
            }
            result.shops.validateItems();
            for(var definition:result.npcs.npcs.values())for(var action:definition.actions())if(action.type().equals("shop"))result.shops.require(action.value());
            cn.yumis.fight.modules.skills.api.SkillBalance.validate(result.skills);
            for(var unit:result.arkWar.units)if(!result.npcs.skins.containsKey(unit.skin()))throw new IllegalArgumentException("方舟临时外观不存在："+unit.id());
            return result;
        }
    }
    private static final RuleStore<Bundle> STORE=new RuleStore<>(new Bundle(MaterialCatalog.current(),DungeonCatalog.current(),AttributeNames.current(),EquipmentDefinitions.current(),AffixDefinitions.current(),EquipmentLayout.current(),IndustrialRules.current(),cn.yumis.fight.modules.skills.api.SkillBalanceRules.defaults(),cn.yumis.fight.modules.economy.api.ChestDefinitions.defaults(),JarDefinitions.defaults(),cn.yumis.fight.modules.economy.api.ShopDefinitions.defaults(),cn.yumis.fight.modules.npc.api.NpcDefinitions.defaults(),cn.yumis.fight.modules.quests.api.QuestDefinitions.defaults(),ArkRules.defaults(),cn.yumis.fight.modules.dungeons.api.RitualBossCatalog.defaults(),cn.yumis.fight.modules.talents.api.TalentCatalog.defaults()),Objects::requireNonNull);
    private ApplicationRules(){}
    public static void install(){
        MaterialCatalog.source(()->current().materials());DungeonCatalog.source(()->current().dungeons());AttributeNames.source(()->current().names());
        EquipmentDefinitions.source(()->current().equipment());AffixDefinitions.source(()->current().affixes());EquipmentLayout.source(()->current().layout());IndustrialRules.source(()->current().industry());
        cn.yumis.fight.modules.skills.api.SkillBalanceRules.source(()->current().skills());
        cn.yumis.fight.modules.economy.api.ChestDefinitions.source(()->current().chests());
        JarDefinitions.source(()->current().jars());cn.yumis.fight.modules.economy.api.ShopDefinitions.source(()->current().shops());cn.yumis.fight.modules.npc.api.NpcDefinitions.source(()->current().npcs());
        cn.yumis.fight.modules.quests.api.QuestDefinitions.source(()->current().quests());
        ArkRules.source(()->current().arkWar());
        cn.yumis.fight.modules.dungeons.api.RitualBossCatalog.source(()->current().ritualBosses());
        cn.yumis.fight.modules.talents.api.TalentCatalog.source(()->current().talents());
        cn.yumis.fight.modules.equipment.api.EquipmentAttributes.characterEffects(cn.yumis.fight.modules.talents.api.TalentAttributes::apply);
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
            packet.has("industrialRules")?IndustrialRules.parse(packet.get("industrialRules").toString()):before.industry(),
            cn.yumis.fight.modules.skills.api.SkillBalanceRules.parse(packet.get("skillBalanceRules").toString()),cn.yumis.fight.modules.economy.api.ChestDefinitions.parse(packet.get("chestDefinitions").toString()),JarDefinitions.parse(packet.get("jarDefinitions").toString()),cn.yumis.fight.modules.economy.api.ShopDefinitions.parse(packet.get("shopDefinitions").toString()),cn.yumis.fight.modules.npc.api.NpcDefinitions.parse(packet.get("npcDefinitions").toString()),packet.has("questDefinitions")?cn.yumis.fight.modules.quests.api.QuestDefinitions.parse(packet.get("questDefinitions").toString()):before.quests(),ArkRules.parse(packet.get("arkWarRules").toString()),
            packet.has("ritualBosses")?cn.yumis.fight.modules.dungeons.api.RitualBossCatalog.parse(packet.get("ritualBosses").toString()):before.ritualBosses(),packet.has("talents")?cn.yumis.fight.modules.talents.api.TalentCatalog.parse(packet.get("talents").toString()):before.talents()));
    }
}
