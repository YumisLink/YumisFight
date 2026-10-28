package cn.yumis.fight.modules.combat.api;

import java.util.*;

/** combat模块拥有的数据合同；仅接收当前存档代际。 */
public class Life {
    public UUID target;
    public String dimension="",instance="";
    public long generation=1,revision,basis;
    public double q=1;
    public boolean dead;
}
