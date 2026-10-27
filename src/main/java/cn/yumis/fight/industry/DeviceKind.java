package cn.yumis.fight.industry;

public enum DeviceKind {
    CRUSHER("mana_crusher",true),FURNACE("mana_furnace",true),MINER("mineral_generator",true),CURRENCY("currency_fabricator",true),FORGE("mana_forge",true),BEACON("mana_beacon",true),
    COAL("coal_generator",false),SOLAR("solar_collector",false),TURBINE("turbine_generator",false),WIND("wind_generator",false),THERMAL("thermal_generator",false),NUCLEAR("nuclear_reactor",false),RTG("rtg_generator",false),MIXER("coolant_mixer",false),TRANSFER("transfer_crystal",false),HARVEST("harvest_crystal",false);
    public final String id;public final boolean graded;
    DeviceKind(String id,boolean graded){this.id=id;this.graded=graded;}
    public boolean generator(){return ordinal()>=COAL.ordinal()&&ordinal()<=RTG.ordinal();}
    public boolean functional(){return this==TRANSFER||this==HARVEST;}
    public boolean processing(){return this==CRUSHER||this==FURNACE||this==MINER||this==CURRENCY||this==FORGE||this==MIXER;}
    public int inputs(){return switch(this){case CRUSHER,FURNACE->3;case FORGE->4;case MIXER->1;default->0;};}
}
