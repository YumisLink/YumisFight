package cn.yumis.fight.modules.industry.api;

/** A pipe grade contributes storage; only its machine-facing ports limit throughput. */
public final class FluidPipes {
    public record Grade(String id,int technology,long capacity,int flow){}
    private static final Grade[] GRADES={new Grade("fluid_pipe_t1",1,1_000,100),new Grade("fluid_pipe_t2",2,4_000,1_000),new Grade("fluid_pipe_t3",4,16_000,16_000)};
    private FluidPipes(){}
    public static Grade grade(int index){return GRADES[index-1];}
    public static java.util.List<Grade> grades(){return java.util.List.of(GRADES);}
}
