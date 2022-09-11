package io.github.skippi.hordetest.gravity;

import org.bukkit.Chunk;
import org.bukkit.block.Block;

public class ChunkPos {
  public int x;
  public int z;

  public ChunkPos(int x, int z) {
    this.x = x;
    this.z = z;
  }

  public static ChunkPos from(Chunk chunk) {
    return new ChunkPos(chunk.getX(), chunk.getZ());
  }

  public static ChunkPos from(Block block) {
    return new ChunkPos(block.getX() >> 4, block.getZ() >> 4);
  }

  @Override
  public int hashCode() {
    int result = x;
    result = 31 * result + z;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ChunkPos other = (ChunkPos) obj;
    return x == other.x && z == other.z;
  }
}
