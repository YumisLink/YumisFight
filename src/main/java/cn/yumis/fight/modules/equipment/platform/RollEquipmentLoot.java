package cn.yumis.fight.modules.equipment.platform;

import cn.yumis.fight.modules.equipment.api.ArmorType;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.JewelryType;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import java.util.Locale;
import java.util.Random;

/** Rolls current-format equipment for shared world loot containers. */
public record RollEquipmentLoot(Slot slot, ArmorType armor, Rarity rarity, int minLevel, int maxLevel)
    implements LootItemFunction {
    public static final MapCodec<RollEquipmentLoot> CODEC = RecordCodecBuilder.<RollEquipmentLoot>mapCodec(builder -> builder.group(
        names(Slot.class).fieldOf("slot").forGetter(RollEquipmentLoot::slot),
        names(ArmorType.class).optionalFieldOf("armor", ArmorType.UNTYPED).forGetter(RollEquipmentLoot::armor),
        names(Rarity.class).optionalFieldOf("rarity", Rarity.COMMON).forGetter(RollEquipmentLoot::rarity),
        Codec.intRange(1, 80).fieldOf("min_level").forGetter(RollEquipmentLoot::minLevel),
        Codec.intRange(1, 80).fieldOf("max_level").forGetter(RollEquipmentLoot::maxLevel)
    ).apply(builder, RollEquipmentLoot::new)).flatXmap(RollEquipmentLoot::checkDefinition, DataResult::success);

    private DataResult<RollEquipmentLoot> checkDefinition() {
        if (minLevel > maxLevel || minLevel < slot.minimum() || !EquipmentTemplates.designed(rarity)
            || armor != ArmorType.UNTYPED && !slot.armor()) {
            return DataResult.error(() -> "Invalid equipment loot: " + this);
        }
        return DataResult.success(this);
    }

    private static <T extends Enum<T>> Codec<T> names(Class<T> type) {
        return Codec.STRING.comapFlatMap(id -> {
            try {
                return DataResult.success(Enum.valueOf(type, id.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + id);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }

    @Override public MapCodec<RollEquipmentLoot> codec() { return CODEC; }

    @Override public ItemStack apply(ItemStack stack, LootContext context) {
        var random = new Random(context.getRandom().nextLong());
        var weapons = Weapon.standard();
        Weapon weapon = slot == Slot.WEAPON ? weapons.get(random.nextInt(weapons.size())) : Weapon.KATANA;
        int level = random.nextInt(minLevel, maxLevel + 1);
        var gear = armor == ArmorType.UNTYPED
            ? EquipmentTemplates.createStandard(slot, weapon, rarity, level, random)
            : EquipmentRules.create(slot, weapon, rarity, level, armor, JewelryType.UNTYPED, random);
        gear.source = "world_loot";
        return EquipmentItems.gear(gear);
    }
}
