package cn.yumis.fight.modules.mapgeneration.application;

import java.util.*;

/**
 * 群体建筑的路径高度差约束与路径间墙体削坡的纯几何求解。
 * 不依赖游戏运行时，供 {@link MapPlanner} 与无头验证直接复用。
 */
final class TerrainWallCutter {
    static final int NO_ANCHOR = Integer.MAX_VALUE;
    static final int MAX_PROXIMITY_GAP = 10;
    static final int MAX_BRIDGE_SPAN = 48;
    static final double WALL_THRESHOLD = 3.0;
    static final double GENTLE_SLOPE = 0.55;
    static final double SHADOW_SLOPE = 0.85;
    private static final double INF = 1e9;
    private static final double DIAG = Math.sqrt(2.0);
    private static final int[][] DIRS8 = {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1},
        {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
    };
    private static final double[] DIRS8_LEN = {1, 1, 1, 1, DIAG, DIAG, DIAG, DIAG};
    private static final int[][] AXES = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final double[] AXES_LEN = {1.0, 1.0, DIAG, DIAG};

    record Result(int[] heights, int cutCells, int wallsDetected, int maxCutDepth) {}

    private TerrainWallCutter() {}

    /**
     * 检测沿 X/Z 方向相距 {@code 2..MAX_PROXIMITY_GAP} 格的邻近平行／相邻道路对，
     * 在高度求解图中补加按水平间距缩放的高度差约束，防止两条相近路径出现陡崖级高差。
     * 返回新增或收紧的邻近约束边数。
     */
    static int addProximityEdges(int width, int depth, Set<Integer> roadCells, int[] componentRoots,
            int stepLimit, Map<Integer, Map<Integer, Integer>> edges) {
        if (width <= 0 || depth <= 0 || roadCells.isEmpty() || stepLimit <= 0) return 0;
        int added = 0;
        for (int z = 0; z < depth; z++) {
            int lastX = -1;
            int row = z * width;
            for (int x = 0; x < width; x++) {
                int i = row + x;
                if (!roadCells.contains(i)) continue;
                if (lastX >= 0) {
                    int gap = x - lastX;
                    if (gap >= 2 && gap <= MAX_PROXIMITY_GAP) {
                        added += addWeightedEdge(edges, componentRoots[row + lastX], componentRoots[i], gap * stepLimit);
                    }
                }
                lastX = x;
            }
        }
        for (int x = 0; x < width; x++) {
            int lastZ = -1;
            for (int z = 0; z < depth; z++) {
                int i = z * width + x;
                if (!roadCells.contains(i)) continue;
                if (lastZ >= 0) {
                    int gap = z - lastZ;
                    if (gap >= 2 && gap <= MAX_PROXIMITY_GAP) {
                        added += addWeightedEdge(edges, componentRoots[lastZ * width + x], componentRoots[i], gap * stepLimit);
                    }
                }
                lastZ = z;
            }
        }
        return added;
    }

    static int addWeightedEdge(Map<Integer, Map<Integer, Integer>> edges, int a, int b, int cap) {
        if (a == b) return 0;
        Map<Integer, Integer> ea = edges.get(a);
        Map<Integer, Integer> eb = edges.get(b);
        if (ea == null || eb == null) return 0;
        Integer old = ea.get(b);
        if (old != null && old <= cap) return 0;
        ea.put(b, cap);
        eb.put(a, cap);
        return 1;
    }

