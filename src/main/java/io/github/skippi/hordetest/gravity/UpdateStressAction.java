package io.github.skippi.hordetest.gravity;

import io.github.skippi.hordetest.HordeTestPlugin;
import org.bukkit.block.Block;

public class UpdateStressAction implements Action {
    private final Block block;

    public UpdateStressAction(Block block) {
        this.block = block;
    }

    @Override
    public double getWeight() { return 0; }

    @Override
    public void call(PhysicsScheduler physicsScheduler) {
        HordeTestPlugin.SS.update(block, physicsScheduler);
    }
}
