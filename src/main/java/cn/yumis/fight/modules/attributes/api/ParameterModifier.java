package cn.yumis.fight.modules.attributes.api;

/** Attribute contribution addressed by a stable content ID; the skill module interprets parameters. */
public record ParameterModifier(String source,String skill,String parameter,double additive,double multiplier) {
    public ParameterModifier {
        if(skill==null||source==null||parameter==null||!Double.isFinite(additive)||!Double.isFinite(multiplier)||multiplier<0)throw new IllegalArgumentException("Invalid skill modifier");
    }
}
