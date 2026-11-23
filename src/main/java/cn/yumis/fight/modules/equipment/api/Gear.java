package cn.yumis.fight.modules.equipment.api;

import java.util.*;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.affixes.api.Affix;

/** equipment模块拥有的数据合同；实例保留：读取接受当前格式自 {@link #MINIMUM_SCHEMA_VERSION} 起的修订版本，主动变异才盖章新版本。 */
public class Gear {
    public UUID instance;
    public Gear(){this(UUID.randomUUID());}
    Gear(UUID instance){this.instance=instance;}
    public static final int SCHEMA_VERSION=10;
    /** 当前格式可读取的最老存档版本；v10 相对 v9 仅新增带默认值的强化字段，v9 实例按缺省值继续生效。 */
    public static final int MINIMUM_SCHEMA_VERSION=9;
    public boolean corrupted,creativeTemplate;
    public int version=SCHEMA_VERSION,level=1,revision,sockets;
    /** 强化等级；被异界气息侵染后冻结作废，只按增幅等级计算收益。 */
    public int enhanceLevel,amplifyLevel;
    /** 异次元三维（STRENGTH/INTELLIGENCE/SPIRIT）；空串表示未被异界气息侵染。 */
    public String contamination="";
    public Slot slot=Slot.WEAPON;
    public Weapon weapon=Weapon.KATANA;
    public ArmorType armorType=ArmorType.UNTYPED;
    public JewelryType jewelryType=JewelryType.UNTYPED;
    public Rarity rarity=Rarity.COMMON;
    public String set="",source="",template="",appearance="";
    public Map<cn.yumis.fight.modules.attributes.api.Stat,double[]> baseGrowth=new EnumMap<>(cn.yumis.fight.modules.attributes.api.Stat.class);
    public String savedName="";
    public List<Affix> affixes=new ArrayList<>();
    public List<Gem> gems=new ArrayList<>();

    long nonBaseAffixCount() {
        return affixes.stream().filter(affix -> !affix.base).count();
    }

    Affix masterAffix() {
        for (Affix affix : affixes) {
            if (!affix.base && affix.fixed && affix.crafted && EquipmentRules.MASTER_LEVEL_AFFIX.equals(affix.id)) {
                return affix;
            }
        }
        return null;
    }

    public int requiredLevel() {
        Affix master = masterAffix();
        return master == null ? level : Math.max(1, level - (int) Math.round(master.value));
    }

    public boolean valid(Progress p,int allowance){
        return valid(requiredLevel(),slot,weapon,p,allowance);
    }
    static boolean valid(int requiredLevel,Slot slot,Weapon weapon,Progress p,int allowance){return requiredLevel<=p.level+allowance&&p.level>=slot.minimum()&&(slot!=Slot.WEAPON||cn.yumis.fight.modules.character.api.JobCatalog.weaponQualified(p,weapon.name()));}
}
