package io.github.skippi.hordetest.gravity;

import io.github.skippi.hordetest.Blocks;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class StressSystem {
  private final Map<ChunkPos, float[]> chunkStresses = new HashMap<>();

  public void update(Block block, PhysicsScheduler physicsScheduler) {
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

  private float computeNewStress(Block block) {
    if (!isStressAware(block)) return 0f;
    Block below = block.getRelative(BlockFace.DOWN);
    float result = isBaseable(below) ? getStress(below) : 1.0f;
    for (Block side : Blocks.getAdjacentBlocks(block)) {
      if (!isBaseable(side)) continue;
      result = Math.min(result, getStress(side) + getStressWeight(side.getType()));
    }
    return result;
  }

  private void setStress(Block block, float value) {
    if (!isStressAware(block)) return;
    float[] stresses = getStressData(block);
    stresses[getOrdinalIndex(block)] = value;
  }

  private float getStressWeight(Material mat) {
    float weight = 1.0f / (mat.getHardness() + mat.getBlastResistance());
    return clamp(weight, 1 / 12f, 1f);
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
