package io.github.skippi.hordetest.gravity;

import org.bukkit.Material;
import org.bukkit.block.Block;

enum StressType {
  None,
  Aware,
  Permanent;

  public static StressType from(Block block) {
    if (block.isEmpty()) {
      return None;
    } else if (isPermanentlyStable(block)) {
      return Permanent;
    }
    return Aware;
  }

  public byte id() {
    switch (this) {
      case None:
        return 0;
      case Aware:
        return 1;
      case Permanent:
        return 2;
    }
    throw new IllegalArgumentException("unreachable code");
  }

  private static boolean isPermanentlyStable(Block block) {
    if (block.isLiquid()) return true;
    final var type = block.getType();
    return type == Material.OAK_LEAVES
        || type == Material.SPRUCE_LEAVES
        || type == Material.BIRCH_LEAVES
        || type == Material.JUNGLE_LEAVES
        || type == Material.ACACIA_LEAVES
        || type == Material.DARK_OAK_LEAVES
        || type == Material.MANGROVE_LEAVES
        || type == Material.AZALEA_LEAVES
        || type == Material.VINE
        || type == Material.COCOA
        || type == Material.BEDROCK
        || type == Material.FIRE
        || type == Material.WALL_TORCH
        || type == Material.REDSTONE_WALL_TORCH
        || type == Material.SOUL_WALL_TORCH;
  }
}
