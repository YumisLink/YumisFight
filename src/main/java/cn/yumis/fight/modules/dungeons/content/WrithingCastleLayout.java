package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.WrithingCastle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authored 200-layer Writhing Sky Castle terrain and architecture loader.
 * Writes compact chunk-section data directly into LevelChunks to avoid 6.4M per-block updates.
 */
public final class WrithingCastleLayout {
    public static final String VERSION_PREFIX = "WRITHING_CASTLE_LAYOUT_";
    public static final String VERSION = VERSION_PREFIX + "1";
    public static final String MAP = "/data/yumis_dungeon_and_fight/dungeon_map/writhing_castle.nbt";
    private static final int FORMAT_VERSION = 1;
    private static final int MIN_CX = -12, MAX_CX = 11, MIN_CZ = -12, MAX_CZ = 11;
    private static final int MIN_CY = -1, MAX_CY = 19;

    /** Relative to the shared cell origin (cell * 512, 80, 0), mapping 1:1 to absolute Y -16..317. */
    public static final DungeonLayouts.Bounds BOUNDS = new DungeonLayouts.Bounds(
        WrithingCastle.MIN_X, WrithingCastle.MIN_Y - WrithingCastle.CELL_ORIGIN_Y, WrithingCastle.MIN_Z,
        WrithingCastle.MAX_X, WrithingCastle.MAX_Y - WrithingCastle.CELL_ORIGIN_Y, WrithingCastle.MAX_Z
    );

    /** Core sanctuary spire and south approach bridge kept force-loaded while players are inside. */
    public static final DungeonLayouts.Bounds ACTIVE_BOUNDS = new DungeonLayouts.Bounds(
        -64, WrithingCastle.MIN_Y - WrithingCastle.CELL_ORIGIN_Y, -64,
        63, WrithingCastle.MAX_Y - WrithingCastle.CELL_ORIGIN_Y, 191
    );

    private WrithingCastleLayout() {}

    public static DungeonLayouts.Layout layout() {
        return new DungeonLayouts.Layout(VERSION, BOUNDS, 0, 1, WrithingCastleLayout::build,
            (world, origin, gate, open) -> {}, ACTIVE_BOUNDS);
    }

    public static boolean recorded(String marker) {
        return marker != null && marker.contains(VERSION_PREFIX);
    }

    /** Keeps the central 8x8 citadel chunks and the 2x8 southern spawn approach bridge loaded. */
    public static boolean keepsChunk(int relativeChunkX, int chunkZ) {
        return relativeChunkX >= -4 && relativeChunkX <= 3 && chunkZ >= -4 && chunkZ <= 3
            || relativeChunkX >= -1 && relativeChunkX <= 0 && chunkZ >= 4 && chunkZ <= 11;
    }

    public static CompoundTag readMapResource() {
        try (InputStream source = WrithingCastleLayout.class.getResourceAsStream(MAP)) {
            if (source == null) throw new IllegalStateException("缺少蠕动之城地图结构：" + MAP);
            return NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
        } catch (IOException failure) {
            throw new IllegalStateException("读取蠕动之城地图结构失败", failure);
        }
    }