    /**
     * 基于道路与刚性设施锚点高度 {@code anchors}，检测路径之间的高墙并计算平滑削坡后的地表高度。
     * 仅向下削平高出路径／设施的自然地形，绝不抬高原生低谷、不改动水面或锚点本身，并在蓝图边界平滑衔接原地形。
     */
    static Result sculpt(int width, int depth, int[] naturalTerrain, boolean[] water, int[] anchors) {
        int n = width * depth;
        if (width <= 0 || depth <= 0 || naturalTerrain.length != n || water.length != n || anchors.length != n) {
            throw new IllegalArgumentException("地形网格尺寸不一致");
        }
        int[] out = naturalTerrain.clone();
        boolean hasAnchor = false;
        for (int a : anchors) {
            if (a != NO_ANCHOR) { hasAnchor = true; break; }
        }
        if (!hasAnchor) return new Result(out, 0, 0, 0);

        // 1. 四方向扫描路径／设施之间的高墙（自然地形高出两端连线 >= WALL_THRESHOLD）
        double[] betweenCap = new double[n];
        Arrays.fill(betweenCap, INF);
        int wallsDetected = 0;
        int maxLine = Math.max(width, depth) + 1;
        int[] coords = new int[maxLine];
        for (int axis = 0; axis < AXES.length; axis++) {
            int dx = AXES[axis][0], dz = AXES[axis][1];
            double stepLen = AXES_LEN[axis];
            wallsDetected += scanAxis(width, depth, dx, dz, stepLen, naturalTerrain, anchors, betweenCap, coords);
        }

        // 2. 从所有道路／设施锚点向外传播柔和削坡包络（GENTLE_SLOPE）与近端硬上限（1.25:1，首格仍 <= +1）
        double[] anchorInit = new double[n];
        for (int i = 0; i < n; i++) anchorInit[i] = anchors[i] != NO_ANCHOR ? anchors[i] : INF;
        double[] slopeCap = propagateMin(width, depth, anchorInit, GENTLE_SLOPE);
        double[] hardCap = propagateMin(width, depth, anchorInit, 1.25);

        // 3. 合并路径间削墙上限与柔和削坡包络，并做 Lipschitz 传播消除射线阴影边界台阶
        double[] rawCap = new double[n];
        for (int i = 0; i < n; i++) {
            rawCap[i] = anchors[i] != NO_ANCHOR ? anchors[i] : Math.min(betweenCap[i], slopeCap[i]);
        }
        double[] smoothCap = propagateMin(width, depth, rawCap, SHADOW_SLOPE);

        // 4. 承托底面分为两层：
        //    (a) 高处道路／设施锚点向下 1:1 承托，防止远处的低路挖空高处建筑坡脚；
        //    (b) 蓝图非锚点外边界向内 1.25:1 回接原生山体，避免在蓝图外框产生人工断崖，
        //        且不覆盖路径之间已检测出的削墙区（betweenCap）。
        double[] anchorFloorInit = new double[n];
        double[] borderFloorInit = new double[n];
        Arrays.fill(anchorFloorInit, -INF);
        Arrays.fill(borderFloorInit, -INF);
        for (int z = 0; z < depth; z++) {
            int row = z * width;
            boolean zBorder = z == 0 || z == depth - 1;
            for (int x = 0; x < width; x++) {
                int i = row + x;
                if (anchors[i] != NO_ANCHOR) anchorFloorInit[i] = anchors[i];
                else if (zBorder || x == 0 || x == width - 1) borderFloorInit[i] = naturalTerrain[i];
            }
        }
        double[] anchorFloor = propagateMax(width, depth, anchorFloorInit, 1.0);
        double[] borderFloor = propagateMax(width, depth, borderFloorInit, 1.25);

        // 5. 合成连续削坡面，仅削低、不抬高、不改动水面与锚点
        double[] surface = new double[n];
        for (int i = 0; i < n; i++) {
            if (anchors[i] != NO_ANCHOR) {
                surface[i] = anchors[i];
            } else if (water[i]) {
                surface[i] = naturalTerrain[i];
            } else {
                double floor = betweenCap[i] < INF ? anchorFloor[i] : Math.max(anchorFloor[i], borderFloor[i]);
                double val = Math.max(smoothCap[i], floor);
                val = Math.min(val, hardCap[i]);
                surface[i] = Math.min(naturalTerrain[i], val);
            }
        }

        // 6. 对已削坡区域做两轮局部平滑，消除斜向交线棱角
        double[] next = new double[n];
        for (int pass = 0; pass < 2; pass++) {
            System.arraycopy(surface, 0, next, 0, n);
            for (int z = 1; z < depth - 1; z++) {
                int row = z * width;
                for (int x = 1; x < width - 1; x++) {
                    int i = row + x;
                    if (anchors[i] != NO_ANCHOR || water[i]) continue;
                    if (surface[i] >= naturalTerrain[i] - 0.01) continue;
                    double avg = (surface[i] * 2.0 + surface[i - 1] + surface[i + 1]
                            + surface[i - width] + surface[i + width]) / 6.0;
                    next[i] = Math.min(naturalTerrain[i], avg);
                }
            }
            double[] tmp = surface;
            surface = next;
            next = tmp;
        }

        int cutCells = 0, maxCutDepth = 0;
        for (int i = 0; i < n; i++) {
            if (anchors[i] != NO_ANCHOR || water[i]) continue;
            int h = Math.min(naturalTerrain[i], (int) Math.round(surface[i]));
            out[i] = h;
            if (h < naturalTerrain[i]) {
                cutCells++;
                maxCutDepth = Math.max(maxCutDepth, naturalTerrain[i] - h);
            }
        }
        return new Result(out, cutCells, wallsDetected, maxCutDepth);
    }

