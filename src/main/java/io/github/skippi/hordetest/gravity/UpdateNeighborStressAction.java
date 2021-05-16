package io.github.skippi.hordetest.gravity;

import io.github.skippi.hordetest.Blocks;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Arrays;

public class UpdateNeighborStressAction implements Action {
    private final Block block;

    public UpdateNeighborStressAction(Block block) {
        this.block = block;
    }

    @Override
    public double getWeight() {
        return 0;
    }

    @Override
    public void call(PhysicsScheduler physicsScheduler) {
        BlockFace[] facesToCheck = {
            BlockFace.WEST,
            BlockFace.EAST,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.UP
        };
        for (Block neighbor : Blocks.getRelativeBlocks(block, Arrays.asList(facesToCheck))) {
            if (!neighbor.getWorld().getWorldBorder().isInside(neighbor.getLocation())) continue;
            physicsScheduler.schedule(new UpdateStressAction(neighbor));
        }
    }
}
