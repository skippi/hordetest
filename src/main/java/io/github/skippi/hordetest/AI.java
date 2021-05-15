package io.github.skippi.hordetest;

import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AI {
    public static void addTossAI(IronGolem golem) {
        new BukkitRunnable() {
            int cooldown = 0;
            Optional<Class<? extends LivingEntity>> pocket = Optional.empty();

            @Override
            public void run() {
                if (!golem.isValid()) {
                    cancel();
                    return;
                }
                cooldown--;
                if (!pocket.isPresent()) {
                    golem.setAI(true);
                    golem.getWorld().getLivingEntities().stream()
                            .filter(e -> e instanceof Creature && !(e instanceof IronGolem))
                            .min(Comparator.comparing(e -> e.getLocation().distanceSquared(golem.getLocation())))
                            .ifPresent(golem::setTarget);
                    if (cooldown <= 55 && golem.getTarget() != null && golem.getTarget().getLocation().distance(golem.getLocation()) < 3) {
                        pocket = Optional.of(golem.getTarget().getClass());
                        golem.getTarget().remove();
                        golem.setTarget(null);
                    }
                } else {
                    if (golem.getTarget() == null || !golem.getTarget().isValid()) {
                        getNearestHumanTarget(golem.getLocation()).ifPresent(golem::setTarget);
                    }
                    if (golem.getTarget() != null) {
                        double dist = golem.getTarget().getLocation().distance(golem.getLocation());
                        golem.setAI(dist > 100);
                        if (cooldown <= 0 && dist <= 100) {
                            LivingEntity yeeted = golem.getWorld().spawn(golem.getLocation().clone().add(0, 2, 0), pocket.get());
                            double areaDeviation = dist / 100 * 16;
                            @NotNull Location to = golem.getTarget().getLocation().clone().add(-areaDeviation / 2 + RandomUtils.nextFloat() * areaDeviation / 2, 0, -areaDeviation / 2 + RandomUtils.nextFloat() * areaDeviation / 2);
                            launch(yeeted, to);
                            pocket = Optional.empty();
                            golem.setTarget(null);
                            cooldown = 60;
                        }
                    }
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    private static void launch(LivingEntity entity, Location to) {
        entity.setCollidable(false);
        double gravity = -0.08;
        double horzSpeed = 0.6;
        double time = entity.getLocation().distance(to) / (horzSpeed * 2);
        new BukkitRunnable() {
            double vy = -gravity * time;
            Vector horzDir = to.clone().subtract(entity.getLocation()).toVector().setY(0).normalize();

            @Override
            public void run() {
                @NotNull Location newPos = entity.getLocation().clone().add(horzDir.clone().multiply(horzSpeed)).add(0, vy, 0);
                @NotNull Vector dir = newPos.toVector().subtract(entity.getLocation().toVector()).normalize();
                double innerDist = newPos.clone().distance(entity.getLocation());
                @Nullable RayTraceResult raytrace = entity.getWorld().rayTraceBlocks(entity.getLocation(), dir, innerDist);
                if ((raytrace != null && raytrace.getHitBlock() != null) || newPos.getY() < 0) {
                    entity.setCollidable(true);
                    cancel();
                    return;
                }
                entity.teleport(newPos);
                vy += gravity;
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    private static Optional<LivingEntity> getNearestHumanTarget(Location loc) {
        Stream<Player> players = loc.getWorld().getPlayers()
                .stream()
                .filter(p -> !p.getGameMode().equals(GameMode.CREATIVE));
        Stream<LivingEntity> turrets = loc.getWorld().getLivingEntities().stream()
                .filter(e -> e instanceof ArmorStand);
        return Stream.concat(players.map(p -> (LivingEntity) p), turrets)
                .min(Comparator.comparing(p -> p.getLocation().distanceSquared(loc)));
    }

    public static void climb(Zombie zombie, Vector dest, double climbSpeed) {
        @NotNull Location newLoc = zombie.getLocation().clone().add(0, climbSpeed, 0);
        if (newLoc.getBlock().isSolid() || newLoc.getBlock().getRelative(BlockFace.UP).isSolid() || newLoc.getBlock().getRelative(0, 2, 0).isSolid()) return;
        zombie.teleport(zombie.getLocation().clone().add(0, climbSpeed, 0));
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
