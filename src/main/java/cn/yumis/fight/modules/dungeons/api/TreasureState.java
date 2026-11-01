package cn.yumis.fight.modules.dungeons.api;
import java.util.*;
/** Current-generation encounter state owned exclusively by dungeons. Region zero is the overworld hunt. */
public final class TreasureState {
    public int region,baseLevel,tier=3,partySize=1;
    public double failedProbability=1;
    public UUID owner;
    public int mapX,mapZ;
    public double mapY;
    public long expires,nextVolley;
    public boolean portal,cleared,warningFive,warningOne;
    public String theme="forest";
    public double lastX,lastY,lastZ;
}
