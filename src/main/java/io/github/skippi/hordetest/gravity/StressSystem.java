package io.github.skippi.hordetest.gravity;

import io.github.skippi.hordetest.Blocks;
import io.github.skippi.hordetest.HordeTestPlugin;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;

public class StressSystem {
  private final Map<ChunkPos, byte[]> chunkStresses = new HashMap<>();

  public void update(Block block, PhysicsScheduler physicsScheduler) {
    if (!block.getWorld().getWorldBorder().isInside(block.getLocation())) return;
    if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return;
    if (!isStressAware(block)) {
      clearStress(block);
      return;
    }
    byte newStress = computeNewStress(block);
    if (newStress >= 64) {
      physicsScheduler.schedule((s) -> Action.drop(block, physicsScheduler.size() > 1024));
    }
    if (getStress(block) != newStress) {
      setStress(block, newStress);
      physicsScheduler.schedule(new UpdateNeighborStressAction(block));
    }
  }

  public void loadChunk(Chunk chunk) {
    var data =
        chunk
            .getPersistentDataContainer()
            .get(HordeTestPlugin.stressKey, PersistentDataType.BYTE_ARRAY);
    if (data == null) return;
    chunkStresses.put(ChunkPos.from(chunk), data);
  }

  public void unloadChunk(Chunk chunk) {
    var data = chunkStresses.remove(ChunkPos.from(chunk));
    if (data == null) return;
    chunk
        .getPersistentDataContainer()
        .set(HordeTestPlugin.stressKey, PersistentDataType.BYTE_ARRAY, data);
  }

  private void clearStress(Block block) {
    getStressData(block)[getOrdinalIndex(block)] = 0;
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

  private byte[] getStressData(Block block) {
    return chunkStresses.computeIfAbsent(ChunkPos.from(block), c -> new byte[16 * 16 * 256]);
  }

  public byte getStress(Block block) {
    return getStressData(block)[getOrdinalIndex(block)];
  }

  private static final BlockFace[] HORIZONTAL_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private byte computeNewStress(Block block) {
    Block below = block.getRelative(BlockFace.DOWN);
    byte result = isBaseable(below) ? getStress(below) : 64;
    if (result == 0) {
      return result;
    }
    for (var face : HORIZONTAL_FACES) {
      var side = block.getRelative(face);
      if (!isBaseable(side)) continue;
      var stress = getStress(side);
      result = (byte) Math.min(result, stress + getStressWeight(side.getType()));
      if (stress == 0) {
        break;
      }
    }
    return result;
  }

  private void setStress(Block block, byte value) {
    byte[] stresses = getStressData(block);
    stresses[getOrdinalIndex(block)] = value;
  }

  private final Map<Material, Byte> weightMemo = new HashMap<>();

  private byte getStressWeight(Material mat) {
    return weightMemo.computeIfAbsent(
        mat,
        m -> {
          float weight = 1.0f / (mat.getHardness() + mat.getBlastResistance());
          weight = clamp(weight, 1 / 12f, 1f);
          return (byte) (weight * 64);
        });
  }

  private boolean isStressAware(Block block) {
    return block.getWorld().getWorldBorder().isInside(block.getLocation())
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
        && !block.isEmpty()
        && !isPermanentlyStable(block);
  }

  private boolean isPermanentlyStable(Block block) {
    return block.isLiquid() || Blocks.isLeaves(block) || block.getType() == Material.BEDROCK;
  }

  private boolean isBaseable(Block block) {
    return block.getWorld().getWorldBorder().isInside(block.getLocation())
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
        && !block.isEmpty()
        && !block.isLiquid()
        && block.getType() != Material.GRASS;
  }

  private float clamp(float value, float min, float max) {
    return Math.max(Math.min(value, max), min);
  }
}
