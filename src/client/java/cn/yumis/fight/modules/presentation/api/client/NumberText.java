package cn.yumis.fight.modules.presentation.api.client;
public final class NumberText {
    private NumberText(){}
    public static String number(double n){
        return n==Math.rint(n)?String.format(java.util.Locale.ROOT,"%.0f",n):String.format(java.util.Locale.ROOT,"%.1f",n);
    }
}