    public static void build(ServerLevel world, BlockPos origin) {
        int baseY = origin.getY() - WrithingCastle.CELL_ORIGIN_Y;
        if ((origin.getX() & 15) != 0 || (origin.getZ() & 15) != 0 || (baseY & 15) != 0) {
            throw new IllegalArgumentException("蠕动之城场次元点必须按区块(16格)对齐：" + origin);
        }
        CompoundTag data = readMapResource();
        if (data.getIntOr("version", 0) != FORMAT_VERSION) {
            throw new IllegalStateException("蠕动之城地图格式版本不受支持");
        }
        int minX = data.getIntOr("minX", 0), minY = data.getIntOr("minY", 0), minZ = data.getIntOr("minZ", 0);
        int maxX = data.getIntOr("maxX", -1), maxY = data.getIntOr("maxY", -1), maxZ = data.getIntOr("maxZ", -1);
        if (minX < WrithingCastle.MIN_X || maxX > WrithingCastle.MAX_X
            || minY < WrithingCastle.MIN_Y || maxY > WrithingCastle.MAX_Y
            || minZ < WrithingCastle.MIN_Z || maxZ > WrithingCastle.MAX_Z) {
            throw new IllegalStateException("蠕动之城地图结构超出声明边界");
        }
        if (baseY + minY < world.getMinY() || baseY + maxY > world.getMaxY()) {
            throw new IllegalStateException("当前维度高度范围无法容纳蠕动之城：" + (baseY + minY) + ".." + (baseY + maxY));
        }

        var blockRegistry = world.registryAccess().lookupOrThrow(Registries.BLOCK);
        var paletteList = data.getListOrEmpty("palette");
        if (paletteList.isEmpty()) throw new IllegalStateException("蠕动之城地图调色板为空");
        BlockState[] palette = new BlockState[paletteList.size()];
        for (int i = 0; i < paletteList.size(); i++) {
            palette[i] = NbtUtils.readBlockState(blockRegistry, paletteList.getCompoundOrEmpty(i));
        }

        Map<Long, List<CompoundTag>> sectionsByChunk = new HashMap<>();
        var sectionsList = data.getListOrEmpty("sections");
        for (int i = 0; i < sectionsList.size(); i++) {
            CompoundTag sec = sectionsList.getCompoundOrEmpty(i);
            int cx = sec.getByteOr("cx", (byte) 0);
            int cy = sec.getByteOr("cy", (byte) 0);
            int cz = sec.getByteOr("cz", (byte) 0);
            if (cx < MIN_CX || cx > MAX_CX || cz < MIN_CZ || cz > MAX_CZ || cy < MIN_CY || cy > MAX_CY) {
                throw new IllegalStateException("蠕动之城子区块坐标越界：" + cx + "," + cy + "," + cz);
            }
            sectionsByChunk.computeIfAbsent(ChunkPos.pack(cx, cz), ignored -> new ArrayList<>()).add(sec);
        }

        Map<Long, List<CompoundTag>> entitiesByChunk = new HashMap<>();
        var entitiesList = data.getListOrEmpty("blockEntities");
        for (int i = 0; i < entitiesList.size(); i++) {
            CompoundTag be = entitiesList.getCompoundOrEmpty(i);
            int rx = be.getIntOr("x", 0), ry = be.getIntOr("y", 0), rz = be.getIntOr("z", 0);
            if (rx < WrithingCastle.MIN_X || rx > WrithingCastle.MAX_X
                || ry < WrithingCastle.MIN_Y || ry > WrithingCastle.MAX_Y
                || rz < WrithingCastle.MIN_Z || rz > WrithingCastle.MAX_Z) {
                continue;
            }
            entitiesByChunk.computeIfAbsent(ChunkPos.pack(rx >> 4, rz >> 4), ignored -> new ArrayList<>()).add(be);
        }

        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        int baseSectionY = baseY >> 4;
        var touched = new ArrayList<LevelChunk>(sectionsByChunk.size());

        for (var entry : sectionsByChunk.entrySet()) {
            long packed = entry.getKey();
            int relCx = ChunkPos.getX(packed);
            int relCz = ChunkPos.getZ(packed);
            LevelChunk chunk = world.getChunk(originChunkX + relCx, originChunkZ + relCz);

            for (CompoundTag secTag : entry.getValue()) {
                int relCy = secTag.getByteOr("cy", (byte) 0);
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(baseSectionY + relCy));
                if (secTag.contains("uniform")) {
                    int id = secTag.getShortOr("uniform", (short) 0) & 0xFFFF;
                    BlockState fillState = palette[id];
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                section.setBlockState(lx, ly, lz, fillState, false);
                            }
                        }
                    }
                } else {
                    var rawBytes = secTag.getByteArray("blocks");
                    if (rawBytes.isPresent()) {
                        byte[] blocks = rawBytes.get();
                        if (blocks.length != 4096) throw new IllegalStateException("蠕动之城子区块字节数组长度无效");
                        int idx = 0;
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                for (int lx = 0; lx < 16; lx++, idx++) {
                                    int id = blocks[idx] & 0xFF;
                                    if (id != 0 || !section.getBlockState(lx, ly, lz).isAir()) {
                                        section.setBlockState(lx, ly, lz, palette[id], false);
                                    }
                                }
                            }
                        }
                    } else {
                        int[] blocks = secTag.getIntArray("blocksInt")
                            .orElseThrow(() -> new IllegalStateException("蠕动之城子区块缺少方块数据"));
                        if (blocks.length != 4096) throw new IllegalStateException("蠕动之城子区块整型数组长度无效");
                        int idx = 0;
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                for (int lx = 0; lx < 16; lx++, idx++) {
                                    int id = blocks[idx];
                                    if (id != 0 || !section.getBlockState(lx, ly, lz).isAir()) {
                                        section.setBlockState(lx, ly, lz, palette[id], false);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Heightmap.primeHeightmaps(chunk, chunk.getPersistedStatus().heightmapsAfter());
            List<CompoundTag> chunkEntities = entitiesByChunk.get(packed);
            if (chunkEntities != null) {
                for (CompoundTag rawBe : chunkEntities) {
                    int wx = origin.getX() + rawBe.getIntOr("x", 0);
                    int wy = baseY + rawBe.getIntOr("y", 0);
                    int wz = origin.getZ() + rawBe.getIntOr("z", 0);
                    BlockPos pos = new BlockPos(wx, wy, wz);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;
                    CompoundTag shifted = rawBe.copy();
                    shifted.putInt("x", wx);
                    shifted.putInt("y", wy);
                    shifted.putInt("z", wz);
                    BlockEntity entity = BlockEntity.loadStatic(pos, state, shifted, world.registryAccess());
                    if (entity != null) chunk.setBlockEntity(entity);
                }
            }
            chunk.markUnsaved();
            touched.add(chunk);
        }

        // Relight after all 576 chunks are placed so cross-chunk skylight and blocklight propagate cleanly.
        var lightEngine = world.getChunkSource().getLightEngine();
        for (LevelChunk chunk : touched) {
            chunk.setLightCorrect(false);
            lightEngine.initializeLight(chunk, false);
        }
        for (LevelChunk chunk : touched) {
            lightEngine.lightChunk(chunk, false);
        }
        lightEngine.tryScheduleUpdate();
    }

    /** Fast section-level clear when an existing Writhing Castle cell is recycled for another layout. */
    public static void clear(ServerLevel world, BlockPos origin) {
        int baseY = origin.getY() - WrithingCastle.CELL_ORIGIN_Y;
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        int baseSectionY = baseY >> 4;
        BlockState air = Blocks.AIR.defaultBlockState();
        var lightEngine = world.getChunkSource().getLightEngine();
        var cleared = new ArrayList<LevelChunk>();

        for (int relCx = MIN_CX; relCx <= MAX_CX; relCx++) {
            for (int relCz = MIN_CZ; relCz <= MAX_CZ; relCz++) {
                LevelChunk chunk = world.getChunk(originChunkX + relCx, originChunkZ + relCz);
                boolean modified = false;
                for (BlockPos bePos : List.copyOf(chunk.getBlockEntitiesPos())) {
                    if (bePos.getY() >= baseY + WrithingCastle.MIN_Y && bePos.getY() <= baseY + WrithingCastle.MAX_Y) {
                        chunk.removeBlockEntity(bePos);
                        modified = true;
                    }
                }
                for (int relCy = MIN_CY; relCy <= MAX_CY; relCy++) {
                    int worldCy = baseSectionY + relCy;
                    int sectionIndex = chunk.getSectionIndexFromSectionY(worldCy);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) continue;
                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section.hasOnlyAir()) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                if (!section.getBlockState(lx, ly, lz).isAir()) {
                                    section.setBlockState(lx, ly, lz, air, false);
                                }
                            }
                        }
                    }
                    modified = true;
                }
                if (modified) {
                    Heightmap.primeHeightmaps(chunk, chunk.getPersistedStatus().heightmapsAfter());
                    chunk.markUnsaved();
                    cleared.add(chunk);
                }
            }
        }
        for (LevelChunk chunk : cleared) {
            chunk.setLightCorrect(false);
            lightEngine.initializeLight(chunk, false);
            lightEngine.lightChunk(chunk, false);
        }
        if (!cleared.isEmpty()) lightEngine.tryScheduleUpdate();
    }
}
