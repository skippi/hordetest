package io.github.skippi.hordetest.gravity;

import org.bukkit.Chunk;
import org.bukkit.block.Block;

public final class ChunkId {
  private final int x;
  private final int z;

  public ChunkId(int x, int z) {
    this.x = x;
    this.z = z;
  }

  public static ChunkId fromChunkPos(int x, int z) {
    return new ChunkId(x, z);
  }

  public static ChunkId from(Chunk chunk) {
    return fromChunkPos(chunk.getX(), chunk.getZ());
  }

  public static ChunkId from(Block block) {
    return fromChunkPos(block.getX() >> 4, block.getZ() >> 4);
  }

  @Override
  public int hashCode() {
    return 31 * x + z;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final ChunkId other = (ChunkId) obj;
    if (x != other.x) return false;
    if (z != other.z) return false;
    return true;
  }
}
