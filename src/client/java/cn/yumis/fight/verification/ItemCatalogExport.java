package cn.yumis.fight.verification;

import cn.yumis.fight.bootstrap.ClientSession;
import cn.yumis.fight.modules.dungeons.api.Waystone;
import cn.yumis.fight.modules.economy.api.LootJarItem;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.GemExtraction;
import cn.yumis.fight.modules.equipment.api.Gemstones;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.NamedEquipment;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;

/** Opt-in snapshot of real client tooltips for the separately hosted item catalog. */
final class ItemCatalogExport {
    private static final String OTHER=ItemData.NS+":unclassified";
    private static final Set<String> INTERNAL=Set.of("crafting_material","loot_jar","treasure_chest","affix_gem","set_stone","waystone");
    private final Minecraft mc;
    private final Map<String,JsonObject> items=new LinkedHashMap<>();
    private final Set<String> represented=new LinkedHashSet<>();
    private final JsonArray categories=new JsonArray();
    private final JsonArray errors=new JsonArray();

    private ItemCatalogExport(Minecraft mc){this.mc=mc;}

    static void write(Minecraft mc,Path directory)throws Exception{
        if(mc.player==null||mc.level==null||!ClientSession.INSTANCE.ready)throw new IllegalStateException("请等待隔离客户端和规则同步完成");
        if(!"zh_cn".equals(mc.getLanguageManager().getSelected()))throw new IllegalStateException("物品采集需要简体中文客户端");
        if(mc.options.advancedItemTooltips||VerificationDriver.tooltipShift||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_LEFT_SHIFT)||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_RIGHT_SHIFT))throw new IllegalStateException("请关闭高级提示并松开 Shift");
        String modHash=System.getProperty("ydnf.itemCatalogModSha256","").toLowerCase(Locale.ROOT);
        if(!modHash.matches("[a-f0-9]{64}"))throw new IllegalStateException("请提供隔离客户端所用模组 JAR 的 SHA-256");
        var export=new ItemCatalogExport(mc);
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),false,mc.player.registryAccess());
        for(CreativeModeTab tab:CreativeModeTabs.allTabs()){
            var key=BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if(key==null||!key.getNamespace().equals(ItemData.NS))continue;
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),false,mc.player.registryAccess()));
            var category=new JsonObject();category.addProperty("id",key.toString());category.addProperty("name",tab.getDisplayName().getString());export.categories.add(category);
            for(ItemStack stack:tab.getDisplayItems()){
                if(stack.isEmpty()||!ItemData.own(stack))continue;
                if(ItemData.id(stack).equals("waystone")&&Waystone.read(stack)==null){export.waystones(key.toString());continue;}
                export.add(stack,key.toString());
            }
        }
        var other=new JsonObject();other.addProperty("id",OTHER);other.addProperty("name","未分类");export.categories.add(other);
        for(Item item:BuiltInRegistries.ITEM){
            var id=BuiltInRegistries.ITEM.getKey(item);
            if(id==null||!id.getNamespace().equals(ItemData.NS)||export.represented.contains(id.toString())||INTERNAL.contains(id.getPath())||EquipmentItems.GEAR_IDS.contains(id.getPath()))continue;
            export.add(item.getDefaultInstance(),OTHER);
        }
        export.derivedVariants();
        JsonObject out=new JsonObject();out.addProperty("schema",1);out.addProperty("modSha256",modHash);out.add("categories",export.categories);
        JsonArray records=new JsonArray();export.items.values().forEach(records::add);out.add("items",records);out.add("errors",export.errors);
        Files.createDirectories(directory);Path target=directory.resolve("item-catalog-raw.json"),pending=directory.resolve("item-catalog-raw.next");
        Files.writeString(pending,JSON.toJson(out),StandardCharsets.UTF_8);Files.move(pending,target,StandardCopyOption.REPLACE_EXISTING);
    }

    private void waystones(String category){
        for(Rarity rarity:List.of(Rarity.COMMON,Rarity.RARE,Rarity.ARTIFACT,Rarity.LEGENDARY)){
            try{add(Waystone.generate(1,80,rarity,new Random(0)).item(),category);}
            catch(RuntimeException failure){error("waystone/"+rarity.name(),failure);}
        }
    }

    private void derivedVariants(){
        // Both extraction recipes use existing, already registered equipment definitions.
        for(String template:List.of("named/crown","named/clown_hat")){
            try{
                var gear=NamedEquipment.create(template,new Random(0));var gem=GemExtraction.create(gear);
                add(EquipmentItems.payload("affix_gem",gem,gem.name),OTHER);
            }catch(RuntimeException failure){error("affix_gem/"+template,failure);}
        }
        for(var set:EquipmentDefinitions.current().sets.values()){
            try{
                var payload=Map.of("set",set.id(),"level",50,"group",set.group());
                add(EquipmentItems.payload("set_stone",payload,"套装石 · "+set.name()),OTHER);
            }catch(RuntimeException failure){error("set_stone/"+set.id(),failure);}
        }
    }

    private void add(ItemStack stack,String category){
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        try{
            String variant=variant(stack);String identity=id+(char)0+variant;
            var model=stack.get(DataComponents.ITEM_MODEL);if(model==null)throw new IllegalStateException("缺少物品模型");
            JsonObject record=new JsonObject();record.addProperty("id",id);record.addProperty("variant",variant);record.addProperty("model",model.toString());
            JsonArray memberships=new JsonArray();memberships.add(category);record.add("categories",memberships);
            String material=EquipmentItems.material(stack);var materialDefinition=MaterialCatalog.current().materials.get(material);
            JsonObject selectors=new JsonObject();selectors.addProperty("material",materialDefinition==null?material:materialDefinition.texture());selectors.addProperty("style",style(stack));
            var custom=stack.get(DataComponents.CUSTOM_MODEL_DATA);selectors.add("customModelData",JSON.toJsonTree(custom==null?List.of():custom.strings()));record.add("selectors",selectors);
            try{
                var lines=Screen.getTooltipFromItem(mc,stack).stream().map(component->component.getString()).toList();
                if(lines.isEmpty()||lines.getFirst().isBlank())throw new IllegalStateException("缺少物品名称");
                String name=lines.getFirst();
                if(name.startsWith("item."+ItemData.NS+".")||name.startsWith("block."+ItemData.NS+"."))throw new IllegalStateException("物品缺少中文名称");
                record.addProperty("name",name);record.addProperty("description",String.join("\n",lines.subList(1,lines.size())));
            }catch(RuntimeException failure){record.addProperty("tooltipError",String.valueOf(failure.getMessage()));}
            JsonObject existing=items.get(identity);
            if(existing!=null){
                if(!existing.get("model").equals(record.get("model"))||!existing.get("selectors").equals(record.get("selectors")))existing.addProperty("modelError","同一物品变体出现不同模型");
                if(!existing.has("tooltipError")&&!record.has("tooltipError")&&(!existing.get("name").equals(record.get("name"))||!existing.get("description").equals(record.get("description"))))existing.addProperty("tooltipError","同一物品变体出现不同名称或提示");
                if(record.has("tooltipError"))existing.add("tooltipError",record.get("tooltipError"));
                JsonArray previous=existing.getAsJsonArray("categories");boolean present=false;for(var value:previous)if(value.getAsString().equals(category))present=true;if(!present)previous.add(category);
                return;
            }
            items.put(identity,record);represented.add(id);
        }catch(RuntimeException failure){error(id,failure);}
    }

    private static String variant(ItemStack stack){
        String path=ItemData.id(stack),material=EquipmentItems.material(stack);
        if(!material.isEmpty())return "material/"+material;
        if(path.equals("loot_jar")){String definition=LootJarItem.definition(stack);if(definition.isEmpty())throw new IllegalArgumentException("罐子缺少定义");return "jar/"+definition;}
        if(path.equals("waystone")){var stone=Waystone.read(stack);if(stone==null)throw new IllegalArgumentException("引路石缺少实例");return "waystone/"+stone.rarity.name().toLowerCase(Locale.ROOT);}
        if(path.equals("set_stone")){var payload=JsonParser.parseString(EquipmentItems.payload(stack)).getAsJsonObject();return "set/"+payload.get("set").getAsString();}
        if(path.equals("affix_gem")){var gem=Gemstones.read(stack);return "affix/"+gem.affixes.getFirst().id;}
        var gear=EquipmentItems.gear(stack);
        if(gear!=null)return "gear/"+String.join("/",gear.template,gear.slot.name(),gear.rarity.name(),gear.armorType.name(),gear.jewelryType.name(),EquipmentTemplates.appearance(gear));
        return "";
    }

    private static String style(ItemStack stack){
        String forced=EquipmentItems.metadata(stack,ItemData.NS+"_appearance");if(!forced.isEmpty())return forced;
        if(ItemData.id(stack).equals("affix_gem")){
            var gem=Gemstones.read(stack);for(var affix:gem.affixes){if(affix.id.equals("unique/gem_crown"))return "crown_gem";if(affix.id.equals("unique/gem_clown"))return "clown_gem";}
        }
        var gear=EquipmentItems.view(stack);return gear==null?ItemData.id(stack)+"_10_base":gear.appearance();
    }

    private void error(String id,RuntimeException failure){JsonObject problem=new JsonObject();problem.addProperty("item",id);problem.addProperty("message",String.valueOf(failure.getMessage()));errors.add(problem);}
}
