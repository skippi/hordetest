package io.github.skippi.hordetest.gravity;

import org.bukkit.Chunk;
import org.bukkit.block.Block;

public final class ChunkId {
  private final int id;

  public ChunkId(int id) {
    this.id = id;
  }

  public static ChunkId fromChunkPos(int x, int z) {
    final int prime = 31;
    return new ChunkId(prime * x + z);
  }

  public static ChunkId from(Chunk chunk) {
    return fromChunkPos(chunk.getX(), chunk.getZ());
  }

  public static ChunkId from(Block block) {
    return fromChunkPos(block.getX() >> 4, block.getZ() >> 4);
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final ChunkId other = (ChunkId) obj;
    if (id != other.id) return false;
    return true;
  }
}
