package io.github.skippi.hordetest;

import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class Blocks {
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
