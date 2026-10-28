package cn.yumis.fight.modules.industry.api;

/** Exact cumulative charging and sub-tick work, independent of Minecraft. */
public final class ProcessingWork {
    public record Step(Fraction work,Fraction timeLeft,long paid,boolean finished){}
    private ProcessingWork(){}
    public static long demand(long energy,long duration,Fraction work,long paid,Fraction time){return energy==0?0:Math.max(0,work.add(time).min(Fraction.of(duration)).multiply(energy).divide(duration).ceil()-paid);}
    public static Step advance(long energy,long duration,Fraction work,long paid,Fraction time,long supply){
        if(energy<0||duration<=0||supply<0||paid<0||paid>energy)throw new IllegalArgumentException("无效加工记账");
        long nextPaid=paid+Math.min(supply,energy-paid);Fraction end=Fraction.of(duration),affordable=energy==0?end:Fraction.of(nextPaid).multiply(duration).divide(energy);
        Fraction nextWork=work.add(time).min(end).min(affordable);return new Step(nextWork,time.subtract(nextWork.subtract(work)),nextPaid,nextPaid==energy&&nextWork.equals(end));
    }
}
