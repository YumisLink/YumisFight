package cn.yumis.fight.modules.attributes.api;
/** Resolved elemental interval; calculation does not read equipment or affix definitions. */
public final class ExtraDamage {
    public double value,maximum; public int element;
    public ExtraDamage(){}
    public ExtraDamage(int element,double value,double maximum){this.element=element;this.value=value;this.maximum=maximum;}
}
