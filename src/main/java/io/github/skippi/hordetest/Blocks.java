package io.github.skippi.hordetest;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class Blocks {
  public static BlockFace[] HORIZONTAL_FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  public static List<Block> getAdjacentBlocks(Block block) {
    return getRelativeBlocks(block, Arrays.asList(HORIZONTAL_FACES));
  }

  public static List<Block> getRelativeBlocks(Block block, List<BlockFace> faces) {
    return faces.stream().map(block::getRelative).collect(Collectors.toList());
  }

  public static boolean isLog(Block block) {
    return block.getType().toString().contains("_LOG");
  }

  public static boolean isLeaves(Block block) {
    return block.getType().toString().contains("_LEAVES");
  }
}
