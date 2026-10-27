package cn.yumis.fight.client;

import com.google.gson.JsonObject;

/** A five-tick prediction window; authoritative snapshots own batch changes and completion. */
public final class IndustrialProgress {
    private JsonObject sample;
    private long serverTick=Long.MIN_VALUE,batch;
    private int elapsed;
    private double confirmed,rate;
    public void tick(JsonObject info){
        if(info!=sample){
            long stamp=info.has("server_tick")?info.get("server_tick").getAsLong():0;
            if(stamp>=serverTick){
                sample=info;serverTick=stamp;batch=info.has("batch_sequence")?info.get("batch_sequence").getAsLong():0;
                confirmed=info.has("progress")?Math.clamp(info.get("progress").getAsDouble(),0,1):0;
                rate=info.has("progress_rate")?Math.max(0,info.get("progress_rate").getAsDouble()):0;
                if(!Double.isFinite(rate))rate=0;
                elapsed=0;return;
            }
        }
        elapsed=Math.min(5,elapsed+1);
    }
    public double value(float partialTick){
        if(confirmed>=1)return 1;
        // The UI rounds percentages to one decimal; 0.9999 would incorrectly print 100.0%.
        return Math.clamp(confirmed+rate*Math.min(5,elapsed+Math.clamp(partialTick,0,1)),0,0.999);
    }
    public long batch(){return batch;}
}