    private static int scanAxis(int width, int depth, int dx, int dz, double stepLen,
            int[] naturalTerrain, int[] anchors, double[] betweenCap, int[] coords) {
        int detected = 0;
        if (dx == 1 && dz == 0) {
            for (int z = 0; z < depth; z++) detected += scanRay(0, z, dx, dz, stepLen, width, depth, naturalTerrain, anchors, betweenCap, coords);
        } else if (dx == 0 && dz == 1) {
            for (int x = 0; x < width; x++) detected += scanRay(x, 0, dx, dz, stepLen, width, depth, naturalTerrain, anchors, betweenCap, coords);
        } else if (dx == 1 && dz == 1) {
            for (int z = 0; z < depth; z++) detected += scanRay(0, z, dx, dz, stepLen, width, depth, naturalTerrain, anchors, betweenCap, coords);
            for (int x = 1; x < width; x++) detected += scanRay(x, 0, dx, dz, stepLen, width, depth, naturalTerrain, anchors, betweenCap, coords);
        } else {
            for (int z = 0; z < depth; z++) detected += scanRay(0, z, dx, dz, stepLen, width, depth, naturalTerrain, anchors, betweenCap, coords);
            for (int x = 1; x < width; x++) detected += scanRay(x, depth - 1, dx, dz, stepLen, width, depth, naturalTerrain, anchors, betweenCap, coords);
        }
        return detected;
    }

    private static int scanRay(int sx, int sz, int dx, int dz, double stepLen, int width, int depth,
            int[] naturalTerrain, int[] anchors, double[] betweenCap, int[] coords) {
        int x = sx, z = sz, k = 0, lastK = -1, lastH = 0, detected = 0;
        while (x >= 0 && x < width && z >= 0 && z < depth) {
            int i = z * width + x;
            coords[k] = i;
            int a = anchors[i];
            if (a != NO_ANCHOR) {
                if (lastK >= 0) {
                    int steps = k - lastK;
                    double spanDist = steps * stepLen;
                    if (steps >= 2 && spanDist <= MAX_BRIDGE_SPAN) {
                        double maxExcess = -INF;
                        for (int m = 1; m < steps; m++) {
                            int ci = coords[lastK + m];
                            double line = lastH + (a - lastH) * ((double) m / steps);
                            double excess = naturalTerrain[ci] - line;
                            if (excess > maxExcess) maxExcess = excess;
                        }
                        if (maxExcess >= WALL_THRESHOLD) {
                            detected++;
                            for (int m = 1; m < steps; m++) {
                                int ci = coords[lastK + m];
                                double line = lastH + (a - lastH) * ((double) m / steps);
                                double edgeDist = Math.min(m, steps - m) * stepLen;
                                double allowance = edgeDist <= 2.5 ? 0.0 : Math.min(1.5, (edgeDist - 2.5) * 0.15);
                                double cap = line + allowance;
                                if (cap < betweenCap[ci]) betweenCap[ci] = cap;
                            }
                        }
                    }
                }
                lastK = k;
                lastH = a;
            }
            x += dx;
            z += dz;
            k++;
        }
        return detected;
    }

