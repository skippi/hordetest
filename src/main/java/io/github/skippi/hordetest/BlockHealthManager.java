package io.github.skippi.hordetest;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;

import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BlockHealthManager {
    private final Map<Block, Double> blockHealths = new HashMap<>();
    private final Map<Block, Integer> breakIds = new HashMap<>();

    public BlockHealthManager() {}

    public void damage(Block block, double amount) {
        BlockPreDamageEvent event = new BlockPreDamageEvent(block, amount);
        Bukkit.getPluginManager().callEvent(event);
        setHealth(block, getHealth(block) - event.getDamage());
        animateBlockBreak(block, (getMaxHealth(block) - getHealth(block)) / getMaxHealth(block));
    }

    public void reset(Block block) {
        setHealth(block, getMaxHealth(block));
        animateBlockBreak(block, (getMaxHealth(block) - getHealth(block)) / getMaxHealth(block));
    }

    public double getHealth(Block block) {
        return blockHealths.getOrDefault(block, getMaxHealth(block));
    }

    private void setHealth(Block block, double value) {
        if (block.isEmpty()) return;
        if (value > 0f) {
            blockHealths.put(block, value);
        } else {
            block.getWorld().playSound(block.getLocation(), block.getSoundGroup().getBreakSound(), 1, 0);
            block.setType(Material.AIR);
            blockHealths.remove(block);
        }
    }

    public double getMaxHealth(Block block) {
        if (Arrays.asList(Material.SANDSTONE, Material.STONE, Material.DIORITE, Material.GRANITE).contains(block.getType())) {
            return 0.5;
        }
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
