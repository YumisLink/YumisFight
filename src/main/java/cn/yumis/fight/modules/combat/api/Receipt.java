package cn.yumis.fight.modules.combat.api;

import java.util.*;

/** combat模块拥有的数据合同；仅接收当前存档代际。 */
public record Receipt(boolean accepted,String reason,double actual,double q,long revision,String deathId) {
}
