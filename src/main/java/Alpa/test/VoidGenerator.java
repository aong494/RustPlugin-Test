package Alpa.test;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import java.util.Random;

public class VoidGenerator extends ChunkGenerator {

    // 지형(돌, 흙 등) 생성을 차단
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // 비워둠으로써 공기만 생성
    }

    // 기반암(Bedrock) 생성을 차단
    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // 비워둠
    }

    // 동굴이나 지하 호수 생성을 차단
    @Override
    public boolean shouldGenerateCaves() { return false; }

    // 구조물(마을, 요새 등) 생성을 차단
    @Override
    public boolean shouldGenerateStructures() { return false; }

    // 꽃, 잔디, 나무 등 장식 생성을 차단
    @Override
    public boolean shouldGenerateDecorations() { return false; }
}