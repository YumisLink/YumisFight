package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import cn.yumis.fight.modules.inventory.api.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Predicate;
import static cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON;

/** Personal processing rules. Random results are produced once by the owning client. */
public final class EquipmentChanges {
    public record Result(ItemStack equipment,ItemStack currency,List<ItemStack> outputs){}
    private EquipmentChanges(){}

    private record Material(String area,int slot,ItemStack item,long quantity){}
    private static final class Materials {
        private final Player player;
        private final List<Material> sources=new ArrayList<>();
        private final long[] remaining;

        Materials(Player player,List<ItemStack> bag){
            this.player=player;
            for(int i=0;i<36;i++)add("inventory",i,player.getInventory().getItem(i));
            for(var stored:MenuHooks.extra(player).categories.stock())
                sources.add(new Material(stored.kind().area(),stored.slot(),stored.item(),stored.quantity()));
            for(int i=0;i<bag.size();i++)add("bag",i,bag.get(i));
            remaining=sources.stream().mapToLong(Material::quantity).toArray();
        }
        private void add(String area,int slot,ItemStack stack){
            if(!stack.isEmpty())sources.add(new Material(area,slot,stack.copy(),stack.getCount()));
        }
        int count(Predicate<ItemStack> accepts){
            int count=0;
            for(int i=0;i<sources.size();i++)if(accepts.test(sources.get(i).item()))
                count+=(int)Math.min(Integer.MAX_VALUE-count,remaining[i]);
            return count;
        }
        boolean take(Predicate<ItemStack> accepts,int count){
            for(int i=0;i<sources.size()&&count>0;i++)if(accepts.test(sources.get(i).item())){
                int amount=(int)Math.min(remaining[i],count);
                remaining[i]-=amount;
                count-=amount;
            }
            return count==0;
        }
        JsonArray edits(){
            var edits=new JsonArray();
            for(int i=0;i<sources.size();i++){
                var source=sources.get(i);
                if(remaining[i]==source.quantity())continue;
                if(source.area().equals(CategoryBags.Kind.CURRENCY.area()))
                    edits.add(InventoryEdits.debitCurrency(source.slot(),source.quantity(),source.quantity()-remaining[i]));
                else edits.add(InventoryEdits.change(player,source.area(),source.slot(),source.item(),source.item().copyWithCount((int)remaining[i])));
            }
            return edits;
        }
    }
    private static boolean matches(MaterialCatalog.Need need,ItemStack stack){
        return need.regional()?EquipmentItems.material(stack).equals(need.id()):BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(need.id());
    }
    private static boolean fuel(ItemStack stack,boolean amplified){
        String id=ItemData.id(stack);
        return amplified?ItemData.own(stack)&&id.equals(EnhancementRules.INTENSE_AURA):stack.is(net.minecraft.world.item.Items.COAL)||stack.is(net.minecraft.world.item.Items.CHARCOAL);
    }
    private static int countMaterials(Player player,List<ItemStack> bag,Predicate<ItemStack> accepts){
        long count=MenuHooks.extra(player).categories.count(accepts);
        for(int i=0;i<36;i++){
            var stack=player.getInventory().getItem(i);
            if(!stack.isEmpty()&&accepts.test(stack))count+=stack.getCount();
        }
        for(var stack:bag)if(!stack.isEmpty()&&accepts.test(stack))count+=stack.getCount();
        return (int)Math.min(Integer.MAX_VALUE,count);
    }
    public static int materialCount(Player player,List<ItemStack> bag,MaterialCatalog.Need need){return countMaterials(player,bag,stack->matches(need,stack));}
    public static int fuelCount(Player player,List<ItemStack> bag,boolean amplified){return countMaterials(player,bag,stack->fuel(stack,amplified));}
    public static Result process(ItemStack source,ItemStack input,int socket,Random random){
        var gear=EquipmentItems.gear(source);if(gear==null||input.isEmpty())throw new IllegalArgumentException("请选择装备和通货");String id=ItemData.id(input);var currency=input.copy();currency.shrink(1);var outputs=new ArrayList<ItemStack>();
        if(gear.corrupted&&!Gemstones.currency(id)&&!id.equals(EnhancementRules.OTHERWORLD_AURA)&&!id.equals(EnhancementRules.AURA_TRANSFORM_BOX))throw new IllegalArgumentException("腐化装备仅允许继续镶嵌宝石");
        if(id.equals("set_extraction_stone")){
            if(gear.set.isEmpty())throw new IllegalArgumentException("该装备没有套装");outputs.add(EquipmentItems.payload("set_stone",Map.of("set",gear.set,"level",gear.level,"group",gear.slot.armor()?"armor":"jewelry"),"套装石 · "+EquipmentDefinitions.current().sets.get(gear.set).name()));return new Result(ItemStack.EMPTY,currency,outputs);
        }
        if(id.equals("set_stone")){
            var payload=JsonParser.parseString(EquipmentItems.payload(input)).getAsJsonObject();boolean armor=payload.get("group").getAsString().equals("armor");
            if(gear.level<payload.get("level").getAsInt()||armor&&!gear.slot.armor()||!armor&&!gear.slot.jewelry())throw new IllegalArgumentException("套装石部位或来源等级不符");
            String set=payload.get("set").getAsString();var definition=EquipmentDefinitions.current().sets.get(set);if(definition==null||!definition.group().equals(payload.get("group").getAsString()))throw new IllegalArgumentException("套装定义或分类不符");gear.set=set;gear.revision++;
        }else if(Gemstones.currency(id))gear=EquipmentRules.socket(gear,Gemstones.read(input),socket>=0?socket:gear.gems.size()<gear.sockets?gear.gems.size():0);
        else if(id.equals("affix_extraction_stone")){var gem=GemExtraction.create(gear);outputs.add(EquipmentItems.payload("affix_gem",gem,gem.name));return new Result(ItemStack.EMPTY,currency,outputs);}
        else gear=EquipmentRules.process(gear,id,random);
        var updated=source.copy();EquipmentItems.write(updated,gear);return new Result(updated,currency,outputs);
    }
    public static void prepareCraft(net.minecraft.world.entity.player.Player player,cn.yumis.fight.modules.character.api.Progress progress,boolean allowed,cn.yumis.fight.modules.professions.api.Profession profession,JsonObject j,Random random){
        prepareCraft(player,progress,allowed,profession,j,random,List.of());
    }
    public static void prepareCraft(net.minecraft.world.entity.player.Player player,cn.yumis.fight.modules.character.api.Progress progress,boolean allowed,cn.yumis.fight.modules.professions.api.Profession profession,JsonObject j,Random random,List<ItemStack> bag){
        if(!j.has("rulesRevision")||j.get("rulesRevision").getAsLong()!=EquipmentDefinitions.current().revision)throw new IllegalArgumentException("装备规则已更新，请刷新后重试");
        if(!(player.containerMenu instanceof FabricatorMenu))throw new IllegalArgumentException("请使用装备制作器");
        if(!allowed)throw new IllegalArgumentException("没有制作权限");
        Slot slot=Slot.valueOf(j.get("slot").getAsString());int level=j.get("level").getAsInt();if(level>progress.level)throw new IllegalArgumentException("制作等级超过角色等级");
        Weapon weapon=Weapon.valueOf(j.get("weapon").getAsString());ArmorType armor=j.has("armorType")?ArmorType.valueOf(j.get("armorType").getAsString()):ArmorType.UNTYPED;JewelryType jewelry=j.has("jewelryType")?JewelryType.valueOf(j.get("jewelryType").getAsString()):JewelryType.UNTYPED;
        if(slot.armor()&&(armor==ArmorType.UNTYPED||jewelry!=JewelryType.UNTYPED)||slot.jewelry()&&(jewelry==JewelryType.UNTYPED||armor!=ArmorType.UNTYPED)||!slot.armor()&&!slot.jewelry()&&(armor!=ArmorType.UNTYPED||jewelry!=JewelryType.UNTYPED))throw new IllegalArgumentException("请选择有效装备类型");
        var materials=new Materials(player,bag);
        String alternative=j.has("recipe")?j.get("recipe").getAsString():"";
        for(var need:MaterialCatalog.current().recipe(slot,level,alternative)){
            if(!materials.take(stack->matches(need,stack),need.count()))throw new IllegalArgumentException("材料不足："+need.id());
        }
        j.add("inventoryChanges",materials.edits());j.addProperty("menu",player.containerMenu.containerId);
        var outputs=new JsonArray();outputs.add(InventoryEdits.encode(player,EquipmentItems.gear(EquipmentRules.create(slot,weapon,Rarity.COMMON,level,armor,jewelry,random))));j.add("outputs",outputs);
        var value=EquipmentJson.copy(profession,cn.yumis.fight.modules.professions.api.Profession.class);j.add("beforeProfession",JSON.toJsonTree(value));value.award(60,false);value.grantTitle("craftsman");j.add("profession",JSON.toJsonTree(value));
    }
    public static ItemStack prepareCurrency(Player player, JsonObject request, Random random) {        if (!request.has("rulesRevision") || request.get("rulesRevision").getAsLong() != EquipmentDefinitions.current().revision) {
            throw new IllegalArgumentException("装备规则已更新，请刷新后重试");
        }
        boolean creativeCursor = request.has("creativeCursor");
        var menu = creativeCursor ? player.inventoryMenu : player.containerMenu;
        int index = request.get("slot").getAsInt();
        if (request.get("menu").getAsInt() != menu.containerId || index < 0 || index >= menu.slots.size()) {
            throw new IllegalArgumentException("加工界面已变化");
        }
        var slot = menu.getSlot(index);
        if (!SlotAccess.isWritable(slot, player)) throw new IllegalArgumentException("该栏位不能加工装备");
        var source = slot.getItem();
        var gear = EquipmentItems.gear(source);
        var selected = !creativeCursor && request.has("storageMaterial") && menu instanceof DimensionalMenu dimensional ? dimensional.selectedMaterial() : null;
        if(request.has("storageMaterial")&&selected==null)throw new IllegalArgumentException("袋内材料已变化，请重新选择");
        var cursor = selected==null?menu.getCarried():selected.item();
        if (gear == null || !gear.instance.toString().equals(request.get("equipment").getAsString())
                || gear.revision != request.get("revision").getAsInt() || !InventoryEdits.encode(player, cursor).equals(request.get("cursor"))) {
            throw new IllegalArgumentException("装备或通货已变化");
        }
        if (creativeCursor && gear.creativeTemplate) throw new IllegalArgumentException("装备正在初始化，请稍后重试");

        int socket = request.has("socket") ? request.get("socket").getAsInt() : -1;
        var result = process(source, cursor, socket, random);
        var edits = new JsonArray();
        edits.add(InventoryEdits.change(player, "menu", index, source, result.equipment()));
        // The creative picker carries its stack on the client; the server only receives the inventory edits.
        if(selected!=null){
            if(selected.kind()==CategoryBags.Kind.CURRENCY)edits.add(InventoryEdits.debitCurrency(selected.slot(),selected.quantity(),1));
            else edits.add(InventoryEdits.change(player,selected.kind().area(),selected.slot(),cursor,result.currency()));
        }else if (!creativeCursor) edits.add(InventoryEdits.change(player, "cursor", 0, cursor, result.currency()));
        request.add("inventoryChanges", edits);
        var outputs = new JsonArray();
        for (var item : result.outputs()) outputs.add(InventoryEdits.encode(player, item));
        request.add("outputs", outputs);
        return result.currency();
    }
    /** 强化/增幅的一次或连续判定：随机结果只在本人客户端产生一次，燃料与金币按实际发生次数结算。 */
    public record EnhanceOutcome(int fromLevel,int toLevel,int successes,int failures,boolean shattered,long gold,int fuel,boolean amplified){}
    public static EnhanceOutcome prepareEnhance(Player player,JsonObject request,long goldBefore,long goldRevision,Random random){
        return prepareEnhance(player,request,goldBefore,goldRevision,random,List.of());
    }
    public static EnhanceOutcome prepareEnhance(Player player,JsonObject request,long goldBefore,long goldRevision,Random random,List<ItemStack> bag){
        if(!request.has("rulesRevision")||request.get("rulesRevision").getAsLong()!=EquipmentDefinitions.current().revision)throw new IllegalArgumentException("装备规则已更新，请刷新后重试");
        if(!(player.containerMenu instanceof EnhanceMenu menu))throw new IllegalArgumentException("请使用凯莉的强化炉");
        if(request.get("menu").getAsInt()!=menu.containerId)throw new IllegalArgumentException("强化界面已变化");
        int index=request.get("slot").getAsInt();
        if(index!=0)throw new IllegalArgumentException("请把装备放进炉口");
        var slot=menu.slots.get(0);
        if(!SlotAccess.isWritable(slot,player))throw new IllegalArgumentException("炉口无法加工装备");
        var source=slot.getItem();
        var gear=EquipmentItems.gear(source);
        if(gear==null||!gear.instance.toString().equals(request.get("equipment").getAsString())||gear.revision!=request.get("revision").getAsInt())throw new IllegalArgumentException("装备已变化，请重新放入");
        if(gear.rarity==Rarity.PRIMORDIAL)throw new IllegalArgumentException("太初装备为管理员定值模板，不可强化");
        if(gear.creativeTemplate)throw new IllegalArgumentException("装备正在初始化，请稍后重试");
        boolean amplified=EnhancementRules.contaminated(gear);
        int current=EnhancementRules.level(gear);
        int target=request.get("target").getAsInt();
        if(target<=current||target>EnhancementRules.MAXIMUM)throw new IllegalArgumentException("强化目标无效");
        var materials=new Materials(player,bag);
        int available=materials.count(stack->fuel(stack,amplified));
        long gold=0;int fuel=0,successes=0,failures=0;
        int from=current;boolean shattered=false;
        while(current<target&&!shattered){
            int attemptTarget=current+1,need=EnhancementRules.fuel(attemptTarget);
            if(available<need){
                if(fuel==0&&successes==0&&failures==0)throw new IllegalArgumentException(amplified?"强烈的气息不足：冲+"+attemptTarget+"需要"+need+"个":"煤或木炭不足：冲+"+attemptTarget+"需要"+need+"个");
                break;
            }
            long cost=EnhancementRules.goldCost(gear,attemptTarget,amplified);
            if(goldBefore-gold-cost<0){
                if(fuel==0&&successes==0&&failures==0)throw new IllegalArgumentException("金币不足：冲+"+attemptTarget+"需要"+cost);
                break;
            }
            gold=Math.addExact(gold,cost);fuel+=need;available-=need;
            if(EnhancementRules.roll(current,EnhancementRules.successRate(attemptTarget),random)==EnhancementRules.Outcome.SUCCESS){
                current++;successes++;
            }else if(current+1>=EnhancementRules.MAXIMUM){
                shattered=true;
            }else{
                current-=EnhancementRules.failureLoss(current);failures++;
            }
        }
        materials.take(stack->fuel(stack,amplified),fuel);
        var edits=materials.edits();
        if(shattered){
            edits.add(InventoryEdits.change(player,"menu",0,source,ItemStack.EMPTY));
        }else if(current!=from){
            if(amplified)gear.amplifyLevel=current;else gear.enhanceLevel=current;
            gear.revision++;
            var updated=source.copy();EquipmentItems.write(updated,gear);
            edits.add(InventoryEdits.change(player,"menu",0,source,updated));
        }
        request.add("inventoryChanges",edits);
        var balances=new JsonObject();var balancesBefore=new JsonObject();
        balancesBefore.addProperty("gold",goldBefore);balancesBefore.addProperty("goldRevision",goldRevision);
        balances.addProperty("gold",Math.subtractExact(goldBefore,gold));balances.addProperty("goldRevision",Math.addExact(goldRevision,1));
        request.add("beforeBalances",balancesBefore);request.add("balances",balances);
        return new EnhanceOutcome(from,current,successes,failures,shattered,gold,fuel,amplified);
    }

