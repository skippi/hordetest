package io.github.skippi.hordetest.gravity;

import io.github.skippi.hordetest.HordeTestPlugin;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;

public class StressSystem {
  private final Map<ChunkId, byte[]> chunkStressDatas = new HashMap<>();

  private static BlockFace[] FACES_TO_CHECK = {
    BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP
  };

  public void update(Block block, PhysicsScheduler physicsScheduler, boolean typeCheck) {
    if (!chunkStressDatas.containsKey(ChunkId.from(block))) return;
    final var src = getStressData(block);
    var data = src;
    if (typeCheck) {
      data = StressData.type(data, StressType.from(block));
      data = StressData.baseable(data, isBaseable(block));
    }
    if (StressData.type(data) == StressType.Permanent) {
      data = StressData.stress(data, 0f);
    } else if (StressData.type(data) == StressType.Aware) {
      data = StressData.stress(data, computeNewStress(block));
    }
    if (StressData.type(data) == StressType.Aware && StressData.stress(data) >= 1f) {
      physicsScheduler.schedule(s -> Action.drop(block, s.size() > 512));
    }
    if (data == src) return;
    setStressData(block, data);
    for (var face : FACES_TO_CHECK) {
      physicsScheduler.schedule(
          (s) -> {
            HordeTestPlugin.SS.update(block.getRelative(face), physicsScheduler, false);
            return 0;
          });
    }
  }

  private static final int MIN_HEIGHT = -64;
  private static final int MAX_HEIGHT = 320; // exclusive

  public void loadChunk(Chunk chunk) {
    if (chunkStressDatas.containsKey(ChunkId.from(chunk))) return;
    var data =
        chunk
            .getPersistentDataContainer()
            .get(HordeTestPlugin.stressKey, PersistentDataType.BYTE_ARRAY);
    if (data != null) {
      chunkStressDatas.put(ChunkId.from(chunk), data);
      return;
    }
    data = new byte[16 * 16 * (MAX_HEIGHT - MIN_HEIGHT)];
    Arrays.fill(data, StressData.DEFAULT_VALUE);
    chunkStressDatas.put(ChunkId.from(chunk), data);
    for (int j = MIN_HEIGHT; j < MAX_HEIGHT; ++j) {
      for (int i = 0; i < 16; ++i) {
        for (int k = 0; k < 16; ++k) {
          final var block = chunk.getBlock(i, j, k);
          var sd = StressData.DEFAULT_VALUE;
          sd = StressData.type(sd, StressType.from(block));
          sd = StressData.baseable(sd, isBaseable(block));
          if (StressData.type(sd) == StressType.Permanent) {
            sd = StressData.stress(sd, 0f);
          } else if (StressData.type(sd) == StressType.Aware) {
            sd = StressData.stress(sd, 0f);
          }
          setStressData(block, sd);
        }
      }
    }
  }

  public void unloadChunk(Chunk chunk) {
    final var data = chunkStressDatas.remove(ChunkId.from(chunk));
    if (data == null) return;
    chunk
        .getPersistentDataContainer()
        .set(HordeTestPlugin.stressKey, PersistentDataType.BYTE_ARRAY, data);
  }

  private static int getOrdinalIndex(int blockX, int blockY, int blockZ) {
    final int chunkX = blockX & 0xF;
    final int chunkY = blockY - MIN_HEIGHT;
    final int chunkZ = blockZ & 0xF;
    return chunkX + 16 * chunkZ + 16 * 16 * chunkY;
  }

  private static int getOrdinalIndex(Block block) {
    return getOrdinalIndex(block.getX(), block.getY(), block.getZ());
  }

  public byte getStressData(Block block) {
    return getStressData(block.getX(), block.getY(), block.getZ());
  }

  private ChunkId id1 = null; 
  private byte[] cache1 = null; 

  public byte getStressData(int blockX, int blockY, int blockZ) {
    if (blockY < MIN_HEIGHT) {
      return StressData.stress(StressData.DEFAULT_VALUE, 0f);
    }
    final var id = ChunkId.fromChunkPos(blockX >> 4, blockZ >> 4);
    if (!id.equals(id1)) {
      id1 = id;
      cache1 = chunkStressDatas.get(id);
    }
    if (cache1 == null) {
      return StressData.DEFAULT_VALUE;
    }
    return cache1[getOrdinalIndex(blockX, blockY, blockZ)];
  }

  public void setStressData(Block block, byte data) {
    final var chunkData = chunkStressDatas.get(ChunkId.from(block));
    if (chunkData == null) return;
    chunkData[getOrdinalIndex(block)] = data;
  }

  private static final BlockFace[] HORIZONTAL_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private float computeNewStress(Block block) {
    final var below = getStressData(block.getX(), block.getY() - 1, block.getZ());
    var result = StressData.baseable(below) ? StressData.stress(below) : 1f;
    if (result == 0) {
      return result;
    }
    for (var face : HORIZONTAL_FACES) {
      final var data =
          getStressData(
              block.getX() + face.getModX(),
              block.getY() + face.getModY(),
              block.getZ() + face.getModZ());
      if (!StressData.baseable(data)) continue;
      result =
          Math.min(
              result, StressData.stress(data) + getStressWeight(block.getRelative(face).getType()));
      if (StressData.stress(data) == 0) {
        break;
      }
    }
    return result;
  }

  private final Map<Material, Float> weightMemo = new HashMap<>();
  private Material id2;
  private float cache2;
  private float getStressWeight(Material mat) {
    if (mat != id2) {
      id2 = mat;
      cache2 = weightMemo.computeIfAbsent(
          mat,
          m -> {
            float weight = 1.0f / (mat.getHardness() + mat.getBlastResistance());
            return clamp(weight, 1 / 12f, 3 / 12f);
          });
    }

    return cache2;
  }

  private static boolean isBaseable(Block block) {
    return block.isSolid();
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(Math.min(value, max), min);
  }
}
