package io.github.skippi.hordetest.gravity;

import io.github.skippi.hordetest.Blocks;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.HashMap;
import java.util.Map;

public class StressSystem {
    private final Map<Block, Float> stressMap = new HashMap<>();

    public void update(Block block, PhysicsScheduler physicsScheduler) {
        if (!isStressAware(block)) {
            stressMap.remove(block);
            return;
        }
        WorldBorder border = block.getWorld().getWorldBorder();
        if (!border.isInside(block.getLocation())) return;
        float newStress = computeNewStress(block);
        if (newStress >= 1.0) {
            physicsScheduler.schedule(new FallAction(block));
        }
        if (getStress(block) != newStress) {
            setStress(block, newStress);
            physicsScheduler.schedule(new UpdateNeighborStressAction(block));
        }
    }

    public float getStress(Block block) {
        if (isStressAware(block)) {
            return stressMap.getOrDefault(block, 0f);
        } else if (isPermanentlyStable(block)) {
            return 0.0f;
        } else {
            return 1.0f;
        }
    }

    private float computeNewStress(Block block) {
        if (!isStressAware(block)) return 0f;
        Block below = block.getRelative(BlockFace.DOWN);
        float result = isBaseable(below) ? getStress(below) : 1.0f;
        for (Block side : Blocks.getAdjacentBlocks(block)) {
            if (!isBaseable(side)) continue;
            result = Math.min(result, getStress(side) + getStressWeight(side.getType()));
        }
        return result;
    }

    private void setStress(Block block, float value) {
        if (!isStressAware(block)) return;
        stressMap.put(block, value);
    }

    private float getStressWeight(Material mat) {
        float weight = 1.0f / (mat.getHardness() + mat.getBlastResistance());
        return clamp(weight, 1 / 12f, 1f);
    }

    private boolean isStressAware(Block block) {
        WorldBorder border = block.getWorld().getWorldBorder();
        return !block.isEmpty() && !isPermanentlyStable(block) && border.isInside(block.getLocation());
    }

    private boolean isPermanentlyStable(Block block) {
        Material mat = block.getType();
        return mat == Material.BEDROCK || block.isLiquid();
    }

    private boolean isBaseable(Block block) {
        Material mat = block.getType();
        return (!block.isEmpty() && !block.isLiquid() && mat != Material.GRASS);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(Math.min(value, max), min);
    }
}
