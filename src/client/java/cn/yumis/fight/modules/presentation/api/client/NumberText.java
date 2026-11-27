package cn.yumis.fight.modules.presentation.api.client;
public final class NumberText {
    private NumberText(){}
    /** Damage points are rounded only for display; never feed this value back into arithmetic. */
    public static String damage(double n){return java.math.BigDecimal.valueOf(n).setScale(0,java.math.RoundingMode.HALF_UP).toPlainString();}
    public static String number(double n){
        return n==Math.rint(n)?String.format(java.util.Locale.ROOT,"%.0f",n):String.format(java.util.Locale.ROOT,"%.1f",n);
    }
    /** Formats a ratio (0..1) as a compact percentage, preserving small decimals like 0.01%. */
    public static String ratePercent(double ratio){
        if(!Double.isFinite(ratio))return "?%";
        double pct=ratio*100;
        if(pct==0)return "0%";
        int scale=Math.abs(pct)<.01?3:2;
        return java.math.BigDecimal.valueOf(pct).setScale(scale,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"%";
    }
}
