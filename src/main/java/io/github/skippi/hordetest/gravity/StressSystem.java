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
  private final Map<ChunkPos, byte[]> chunkStressDatas = new HashMap<>();

  public void update(Block block, PhysicsScheduler physicsScheduler) {
    if (!chunkStressDatas.containsKey(ChunkPos.from(block))) return;
    var data = StressData.DEFAULT_VALUE;
    data = StressData.type(data, StressType.from(block));
    data = StressData.baseable(data, isBaseable(block));
    if (StressData.type(data) == StressType.Permanent) {
      data = StressData.stress(data, 0f);
    } else if (StressData.type(data) == StressType.Aware) {
      data = StressData.stress(data, computeNewStress(block));
    }
    if (StressData.type(data) == StressType.Aware && StressData.stress(data) >= 1f) {
      physicsScheduler.schedule(s -> Action.drop(block, s.size() > 512));
    }
    if (data == getStressData(block)) return;
    setStressData(block, data);
    physicsScheduler.schedule(new UpdateNeighborStressAction(block));
  }

  public void loadChunk(Chunk chunk) {
    if (chunkStressDatas.containsKey(ChunkPos.from(chunk))) return;
    var data =
        chunk
            .getPersistentDataContainer()
            .get(HordeTestPlugin.stressKey, PersistentDataType.BYTE_ARRAY);
    if (data != null) {
      chunkStressDatas.put(ChunkPos.from(chunk), data);
    }
    final var height = chunk.getWorld().getMaxHeight() - chunk.getWorld().getMinHeight() + 1;
    data = new byte[16 * 16 * height];
    Arrays.fill(data, StressData.DEFAULT_VALUE);
    chunkStressDatas.put(ChunkPos.from(chunk), data);
    for (int j = chunk.getWorld().getMinHeight(); j < chunk.getWorld().getMaxHeight(); ++j) {
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
    final var data = chunkStressDatas.remove(ChunkPos.from(chunk));
    if (data == null) return;
    chunk
        .getPersistentDataContainer()
        .set(HordeTestPlugin.stressKey, PersistentDataType.BYTE_ARRAY, data);
  }

  private int getOrdinalIndex(Block block) {
    final int relX = block.getX() & 0xF;
    final int relY = block.getY() - block.getWorld().getMinHeight();
    final int relZ = block.getZ() & 0xF;
    return getOrdinalIndex(relX, relY, relZ);
  }

  private int getOrdinalIndex(int chunkX, int chunkY, int chunkZ) {
    return chunkX + 16 * chunkZ + 16 * 16 * chunkY;
  }

  public byte getStressData(Block block) {
    if (block.getY() < block.getWorld().getMinHeight()) {
      return StressData.stress(StressData.DEFAULT_VALUE, 0f);
    }
    final var chunkData = chunkStressDatas.get(ChunkPos.from(block));
    if (chunkData == null) {
      return StressData.DEFAULT_VALUE;
    }
    return chunkData[getOrdinalIndex(block)];
  }

  public void setStressData(Block block, byte data) {
    final var chunkData = chunkStressDatas.get(ChunkPos.from(block));
    if (chunkData == null) return;
    chunkData[getOrdinalIndex(block)] = data;
  }

  private static final BlockFace[] HORIZONTAL_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private float computeNewStress(Block block) {
    final var below = getStressData(block.getRelative(BlockFace.DOWN));
    var result = StressData.baseable(below) ? StressData.stress(below) : 1f;
    if (result == 0) {
      return result;
    }
    for (var face : HORIZONTAL_FACES) {
      final var side = block.getRelative(face);
      final var data = getStressData(side);
      if (!StressData.baseable(data)) continue;
      result = Math.min(result, StressData.stress(data) + getStressWeight(side.getType()));
      if (StressData.stress(data) == 0) {
        break;
      }
    }
    return result;
  }

  private final Map<Material, Float> weightMemo = new HashMap<>();

  private float getStressWeight(Material mat) {
    return weightMemo.computeIfAbsent(
        mat,
        m -> {
          float weight = 1.0f / (mat.getHardness() + mat.getBlastResistance());
          return clamp(weight, 1 / 12f, 3 / 12f);
        });
  }

  public static boolean isPermanentlyStable(Block block) {
    if (block.isLiquid()) return true;
    final var type = block.getType();
    return type == Material.OAK_LEAVES
        || type == Material.SPRUCE_LEAVES
        || type == Material.BIRCH_LEAVES
        || type == Material.JUNGLE_LEAVES
        || type == Material.ACACIA_LEAVES
        || type == Material.DARK_OAK_LEAVES
        || type == Material.MANGROVE_LEAVES
        || type == Material.AZALEA_LEAVES
        || type == Material.BEDROCK
        || type == Material.FIRE
        || type == Material.WALL_TORCH
        || type == Material.REDSTONE_WALL_TORCH
        || type == Material.SOUL_WALL_TORCH;
  }

  public boolean isBaseable(Block block) {
    return block.isSolid();
  }

  public static float clamp(float value, float min, float max) {
    return Math.max(Math.min(value, max), min);
  }
}
