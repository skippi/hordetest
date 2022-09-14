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
  public String toString() {
    return "ChunkPos{x=" + x + ", z=" + z + "}";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + x;
    result = prime * result + z;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ChunkPos other = (ChunkPos) obj;
    if (x != other.x) return false;
    if (z != other.z) return false;
    return true;
  }
}
