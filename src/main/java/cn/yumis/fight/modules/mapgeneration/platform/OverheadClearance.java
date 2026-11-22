package cn.yumis.fight.modules.mapgeneration.platform;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 野外建筑头顶净空的采样判定与分片清理。判定只随机抽 1~2 个占地立柱向上探测，
 * 探到自然地形压到屋顶上方才对整座建筑清理净空带，否则视为通过、一个方块都不动。
 * 立柱高度、方块读写均由调用方以函数式接口提供，算法本体可在无头环境验证。
 */
public final class OverheadClearance {
    /** 屋顶上方保留的净空格数。 */
    public static final int DEPTH = 5;

    /** 指定立柱的自然首个自由高度（最高方块上方第一格），由调用方提供。 */
    @FunctionalInterface
    public interface ColumnProbe { int firstFreeHeight(int x, int z); }

    /** 该格是否跳过清理（空气或受保护方块），由调用方提供。 */
    @FunctionalInterface
    public interface SkipProbe { boolean skip(int x, int y, int z); }

    /** 将该格清理为空气，由调用方提供。 */
    @FunctionalInterface
    public interface Eraser { void erase(int x, int y, int z); }

    private OverheadClearance() {}

    /**
     * 随机抽取 1~2 个占地立柱向上探测：立柱自然地表压到屋顶以上（首个自由高度
     * 超过屋顶上一格）即判定头顶有遮挡。种子由调用方混入世界种子与结构包围盒，
     * 同一建筑的各区块调用得到相同采样点，整座建筑判定一致。
     */
    public static boolean hangsOverhead(long seed, BoundingBox box, ColumnProbe probe) {
        RandomSource random = RandomSource.create(seed);
        int samples = 1 + random.nextInt(2);
        for (int i = 0; i < samples; i++) {
            int x = box.minX() + random.nextInt(box.maxX() - box.minX() + 1);
            int z = box.minZ() + random.nextInt(box.maxZ() - box.minZ() + 1);
            if (probe.firstFreeHeight(x, z) > box.maxY() + 1) return true;
        }
        return false;
    }

    /**
     * 清理整座建筑占地投影上方 [{@code box} 屋顶+1, 屋顶+{@link #DEPTH}] 的净空带，
     * 写入裁剪到当前区块切片，越出世界高度的部分不动。返回实际清理格数。
     */
    public static int clear(BoundingBox box, BoundingBox chunk, SkipProbe skip, Eraser eraser) {
        int x0 = Math.max(box.minX(), chunk.minX()), x1 = Math.min(box.maxX(), chunk.maxX());
        int z0 = Math.max(box.minZ(), chunk.minZ()), z1 = Math.min(box.maxZ(), chunk.maxZ());
        int y0 = Math.max(box.maxY() + 1, chunk.minY()), y1 = Math.min(box.maxY() + DEPTH, chunk.maxY());
        int cleared = 0;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++)
            if (!skip.skip(x, y, z)) { eraser.erase(x, y, z); cleared++; }
        return cleared;
    }
}
