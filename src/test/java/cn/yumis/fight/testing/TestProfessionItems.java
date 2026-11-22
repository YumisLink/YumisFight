package cn.yumis.fight.testing;
import cn.yumis.fight.bootstrap.ProfessionItemDisplay;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import cn.yumis.fight.modules.professions.api.FishSpecies;
import cn.yumis.fight.modules.professions.api.HerbSpecies;
import cn.yumis.fight.modules.professions.platform.AlchemyContent;
import cn.yumis.fight.modules.professions.platform.HerbContent;
import cn.yumis.fight.modules.professions.platform.ProfessionContent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 副职业产出物品的「AYD-副职业」创造栏归属：清单完整、顺序稳定、非副职业物品不混入。 */
class TestProfessionItems {
    private Set<String> everyProfessionSource(){
        var expected=new LinkedHashSet<String>();
        expected.addAll(ProfessionContent.MATERIAL_IDS);
        expected.addAll(ProfessionContent.CONSUMABLE_IDS);
        expected.addAll(List.of(HerbContent.SEED_ID,AlchemyContent.ITEM_ID,AlchemyContent.WAND_ID));
        List.of(FishSpecies.COMMON,FishSpecies.RARE,FishSpecies.ARTIFACT_ALL,FishSpecies.LEGENDARY_ALL,FishSpecies.EPIC_ALL,FishSpecies.MYTHIC_ALL).forEach(expected::addAll);
        List.of(HerbSpecies.COMMON,HerbSpecies.RARE,HerbSpecies.ARTIFACT,HerbSpecies.LEGENDARY,HerbSpecies.EPIC,HerbSpecies.MYTHIC).forEach(expected::addAll);
        expected.addAll(List.of(
            ProfessionContent.ROD_ID,ProfessionContent.LUCKY_ROD_ID,ProfessionContent.GRAPHITE_ROD_ID,
            ProfessionContent.TIDAL_ROD_ID,ProfessionContent.STYX_ROD_ID,ProfessionContent.FARER_ROD_ID,
            ProfessionContent.SICKLE_ID,ProfessionContent.RARE_SICKLE_ID,ProfessionContent.ARTIFACT_SICKLE_ID,
            ProfessionContent.LEGENDARY_SICKLE_ID,ProfessionContent.EPIC_SICKLE_ID,ProfessionContent.MYTHIC_SICKLE_ID));
        expected.addAll(AlchemyPotions.tiers().keySet());
        expected.addAll(List.of("emerald_bottle","amethyst_bottle","diamond_bottle"));
        return expected;
    }

    @Test void catalogCoversEveryProfessionSource(){
        var ids=ProfessionItemDisplay.ids();
        assertFalse(ids.isEmpty());
        assertEquals(ids.size(),Set.copyOf(ids).size(),"清单不得重复");
        assertEquals(everyProfessionSource(),Set.copyOf(ids),"副职业清单与登记来源不一致");
        for(String id:ids)assertNotNull(ProfessionItemDisplay.rarity(id),"未登记稀有度："+id);
    }

    @Test void nonProfessionItemsStayOut(){
        assertNull(ProfessionItemDisplay.rarity("katana"));
        assertNull(ProfessionItemDisplay.rarity("chaos_stone"));
        assertNull(ProfessionItemDisplay.rarity("crafting_material"));
        assertNotNull(ProfessionItemDisplay.rarity(HerbContent.SEED_ID));
        assertNotNull(ProfessionItemDisplay.rarity("potion_life"));
        assertNotNull(ProfessionItemDisplay.rarity(ProfessionContent.SICKLE_ID));
    }

    @Test void displayOrderKeepsMaterialsFirst(){
        assertEquals(ProfessionContent.MATERIAL_IDS,ProfessionItemDisplay.ids().subList(0,ProfessionContent.MATERIAL_IDS.size()));
    }
}
