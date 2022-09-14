package io.github.skippi.hordetest.gravity;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

@FunctionalInterface
public interface Action {
  double call(PhysicsScheduler physicsScheduler);

  public static double drop(Block block, boolean fast) {
    if (block.isEmpty()) return 0;
    if (fast) {
      var curr = block.getRelative(BlockFace.DOWN);
      while (curr.getY() > 0 && canFallThrough(curr)) {
        curr = curr.getRelative(BlockFace.DOWN);
      }
      curr.getRelative(BlockFace.UP).setBlockData(block.getBlockData(), true);
      block.setType(Material.AIR);
      return 1 / 256;
    }
    block
        .getWorld()
        .spawnFallingBlock(block.getLocation().clone().add(0.5, 0, 0.5), block.getBlockData());
    block.setType(Material.AIR);
    return 1 / 64;
  }

  private static boolean canFallThrough(Block block) {
    return block.isEmpty() || block.isLiquid() || block.getType() == Material.FIRE;
  }
}
