package cn.yumis.fight.bootstrap;

import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import cn.yumis.fight.modules.professions.api.FishSpecies;
import cn.yumis.fight.modules.professions.api.HerbSpecies;
import cn.yumis.fight.modules.professions.platform.AlchemyContent;
import cn.yumis.fight.modules.professions.platform.HerbContent;
import cn.yumis.fight.modules.professions.platform.ProfessionContent;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Joins profession content to the existing item rarity, name and tooltip presentation. */
public final class ProfessionItemDisplay {
    private static final Map<String,Rarity> GRADES=grades();
    private ProfessionItemDisplay(){}
    private static Map<String,Rarity> grades(){
        var result=new LinkedHashMap<String,Rarity>();
        for(String id:ProfessionContent.MATERIAL_IDS)result.put(id,Rarity.COMMON);
        for(String id:ProfessionContent.CONSUMABLE_IDS)result.put(id,Rarity.COMMON);
        for(String id:List.of(HerbContent.SEED_ID,AlchemyContent.ITEM_ID,AlchemyContent.WAND_ID))result.put(id,Rarity.COMMON);
        var fish=List.of(FishSpecies.COMMON,FishSpecies.RARE,FishSpecies.ARTIFACT_ALL,FishSpecies.LEGENDARY_ALL,FishSpecies.EPIC_ALL,FishSpecies.MYTHIC_ALL);
        var herbs=List.of(HerbSpecies.COMMON,HerbSpecies.RARE,HerbSpecies.ARTIFACT,HerbSpecies.LEGENDARY,HerbSpecies.EPIC,HerbSpecies.MYTHIC);
        var rods=List.of(ProfessionContent.ROD_ID,ProfessionContent.LUCKY_ROD_ID,ProfessionContent.GRAPHITE_ROD_ID,ProfessionContent.TIDAL_ROD_ID,ProfessionContent.STYX_ROD_ID,ProfessionContent.FARER_ROD_ID);
        var sickles=List.of(ProfessionContent.SICKLE_ID,ProfessionContent.RARE_SICKLE_ID,ProfessionContent.ARTIFACT_SICKLE_ID,ProfessionContent.LEGENDARY_SICKLE_ID,ProfessionContent.EPIC_SICKLE_ID,ProfessionContent.MYTHIC_SICKLE_ID);
        for(var rarity:Rarity.values()){
            for(String id:fish.get(rarity.ordinal()))result.put(id,rarity);
            for(String id:herbs.get(rarity.ordinal()))result.put(id,rarity);
            result.put(rods.get(rarity.ordinal()),rarity);result.put(sickles.get(rarity.ordinal()),rarity);
        }
        AlchemyPotions.tiers().forEach((id,tier)->{
            var rarity=Rarity.valueOf(tier.name());result.put(id,rarity);
            String bottle=AlchemyPotions.bottleFor(id);if(!bottle.equals(AlchemyPotions.BOTTLE_GLASS))result.put(bottle,rarity);
        });
        return Collections.unmodifiableMap(result);
    }
    public static Rarity rarity(String id){return GRADES.get(id);}
    public static Rarity rarity(ItemStack stack){return stack.isEmpty()||!ItemData.own(stack)?null:rarity(ItemData.id(stack));}
    public static Component name(String translation,Rarity rarity){return ModText.decorate(Component.translatable(translation)).withStyle(style->style.withColor(rarity.color()).withItalic(false));}
    public static void names(ModifyDefaultComponentsEvent event){
        ItemRegistry.ALL.forEach((id,holder)->{
            var rarity=rarity(id);if(rarity==null)return;
            event.modify(holder.get(),(components,context,item)->components.set(DataComponents.ITEM_NAME,name(item.getDescriptionId(),rarity)));
        });
    }
    public static void tooltip(ItemTooltipEvent event){
        var rarity=rarity(event.getItemStack());if(rarity==null)return;
        event.getToolTip().add(ModText.decorate(Component.literal("稀有度 · "+rarity.title()).withColor(rarity.color())));
    }
}
