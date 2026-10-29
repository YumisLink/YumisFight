package cn.yumis.fight.modules.equipment.api;

import java.util.*;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.affixes.api.Affix;

/** equipment模块拥有的数据合同；只接收当前存档代际。 */
public class Gear {
    public UUID instance;
    public Gear(){this(UUID.randomUUID());}
    Gear(UUID instance){this.instance=instance;}
    public static final int SCHEMA_VERSION=9;
    public boolean corrupted,creativeTemplate;
    public int version=SCHEMA_VERSION,level=1,revision,sockets;
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
    public boolean valid(Progress p,int allowance){
        return valid(level,slot,weapon,p,allowance);
    }
    static boolean valid(int level,Slot slot,Weapon weapon,Progress p,int allowance){return level<=p.level+allowance&&p.level>=slot.minimum()&&(slot!=Slot.WEAPON||weapon!=Weapon.LIGHTSABER||p.hasJob(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER));}
}
