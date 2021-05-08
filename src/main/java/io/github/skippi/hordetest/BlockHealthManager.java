package io.github.skippi.hordetest;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;

import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class BlockHealthManager {
    private final Map<Block, Double> blockHealths = new HashMap<>();
    private final Map<Block, Integer> breakIds = new HashMap<>();

    public BlockHealthManager() {}

    public void damage(Block block, double amount) {
        setHealth(block, getHealth(block) - amount);
        animateBlockBreak(block, (getMaxHealth(block) - getHealth(block)) / getMaxHealth(block));
    }

    private double getHealth(Block block) {
        return blockHealths.getOrDefault(block, getMaxHealth(block));
    }

    private void setHealth(Block block, double value) {
        if (block.isEmpty()) return;
        if (value > 0f) {
            blockHealths.put(block, value);
        } else {
            block.setType(Material.AIR);
            blockHealths.remove(block);
        }
    }

    private double getMaxHealth(Block block) {
        return Math.min(15, block.getType().getBlastResistance());
    }

    private void animateBlockBreak(Block block, double percent) {
        int id = breakIds.computeIfAbsent(block, k -> RandomUtils.nextInt());
        int stage = percent > 0 ? (int)(percent * 9) : 10;
        PacketContainer packet = HordeTestPlugin.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
        packet.getIntegers().write(0, id).write(1, stage);
        packet.getBlockPositionModifier().write(0, new BlockPosition(block.getLocation().toVector()));
        for (Player player : block.getWorld().getPlayers()) {
            try {
                HordeTestPlugin.getProtocolManager().sendServerPacket(player, packet);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        if (stage == 10) {
            breakIds.remove(block);
        }
    }
}
