package cn.yumis.fight.modules.industry.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Display units only; energy settlement always retains its original precision. */
public final class EnergyDisplay {
    private EnergyDisplay(){}
    public static String format(long value){return format(BigDecimal.valueOf(value));}
    public static String format(double value){return format(BigDecimal.valueOf(value));}
    private static String format(BigDecimal value){
        BigDecimal magnitude=value.abs();long divisor=1;String unit="";
        if(magnitude.compareTo(BigDecimal.valueOf(1_000_000_000_000L))>=0){divisor=1_000_000_000_000L;unit="万亿";}
        else if(magnitude.compareTo(BigDecimal.valueOf(100_000_000L))>=0){divisor=100_000_000L;unit="亿";}
        else if(magnitude.compareTo(BigDecimal.valueOf(10_000L))>=0){divisor=10_000L;unit="万";}
        return value.divide(BigDecimal.valueOf(divisor),2,RoundingMode.DOWN).stripTrailingZeros().toPlainString()+unit;
    }
}
