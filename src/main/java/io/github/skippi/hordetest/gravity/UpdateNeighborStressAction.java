package io.github.skippi.hordetest.gravity;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class UpdateNeighborStressAction implements Action {
  private final Block block;

  public UpdateNeighborStressAction(Block block) {
    this.block = block;
  }

  private static BlockFace[] FACES_TO_CHECK = {
    BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP
  };

  @Override
  public double call(PhysicsScheduler physicsScheduler) {
    for (var face : FACES_TO_CHECK) {
      physicsScheduler.schedule(new UpdateStressAction(block.getRelative(face)));
    }
    return 0;
  }
}
