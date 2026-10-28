package cn.yumis.fight.modules.attributes.api;
import java.util.*;

public final class AttributeNumbers {
    private AttributeNumbers(){}
    public static final double DROP_RATE_BASE=1000;
    public static void finite(double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("数值不是有限值");
    }
}
