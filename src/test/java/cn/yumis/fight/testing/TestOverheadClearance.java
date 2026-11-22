package cn.yumis.fight.testing;
import cn.yumis.fight.modules.mapgeneration.platform.OverheadClearance;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 野外建筑头顶净空：采样判定 1~2 点、同种子跨区块一致、阈值边界、净空带按区块分片且越界裁剪。 */
class TestOverheadClearance {
    /** 一座 24x24、区块对齐、屋顶顶面 y=70 的示例建筑。 */
    private static BoundingBox building() {return new BoundingBox(96,64,-64,119,70,-41);}
    /** 指定区块的 16x16 可写区，高度 [64,73] 模拟世界顶部裁剪。 */
    private static BoundingBox chunk(int chunkX,int chunkZ) {
        return new BoundingBox(chunkX*16,64,chunkZ*16,chunkX*16+15,73,chunkZ*16+15);
    }

    @Test void samplingPicksOneOrTwoColumnsInsideFootprint(){
        for(long seed=0;seed<200;seed++) {
            List<int[]> columns=new ArrayList<>();
            assertFalse(OverheadClearance.hangsOverhead(seed,building(),(x,z)->{
                columns.add(new int[]{x,z});
                return 0;
            }));
            assertTrue(columns.size()>=1&&columns.size()<=2,"采样数量须在 1~2 之间，实际 "+columns.size());
            for(int[] c:columns)
                assertTrue(c[0]>=96&&c[0]<=119&&c[1]>=-64&&c[1]<=-41,"采样点越出占地投影");
        }
    }

    @Test void sameSeedSamplesSameColumnsAcrossChunkCalls(){
        List<int[]> first=new ArrayList<>(),second=new ArrayList<>();
        OverheadClearance.hangsOverhead(1234567L,building(),(x,z)->{first.add(new int[]{x,z});return 0;});
        OverheadClearance.hangsOverhead(1234567L,building(),(x,z)->{second.add(new int[]{x,z});return 0;});
        assertEquals(first.size(),second.size());
        for(int i=0;i<first.size();i++)
            assertArrayEquals(first.get(i),second.get(i),"各区块调用须得到相同采样点，整座建筑判定才一致");
    }

    @Test void obstructionThresholdAtRoofEdge(){
        BoundingBox box=building();
        // 自然地表顶面与屋顶齐平（首个自由高度恰为屋顶+1）属于贴顶而非遮挡，视为通过。
        assertFalse(OverheadClearance.hangsOverhead(99L,box,(x,z)->box.maxY()+1));
        // 高出一格即判定头顶有遮挡。
        assertTrue(OverheadClearance.hangsOverhead(99L,box,(x,z)->box.maxY()+2));
    }

    @Test void anySampledColumnTriggersTheVerdict(){
        for(long seed=0;seed<200;seed++) {
            List<int[]> columns=new ArrayList<>();
            OverheadClearance.hangsOverhead(seed,building(),(x,z)->{columns.add(new int[]{x,z});return 0;});
            // 第一采样点必然被探测：只在它头顶放遮挡就必须判定成立。
            int[] first=columns.get(0);
            assertTrue(OverheadClearance.hangsOverhead(seed,building(),(x,z)->x==first[0]&&z==first[1]?building().maxY()+2:0));
            // 第二采样点只在确实抽到第二个点时参与判定。
            boolean expected=columns.size()==2;
            if(expected) {
                int[] second=columns.get(1);
                assertEquals(expected,OverheadClearance.hangsOverhead(seed,building(),
                    (x,z)->x==second[0]&&z==second[1]?building().maxY()+2:0),"抽到第二个点时它头顶的遮挡也须成立");
            } else
                assertFalse(OverheadClearance.hangsOverhead(seed,building(),(x,z)->0));
        }
    }

    @Test void clearWritesBandClippedPerChunkSlice(){
        Set<Long> writes=new HashSet<>();
        int cleared=OverheadClearance.clear(building(),chunk(6,-4),(x,y,z)->false,(x,y,z)->writes.add(pack(x,y,z)));
        assertEquals(16*16*3,cleared,"世界顶部裁到 73 时 16x16 切片应清满屋顶上方 3 格");
        for(long key:writes) {
            int[] pos=unpack(key);
            assertTrue(pos[0]>=96&&pos[0]<=111&&pos[1]>=71&&pos[1]<=73&&pos[2]>=-64&&pos[2]<=-49,"写入越出当前区块切片或净空带");
        }
        // 四个区块切片合并后恰好覆盖整座占地投影且互不重叠。
        OverheadClearance.clear(building(),chunk(7,-4),(x,y,z)->false,(x,y,z)->writes.add(pack(x,y,z)));
        OverheadClearance.clear(building(),chunk(6,-3),(x,y,z)->false,(x,y,z)->writes.add(pack(x,y,z)));
        OverheadClearance.clear(building(),chunk(7,-3),(x,y,z)->false,(x,y,z)->writes.add(pack(x,y,z)));
        assertEquals(24*24*3,writes.size(),"四切片合并应恰好覆盖整座占地投影且互不重叠");
    }

    @Test void clearSkipsProtectedColumns(){
        Set<Long> writes=new HashSet<>();
        int cleared=OverheadClearance.clear(building(),chunk(6,-4),(x,y,z)->x==97,(x,y,z)->writes.add(pack(x,y,z)));
        assertEquals(15*16*3,writes.size(),"受保护立柱整列跳过");
        for(long key:writes) {
            int[] pos=unpack(key);
            assertNotEquals(97,pos[0],"受保护方块不得被清理");
        }
        assertEquals(cleared,writes.size());
    }

    private static long pack(int x,int y,int z) {
        return ((long)x&0x3FFFFFF)<<38|((long)z&0x3FFFFFF)<<12|(long)y&0xFFF;
    }
    private static int[] unpack(long key) {
        return new int[]{(int)(key>>38),(int)(key<<52>>52),(int)(key<<26>>38)};
    }
}
