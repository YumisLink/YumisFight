package cn.yumis.fight.architecture;

/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public final class AttributeMath {
    private AttributeMath(){}
    public static double value(double base,double mut,double add,double totalProduct,double finalAdd){return (base*(1+mut)+add)*totalProduct+finalAdd;}
    public static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("数值不是有限值");}
}