    public static void prepareCube(net.minecraft.world.entity.player.Player player,cn.yumis.fight.modules.professions.api.Profession profession,JsonObject request){
        prepareCube(player,profession,request,List.of());
    }
    private static boolean exaltedStone(ItemStack stack){return ItemData.own(stack)&&ItemData.id(stack).equals("exalted_stone");}
    public static int cubeReserve(Player player,List<ItemStack> bag){return countMaterials(player,bag,EquipmentChanges::exaltedStone);}
    public static CraftingEssences.Recipe cubeRecipe(List<ItemStack> grid,int reserve){
        if(!grid.get(4).isEmpty()||reserve<=0)return CraftingEssences.match(grid);
        var supplemented=new ArrayList<>(grid);
        supplemented.set(4,cn.yumis.fight.platform.ItemRegistry.item("exalted_stone",1));
        return CraftingEssences.match(supplemented);
    }
    public static void prepareCube(Player player,cn.yumis.fight.modules.professions.api.Profession profession,JsonObject request,List<ItemStack> bag){
        if(!(player.containerMenu instanceof HoradricCubeMenu menu)||!menu.initialContentsReady)throw new IllegalArgumentException("请等待赫拉迪姆方块材料同步");
        if(request.get("menu").getAsInt()!=menu.containerId)throw new IllegalArgumentException("合成界面已变化");
        if(request.get("rulesRevision").getAsLong()!=EquipmentDefinitions.current().revision)throw new IllegalArgumentException("装备规则已更新，请重试");
        var grid=menu.grid();
        var materials=new Materials(player,bag);
        boolean supplement=grid.get(4).isEmpty()&&request.has("supplement");
        int reserve=supplement?materials.count(EquipmentChanges::exaltedStone):0;
        var recipe=cubeRecipe(grid,reserve);
        if(recipe==null||supplement&&!recipe.essence().id().equals(request.get("supplement").getAsString()))throw new IllegalArgumentException("材料、排列或装备保护条件不符合配方");
        int count=1;
        boolean toInventory=request.has("toInventory")&&request.get("toInventory").getAsBoolean();
        if(toInventory){count=supplement?Math.min(64,reserve):64;for(var stack:grid)if(!stack.isEmpty())count=Math.min(count,stack.getCount());}
        if(supplement&&!materials.take(EquipmentChanges::exaltedStone,count))throw new IllegalArgumentException("崇高石不足");
        var edits=materials.edits();
        for(int i=0;i<9;i++){var before=grid.get(i);var after=before.copy();if(!after.isEmpty())after.shrink(count);edits.add(InventoryEdits.change(player,"menu",i+1,before,after));}
        var output=cn.yumis.fight.platform.ItemRegistry.item(recipe.essence().id(),count);
        if(toInventory){var outputs=new JsonArray();outputs.add(InventoryEdits.encode(player,output));request.add("outputs",outputs);}
        else{
            var cursor=menu.getCarried();if(!cursor.isEmpty()&&(!ItemStack.isSameItemSameComponents(cursor,output)||cursor.getCount()+count>cursor.getMaxStackSize()))throw new IllegalArgumentException("请先放下手中物品，或按Shift合成到背包");
            var after=cursor.isEmpty()?output:cursor.copyWithCount(cursor.getCount()+count);edits.add(InventoryEdits.change(player,"cursor",0,cursor,after));
        }
        request.add("inventoryChanges",edits);
        var before=EquipmentJson.copy(profession,cn.yumis.fight.modules.professions.api.Profession.class);request.add("beforeProfession",JSON.toJsonTree(before));
        before.award(60*count,false);before.grantTitle("craftsman");request.add("profession",JSON.toJsonTree(before));
    }
}
