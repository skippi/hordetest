package io.github.skippi.hordetest.gravity;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class FallAction implements Action {
  private Block block;

  public FallAction(Block block) {
    this.block = block;
  }

  @Override
  public double call(PhysicsScheduler physicsScheduler) {
    if (block.isEmpty()) return 0;
    if (physicsScheduler.size() > 512) {
      var curr = block.getRelative(BlockFace.DOWN);
      while (curr.getY() > 0 && canFall(curr)) {
        curr = curr.getRelative(BlockFace.DOWN);
      }
      curr.getRelative(BlockFace.UP).setBlockData(block.getBlockData(), false);
      block.setType(Material.AIR);
      return 0;
    }
    block
        .getWorld()
        .spawnFallingBlock(block.getLocation().clone().add(0.5, 0, 0.5), block.getBlockData());
    block.setType(Material.AIR);
    return 1 / 256;
  }

  private boolean canFall(Block block) {
    return block.isEmpty() || block.getType() == Material.FIRE || block.isLiquid();
  }
}
