package cn.yumis.fight.modules.equipment.api;

import java.util.*;

import cn.yumis.fight.modules.affixes.api.Affix;

/** equipment模块拥有的数据合同；仅接收当前存档代际。 */
public class Gem {
    public String name="宝石";
    public int level=1;
    public String group="";
    public List<Affix> affixes=new ArrayList<>();
}
