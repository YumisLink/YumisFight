package cn.yumis.fight.modules.industry.api;

/** A client-local rotor clock. Pausing holds its angle, including across render gaps. */
public final class RotorMotion {
    private double last=Double.NaN,angle;
    private boolean moving;
    public float sample(double time,boolean active,double degreesPerTick){
        if(!Double.isNaN(last)&&time>=last&&moving)angle=(angle+(time-last)*degreesPerTick)%360;
        last=time;moving=active;return (float)angle;
    }
}
