package cn.yumis.fight.modules.industry.api;

import java.math.BigInteger;

/** Exact work/time fractions. Energy balances themselves remain long integers. */
public record Fraction(BigInteger numerator,BigInteger denominator) implements Comparable<Fraction> {
    public static final Fraction ZERO=of(0);
    public Fraction {if(denominator.signum()<=0)throw new IllegalArgumentException("非正分母");BigInteger gcd=numerator.gcd(denominator);numerator=numerator.divide(gcd);denominator=denominator.divide(gcd);}
    public static Fraction of(long n){return new Fraction(BigInteger.valueOf(n),BigInteger.ONE);}
    public static Fraction parse(String s){String[] p=s.split("/");return new Fraction(new BigInteger(p[0]),p.length==1?BigInteger.ONE:new BigInteger(p[1]));}
    public Fraction add(Fraction b){return new Fraction(numerator.multiply(b.denominator).add(b.numerator.multiply(denominator)),denominator.multiply(b.denominator));}
    public Fraction subtract(Fraction b){return new Fraction(numerator.multiply(b.denominator).subtract(b.numerator.multiply(denominator)),denominator.multiply(b.denominator));}
    public Fraction multiply(long b){return new Fraction(numerator.multiply(BigInteger.valueOf(b)),denominator);}
    public Fraction divide(long b){return new Fraction(numerator,denominator.multiply(BigInteger.valueOf(b)));}
    public Fraction min(Fraction b){return compareTo(b)<=0?this:b;}
    public long ceil(){return numerator.add(denominator.subtract(BigInteger.ONE)).divide(denominator).longValueExact();}
    public double value(){return numerator.doubleValue()/denominator.doubleValue();}
    public boolean positive(){return numerator.signum()>0;}
    @Override public int compareTo(Fraction b){return numerator.multiply(b.denominator).compareTo(b.numerator.multiply(denominator));}
    @Override public String toString(){return numerator+"/"+denominator;}
}
