package cn.yumis.fight.architecture;

/** 底层架构文件：统一属性的数值存储与运算合同。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。
 * Ratios are increments (150% = 1.5). Serialized fields and double precision are preserved. */
public final class AttributeValue {
    public double base,mut,add,alls=1,fadd;
    public AttributeValue(){}
    public AttributeValue(double base){this.base=base;}
    public double value(){return AttributeMath.value(base,mut,add,alls,fadd);}
    public void more(double increment){if(!Double.isFinite(increment))throw new IllegalArgumentException("数值不是有限值");alls*=1+increment;}
}
