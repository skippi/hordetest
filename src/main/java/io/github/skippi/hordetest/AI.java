package io.github.skippi.hordetest;

import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AI {
    public static void climb(Zombie zombie, Vector dest) {
        if (zombie.getEyeLocation().clone().add(0,  0.5, 0).getBlock().getType().isSolid()) return;
        zombie.teleport(zombie.getLocation().clone().add(0, 0.6, 0));
        @NotNull Vector climbDir = dest.clone().subtract(zombie.getLocation().toVector()).normalize();
        double moveSpeed = zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
        zombie.setVelocity(climbDir.clone().setY(0).multiply(moveSpeed));
        zombie.swingMainHand();
    }

    public static Stream<Block> findDigTargetBlocks(LivingEntity entity, Vector dest) {
        int x = entity.getLocation().getBlockX();
        int z = entity.getLocation().getBlockZ();
        @NotNull Vector dir = dest.clone().subtract(entity.getLocation().toVector()).normalize();
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
            x += (dir.getX() < 0) ? -1 : 1;
        } else {
            z += (dir.getZ() < 0) ? -1 : 1;
        }
        Block footBlock = entity.getWorld().getBlockAt(x, entity.getLocation().getBlockY(), z);
        @NotNull Block faceBlock = footBlock.getRelative(BlockFace.UP);
        List<Block> blocks = new ArrayList<>();
        blocks.add(faceBlock);
        if (dir.getY() > 0) {
            blocks.add(entity.getWorld().getBlockAt(entity.getEyeLocation()).getRelative(BlockFace.UP));
            blocks.add(faceBlock.getRelative(BlockFace.UP));
        } else if (dir.getY() < 0) {
            blocks.add(footBlock);
            blocks.add(footBlock.getRelative(BlockFace.DOWN));
        } else {
            blocks.add(footBlock);
        }
        return blocks.stream().filter(b -> !b.getType().isAir());
    }

    public static Stream<Block> findDigTargetBlocks(Spider spider, Vector dest) {
        @NotNull Vector dir = dest.subtract(spider.getLocation().toVector()).normalize();
        int x = spider.getLocation().getBlockX();
        int z = spider.getLocation().getBlockZ();
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
            x += (dir.getX() < 0) ? -1 : 1;
        } else {
            z += (dir.getZ() < 0) ? -1 : 1;
        }
        Block footBlock = spider.getWorld().getBlockAt(x, spider.getLocation().getBlockY(), z);
        @NotNull Block faceBlock = footBlock.getRelative(BlockFace.UP);
        List<Block> blocks = new ArrayList<>();
        if (dir.getY() > 0) {
            blocks.add(spider.getWorld().getBlockAt(spider.getEyeLocation()).getRelative(BlockFace.UP));
            blocks.add(faceBlock.getRelative(BlockFace.UP));
        } else if (dir.getY() < 0) {
            blocks.add(footBlock);
            blocks.add(footBlock.getRelative(BlockFace.DOWN));
        } else {
            blocks.add(footBlock);
        }
        blocks.add(faceBlock);
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
            List<Block> leftBlocks = blocks.stream().map(b -> b.getRelative(0, 0, -1)).collect(Collectors.toList());
            List<Block> rightBlocks = blocks.stream().map(b -> b.getRelative(0, 0, 1)).collect(Collectors.toList());
            blocks.addAll(leftBlocks);
            blocks.addAll(rightBlocks);
        } else {
            List<Block> leftBlocks = blocks.stream().map(b -> b.getRelative(-1, 0, 0)).collect(Collectors.toList());
            List<Block> rightBlocks = blocks.stream().map(b -> b.getRelative(1, 0, 0)).collect(Collectors.toList());
            blocks.addAll(leftBlocks);
            blocks.addAll(rightBlocks);
        }
        return blocks.stream().filter(b -> !b.getType().isAir());
    }

    public static void attack(LivingEntity entity, Block block, double damage) {
        entity.swingMainHand();
        entity.getWorld().playSound(block.getLocation(), block.getSoundGroup().getHitSound(), 0.5f, 0);
        HordeTestPlugin.getBlockHealthManager().damage(block, damage);
    }
}
