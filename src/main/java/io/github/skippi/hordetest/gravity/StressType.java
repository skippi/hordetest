package io.github.skippi.hordetest.gravity;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.block.Block;

enum StressType {
  None,
  Aware,
  Permanent;

  public static Map<StressType, Byte> IDS = new HashMap<>();

  public static StressType from(Block block) {
    if (block.isEmpty()) {
      return None;
    } else if (StressSystem.isPermanentlyStable(block)) {
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
}
