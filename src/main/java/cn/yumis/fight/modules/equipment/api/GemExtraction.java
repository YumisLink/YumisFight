package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.*;
import java.util.*;
/** Approved deterministic recipes; an absent recipe means the equipment is not extractable. */
public final class GemExtraction {
    public record Recipe(String template,String name,String affix,double value){}
    private static final Map<String,Recipe> RECIPES=Map.of(
        "named/crown",new Recipe("named/crown","皇冠之石","unique/gem_crown",.1),
        "named/clown_hat",new Recipe("named/clown_hat","小丑之石","unique/gem_clown",15));
    private GemExtraction(){}
    public static boolean available(Gear gear){return !gear.corrupted&&RECIPES.containsKey(gear.template);}
    public static Gem create(Gear gear){
        Recipe recipe=RECIPES.get(gear.template);if(gear.corrupted||recipe==null)throw new IllegalArgumentException("该装备尚未开放词条提炼，或已经腐化");
        Gem gem=new Gem();gem.name=recipe.name;gem.level=gear.level;gem.group="armor";
        var d=AffixDefinitions.current().affixes.get(recipe.affix);Affix a=new Affix();a.id=recipe.affix;a.operation=d.operation();a.stat=d.stat().name();a.value=a.baseline=recipe.value;a.fixed=true;
        AffixResolver.anchor(a,UUID.randomUUID().toString(),d,d.revision());gem.affixes.add(a);return gem;
    }
}
