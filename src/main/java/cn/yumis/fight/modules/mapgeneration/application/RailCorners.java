package cn.yumis.fight.modules.mapgeneration.application;

import java.util.*;
import java.util.function.BiPredicate;

/** 栅栏拐角的纯几何求解：不依赖游戏运行时，供无头验证直接复用。 */
final class RailCorners {
    private RailCorners() {}

    /**
     * 同高护栏两两对角相邻、公共邻格恰有一个属于内侧（道路／台面）时，
     * 另一个公共邻格即外拐角缺口，返回 {缺口x,缺口y,缺口z,护栏Ax,护栏Az,护栏Bx,护栏Bz}。
     * 已有护栏的公共邻格不再视为缺口；每个缺口只报告一次。
     */
    static List<int[]> solve(Collection<int[]> rails,BiPredicate<Integer,Integer> inside,BiPredicate<Integer,Integer> interior,BiPredicate<Integer,Integer> fillableCell) {
        Map<Long,int[]> grid=new HashMap<>();
        for(int[] r:rails)grid.putIfAbsent(pack(r[0],r[1],r[2]),r);
        List<int[]> gaps=new ArrayList<>();Set<Long> unique=new HashSet<>();
        for(int[] a:rails)for(int dz:new int[]{1,-1}) {
            int[] b=grid.get(pack(a[0]+1,a[1],a[2]+dz));
            if(b==null)continue;
            int[][] common={{a[0]+1,a[2]},{a[0],a[2]+dz}};
            int gap=-1,insiders=0;
            for(int k=0;k<2;k++) {
                int cx=common[k][0],cz=common[k][1];
                if(!inside.test(cx,cz))continue;
                if(interior.test(cx,cz))insiders++;
                else if(fillableCell.test(cx,cz)&&gap<0&&!grid.containsKey(pack(cx,a[1],cz)))gap=k;
            }
            if(insiders!=1||gap<0)continue;
            if(!unique.add(pack(common[gap][0],a[1],common[gap][1])))continue;
            gaps.add(new int[]{common[gap][0],a[1],common[gap][1],a[0],a[2],b[0],b[2]});
        }
        return gaps;
    }

    private static long pack(int x,int y,int z){return ((long)(x&0x1FFFFF)<<42)|((long)(z&0x1FFFFF)<<21)|(y&0x1FFFFF);}
}
