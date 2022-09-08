package io.github.skippi.hordetest.gravity;

import io.github.skippi.hordetest.Blocks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class StressSystem {
  private final Map<ChunkPos, float[]> chunkStresses = new HashMap<>();
  private final Set<Block> visited = new HashSet<>();

  public void update(Block block, PhysicsScheduler physicsScheduler) {
    if (visited.contains(block)) {
      return;
    }
    if (!isStressAware(block)) {
      clearStress(block);
      return;
    }
    WorldBorder border = block.getWorld().getWorldBorder();
    if (!border.isInside(block.getLocation())) return;
    float newStress = computeNewStress(block);
    if (newStress >= 1.0) {
      physicsScheduler.schedule(new FallAction(block));
    }
    if (getStress(block) != newStress) {
      setStress(block, newStress);
      physicsScheduler.schedule(new UpdateNeighborStressAction(block));
    }
  }

  public void resetHistory() {
    visited.clear();
  }

  private void clearStress(Block block) {
    float[] stresses = getStressData(block);
    stresses[getOrdinalIndex(block)] = 0f;
  }

  private int getOrdinalIndex(Block block) {
    int relX = block.getX() & 0xF;
    int relY = block.getY() & 0xFF;
    int relZ = block.getZ() & 0xF;
    return getOrdinalIndex(relX, relY, relZ);
  }

  private int getOrdinalIndex(int chunkX, int chunkY, int chunkZ) {
    return chunkX + 16 * chunkZ + 16 * 16 * chunkY;
  }

  private float[] getStressData(Block block) {
    return chunkStresses.computeIfAbsent(ChunkPos.from(block), c -> new float[16 * 16 * 256]);
  }

  public float getStress(Block block) {
    float[] stresses = getStressData(block);
    return stresses[getOrdinalIndex(block)];
  }

  private static final BlockFace[] HORIZONTAL_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private float computeNewStress(Block block) {
    if (!isStressAware(block)) return 0f;
    Block below = block.getRelative(BlockFace.DOWN);
    float result = isBaseable(below) ? getStress(below) : 1.0f;
    if (result == 0f) {
      return result;
    }
    for (var face : HORIZONTAL_FACES) {
      var side = block.getRelative(face);
      if (!isBaseable(side)) continue;
      var stress = getStress(side);
      result = Math.min(result, stress + getStressWeight(side.getType()));
      if (stress == 0f) {
        break;
      }
    }
    return result;
  }

  private void setStress(Block block, float value) {
    if (!isStressAware(block)) return;
    float[] stresses = getStressData(block);
    stresses[getOrdinalIndex(block)] = value;
  }

  private final Map<Material, Float> weightMemo = new HashMap<>();

  private float getStressWeight(Material mat) {
    return weightMemo.computeIfAbsent(
        mat,
        m -> {
          float weight = 1.0f / (mat.getHardness() + mat.getBlastResistance());
          weight = clamp(weight, 1 / 12f, 1f);
          return weight;
        });
  }

  private boolean isStressAware(Block block) {
    WorldBorder border = block.getWorld().getWorldBorder();
    return border.isInside(block.getLocation())
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
        && !block.isEmpty()
        && !isPermanentlyStable(block);
  }

  private boolean isPermanentlyStable(Block block) {
    return block.isLiquid() || Blocks.isLeaves(block) || block.getType() == Material.BEDROCK;
  }

  private boolean isBaseable(Block block) {
    return (!block.isEmpty() && !block.isLiquid() && block.getType() != Material.GRASS);
  }

  private float clamp(float value, float min, float max) {
    return Math.max(Math.min(value, max), min);
  }
}