    private static double[] propagateMin(int width, int depth, double[] init, double rate) {
        int n = width * depth;
        double[] dist = init.clone();
        MinHeap heap = new MinHeap(n);
        for (int i = 0; i < n; i++) {
            if (dist[i] < INF) heap.push(i, dist[i]);
        }
        while (!heap.isEmpty()) {
            int u = heap.popIndex();
            double val = heap.lastVal;
            if (val > dist[u] + 1e-6) continue;
            int ux = u % width, uz = u / width;
            for (int d = 0; d < 8; d++) {
                int nx = ux + DIRS8[d][0], nz = uz + DIRS8[d][1];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) continue;
                int v = nz * width + nx;
                double cand = val + rate * DIRS8_LEN[d];
                if (cand < dist[v] - 1e-6) {
                    dist[v] = cand;
                    heap.push(v, cand);
                }
            }
        }
        return dist;
    }

    private static double[] propagateMax(int width, int depth, double[] init, double rate) {
        int n = width * depth;
        double[] flr = init.clone();
        MinHeap heap = new MinHeap(n);
        for (int i = 0; i < n; i++) {
            if (flr[i] > -INF) heap.push(i, -flr[i]);
        }
        while (!heap.isEmpty()) {
            int u = heap.popIndex();
            double val = -heap.lastVal;
            if (val < flr[u] - 1e-6) continue;
            int ux = u % width, uz = u / width;
            for (int d = 0; d < 8; d++) {
                int nx = ux + DIRS8[d][0], nz = uz + DIRS8[d][1];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) continue;
                int v = nz * width + nx;
                double cand = val - rate * DIRS8_LEN[d];
                if (cand > flr[v] + 1e-6) {
                    flr[v] = cand;
                    heap.push(v, -cand);
                }
            }
        }
        return flr;
    }

    /** 无装箱二进制最小堆，保证 224x224 网格多次传播在毫秒级完成。 */
    private static final class MinHeap {
        private int[] idx;
        private double[] val;
        private int size;
        double lastVal;

        MinHeap(int initialCapacity) {
            int cap = Math.max(16, initialCapacity);
            idx = new int[cap];
            val = new double[cap];
        }

        boolean isEmpty() { return size == 0; }

        void push(int i, double v) {
            if (size == idx.length) {
                int nextCap = idx.length * 2;
                idx = Arrays.copyOf(idx, nextCap);
                val = Arrays.copyOf(val, nextCap);
            }
            int k = size++;
            while (k > 0) {
                int p = (k - 1) >>> 1;
                if (val[p] <= v) break;
                idx[k] = idx[p];
                val[k] = val[p];
                k = p;
            }
            idx[k] = i;
            val[k] = v;
        }

        int popIndex() {
            int res = idx[0];
            lastVal = val[0];
            int n = --size;
            if (n > 0) {
                int lastIdx = idx[n];
                double lastV = val[n];
                int k = 0, half = n >>> 1;
                while (k < half) {
                    int child = (k << 1) + 1;
                    int right = child + 1;
                    if (right < n && val[right] < val[child]) child = right;
                    if (lastV <= val[child]) break;
                    idx[k] = idx[child];
                    val[k] = val[child];
                    k = child;
                }
                idx[k] = lastIdx;
                val[k] = lastV;
            }
            return res;
        }
    }
}
