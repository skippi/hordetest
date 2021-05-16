package io.github.skippi.hordetest;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Blocks {
    public static BlockFace[] HORIZONTAL_FACES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

    public static List<Block> getAdjacentBlocks(Block block) {
        return getRelativeBlocks(block, Arrays.asList(HORIZONTAL_FACES));
    }

    public static List<Block> getRelativeBlocks(Block block, List<BlockFace> faces) {
        return faces.stream().map(block::getRelative).collect(Collectors.toList());
    }
}
