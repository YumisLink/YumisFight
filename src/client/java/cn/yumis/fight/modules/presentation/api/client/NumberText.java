package cn.yumis.fight.modules.presentation.api.client;
public final class NumberText {
    private NumberText(){}
    /** Damage points are rounded only for display; never feed this value back into arithmetic. */
    public static String damage(double n){return java.math.BigDecimal.valueOf(n).setScale(0,java.math.RoundingMode.HALF_UP).toPlainString();}
    public static String number(double n){
        return n==Math.rint(n)?String.format(java.util.Locale.ROOT,"%.0f",n):String.format(java.util.Locale.ROOT,"%.1f",n);
    }
}
