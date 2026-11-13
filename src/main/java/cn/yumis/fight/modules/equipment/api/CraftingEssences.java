package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.affixes.api.*;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Equipment-owned deterministic essence recipes and their application qualifications. */
public final class CraftingEssences {
    public record Definition(String id,String name,String affix,List<Slot> slots,String material,boolean paper,String family,boolean dropOnly){}
    public record Recipe(Definition essence,Weapon weapon){}
    private static final Map<Weapon,String> FAMILIES=Map.ofEntries(
        Map.entry(Weapon.KATANA,"GHOST"),Map.entry(Weapon.LIGHTSABER,"SWORD"),Map.entry(Weapon.BLUNT,"SWORD"),
        Map.entry(Weapon.GREATSWORD,"BLOOD"),Map.entry(Weapon.SHORTSWORD,"WAVE"),Map.entry(Weapon.STAFF,"ELEMENT"),
        Map.entry(Weapon.WAND,"SUMMON"),Map.entry(Weapon.CLUB,"SUN_MOON"),Map.entry(Weapon.WARGLAIVE,"HAVOC"));
    private static final List<Definition> ALL=List.of(EquipmentJson.JSON.fromJson(content().get("essences"),Definition[].class));
    private CraftingEssences(){}
    static JsonObject content(){
        try(var input=CraftingEssences.class.getResourceAsStream("/data/yumis_dungeon_and_fight/equipment/currency_crafting.json")){
            if(input==null)throw new IllegalStateException("Missing equipment currency catalog");
            return JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    public static List<Definition> all(){return ALL;}
    public static Definition find(String id){return ALL.stream().filter(d->d.id().equals(id)).findFirst().orElse(null);}
    public static List<Recipe> recipes(){
        var result=new ArrayList<Recipe>();
        for(var d:ALL)if(!d.dropOnly()){if(d.family().isEmpty())result.add(new Recipe(d,null));else for(var weapon:Weapon.standard())if(d.family().equals(FAMILIES.get(weapon)))result.add(new Recipe(d,weapon));}
        return List.copyOf(result);
    }
    public static boolean protectedMaterial(Gear g){return g.creativeTemplate||g.corrupted||g.sockets>0||!g.gems.isEmpty()||g.affixes.stream().anyMatch(a->a.locked||a.crafted);}
    public static Recipe match(List<ItemStack> grid){
        if(grid.size()!=9||!ItemData.own(grid.get(4))||!ItemData.id(grid.get(4)).equals("exalted_stone"))return null;
        for(var recipe:recipes()){
            var d=recipe.essence();boolean matches=true;
            for(int i=0;i<9&&matches;i++){
                ItemStack stack=grid.get(i);
                if(i==4)continue;
                if(i==1||i==3||i==5||i==7){
                    if(recipe.weapon()!=null){Gear gear=EquipmentItems.gear(stack);matches=gear!=null&&gear.slot==Slot.WEAPON&&gear.weapon==recipe.weapon()&&!protectedMaterial(gear);}
                    else matches=d.material().equals("#wool")?stack.is(ItemTags.WOOL):BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("minecraft:"+d.material());
                }else if(i==0&&d.paper())matches=stack.is(net.minecraft.world.item.Items.PAPER);
                else matches=stack.isEmpty();
            }
            if(matches)return recipe;
        }
        return null;
    }
    public static ItemStack ingredient(Recipe recipe){
        if(recipe.weapon()!=null)return cn.yumis.fight.platform.ItemRegistry.item(recipe.weapon().name().toLowerCase(Locale.ROOT),1);
        String material=recipe.essence().material();return new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(material.equals("#wool")?"white_wool":material)));
    }
    public static Gear apply(Gear original,Definition essence,Random random){
        Gear g=EquipmentResolution.resolve(original);
        if(g.corrupted||g.rarity!=Rarity.ARTIFACT)throw new IllegalArgumentException("精华仅用于未腐化的神器装备");
        var d=AffixDefinitions.current().affixes.get(essence.affix());
        if(d==null||!d.active()||!essence.slots().contains(g.slot))throw new IllegalArgumentException("精华部位或效果未开放");
        if(!essence.family().isEmpty()&&!EquipmentAffixes.skillFamilyAllowed(g,essence.family()))throw new IllegalArgumentException("武器不符合该技能系别资格");
        if(g.affixes.stream().filter(a->!a.base).anyMatch(a->d.operation().equals("SKILL_FAMILY")?a.operation.equals("SKILL_FAMILY"):sameEffect(a,d)))throw new IllegalArgumentException("装备已有同效果词条，不能重复工艺");
        if(d.operation().equals("PERCENT")&&EquipmentAffixes.base(g,d.stat())<=0)throw new IllegalArgumentException("装备没有对应的基础属性");
        Affix a=new Affix();a.id=d.id();a.operation=d.operation();a.stat=d.stat()==null?"":d.stat().name();a.element=d.element();a.crafted=true;a.roll=1;
        if(a.operation.equals("SKILL_FAMILY")){a.family=essence.family();a.rank=2;a.fixed=true;}
        else if(a.operation.equals("EXTRA")){a.value=a.baseline=.1*EquipmentRules.budget(g.level);a.maximum=a.baselineMaximum=.2*EquipmentRules.budget(g.level);}
        else if(a.stat.equals("ATTACK_SPEED")){a.value=AttackSpeedRoll.sample(g.level,1);a.baseline=AttackSpeedRoll.sample(g.level,.8);a.baselineMaximum=AttackSpeedRoll.sample(g.level,1.2);}
        else if(d.minimum()!=null){a.value=(d.minimum()+(d.maximum()-d.minimum())*(essence.dropOnly()?random.nextDouble():.5))*d.scale();a.baseline=a.value;}
        else a.value=a.baseline=EquipmentAffixes.baseline(g,a);
        AffixResolver.anchor(a,g.instance+"/"+UUID.randomUUID(),d,d.revision(),d.effectiveScale(g.level));
        g.affixes.add(a);g.rarity=Rarity.LEGENDARY;g.revision++;
        return EquipmentResolution.initialize(g);
    }
    private static boolean sameEffect(Affix a,AffixDefinition d){return a.operation.equals(d.operation())&&Objects.equals(a.stat,d.stat()==null?"":d.stat().name())&&a.element==d.element();}
}
