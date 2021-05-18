package io.github.skippi.hordetest;

import com.google.common.primitives.Ints;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AI {
    public static void init(LivingEntity entity) {
        if (entity instanceof Monster) {
            AI.addAutoTargetAI((Creature) entity);
        }
        if (entity instanceof IronGolem) {
            AI.addTossAI((IronGolem) entity);
        } else if (entity instanceof Zombie) {
            Zombie zombie = (Zombie) entity;
            zombie.setAdult();
            AI.addClimbAI(zombie);
            AI.addDigAI(zombie);
            AI.addStepHeightAI(zombie);
            AI.addSpeedAI(zombie);
        } else if (entity instanceof Skeleton) {
            AI.addAttackAI((Skeleton) entity);
        } else if (entity instanceof Creeper) {
            AI.addPatienceAI((Creeper) entity);
        } else if (entity instanceof Spider) {
            AI.addDigAI((Spider) entity);
        }
    }

    public static void addStepHeightAI(Zombie zombie) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!zombie.isValid()) {
                    cancel();
                    return;
                }
                if (zombie.getTarget() == null) return;
                @NotNull Vector dir = zombie.getTarget().getLocation().subtract(zombie.getLocation()).toVector().normalize();
                @NotNull Block targetFeetBlock = zombie.getLocation().clone().add(dir).getBlock();
                if (targetFeetBlock.isSolid() && !targetFeetBlock.getRelative(BlockFace.UP).isSolid()) {
                    zombie.teleport(targetFeetBlock.getRelative(BlockFace.UP).getLocation());
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    public static void addDigAI(Spider spider) {
        new BukkitRunnable() {
            int cooldown = 0;
            @Override
            public void run() {
                if (!spider.isValid()) {
                    cancel();
                    return;
                }
                if (cooldown-- > 0) return;
                @Nullable LivingEntity target = spider.getTarget();
                if (target == null || spider.hasLineOfSight(target)) return;
                Optional<Block> maybeBlock = AI.findDigTargetBlocks(spider, target.getLocation().toVector())
                        .findFirst();
                if (maybeBlock.isPresent()) {
                    AI.attack(spider, maybeBlock.get(), 1);
                    cooldown = 40;
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0 ,1);
    }

    public static void addPatienceAI(Creeper creeper) {
        new BukkitRunnable() {
            int timer = 0;
            Location lastPos = creeper.getLocation();
            @Override
            public void run() {
                if (!creeper.isValid()) {
                    cancel();
                    return;
                }
                if (timer++ < 80) return;
                double dist = creeper.getLocation().distance(lastPos);
                if (dist < 3) {
                    creeper.ignite();
                }
                lastPos = creeper.getLocation();
                timer = 0;
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    public static void addAttackAI(Skeleton skeleton) {
        new BukkitRunnable() {
            int cooldown = 0;

            @Override
            public void run() {
                if (skeleton.isDead()) {
                    cancel();
                    return;
                }
                @Nullable LivingEntity target = skeleton.getTarget();
                if (target == null || skeleton.hasLineOfSight(target)) {
                    skeleton.setAI(true);
                } else {
                    skeleton.setAI(skeleton.getLocation().distance(target.getLocation()) > 20);
                    --cooldown;
                    if (cooldown <= 0) {
                        skeleton.rangedAttack(target, 1);
                        cooldown = 30;
                    }
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

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
        double horzSpeed = 1.2;
        double time = entity.getLocation().toVector().setY(0).distance(to.toVector().setY(0)) / horzSpeed;
        new BukkitRunnable() {
            double vy = (to.getY() - entity.getLocation().getY() - 2) / time - 0.5 * gravity * time;
            Vector horzDir = to.clone().subtract(entity.getLocation()).toVector().setY(0).normalize();

            @Override
            public void run() {
                if (!entity.isValid()) {
                    cancel();
                    return;
                }
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

    public static void addAutoTargetAI(Creature creature) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!creature.isValid()) {
                    cancel();
                    return;
                }
                if (creature.getTarget() == null || !creature.getTarget().isValid()) {
                    getNearestHumanTarget(creature.getLocation()).ifPresent(creature::setTarget);
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    public static void addClimbAI(Zombie zombie) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!zombie.isValid()) {
                    cancel();
                    return;
                }
                double climbSpeed = 0.3 + 0.65 * (1 - Math.max(0, getExposureTime(zombie)) / 20.0);
                @Nullable LivingEntity target = zombie.getTarget();
                if (target == null) return;
                if (target.getLocation().getY() < zombie.getLocation().getY()) return;
                if (zombie.getWorld().getLivingEntities()
                        .stream()
                        .anyMatch(e -> e != zombie && e instanceof Zombie
                            && e.getLocation().getY() <= zombie.getLocation().getY()
                            && zombie.getBoundingBox().clone().expand(0.125, 0, 0.125).overlaps(e.getBoundingBox().clone().expand(0.125, 0.0, 0.125)))) {
                    AI.climb(zombie, target.getLocation().toVector(), climbSpeed);
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 4);
    }

    private static final Map<UUID, Integer> entityExposures = new HashMap<>();

    private static int getExposureTime(LivingEntity entity) {
        return entityExposures.getOrDefault(entity.getUniqueId(), 0);
    }

    private static void setExposureTime(LivingEntity entity, int value) {
        entityExposures.put(entity.getUniqueId(), value);
    }

    public static void cleanupExposure(UUID id) {
        entityExposures.remove(id);
    }

    public static void addSpeedAI(Zombie zombie) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!zombie.isValid()) {
                    cancel();
                    return;
                }
                boolean isInLight = zombie.getLocation().getBlock().getRelative(BlockFace.UP).getLightFromBlocks() > 5;
                setExposureTime(zombie, Ints.constrainToRange(getExposureTime(zombie) + (isInLight ? 1 : -1), -80, 20));
                AttributeInstance speedAttr = zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
                assert speedAttr != null;
                if (getExposureTime(zombie) <= 0) {
                    speedAttr.setBaseValue(0.75);
                } else {
                    speedAttr.setBaseValue(0.75 - 0.0175 * getExposureTime(zombie));
                }
                if ((zombie.isInWater() || zombie.isInLava()) && zombie.getTarget() != null && zombie.getTarget().getLocation().distance(zombie.getLocation()) > 1.5) {
                    @NotNull Vector dir = zombie.getTarget().getLocation().clone().subtract(zombie.getLocation()).toVector().normalize();
                    @NotNull Vector horz = dir.clone().setY(0).multiply(speedAttr.getValue() * 0.7);
                    zombie.setVelocity(horz.clone().setY(dir.getY() * 0.3));
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    public static void addArrowTurretAI(ArmorStand turret) {
        new BukkitRunnable() {
            LivingEntity target = null;
            int cooldown = 0;

            private boolean isTargettable(Entity e) {
                return e instanceof Creature && e.isValid() && e.getLocation().distance(turret.getLocation()) < 75 && turret.hasLineOfSight(e);
            }

            @Override
            public void run() {
                if (!turret.isValid()) {
                    cancel();
                    return;
                }
                if (!isTargettable(target)) {
                    target = turret.getWorld().getLivingEntities().stream()
                            .filter(this::isTargettable)
                            .min(Comparator.comparing(e -> turret.getLocation().distanceSquared(e.getLocation())))
                            .orElse(null);
                }
                if (target != null && cooldown-- <= 0) {
                    @NotNull Vector dir = target.getEyeLocation().clone().subtract(turret.getEyeLocation()).toVector().normalize();
                    @NotNull Arrow arrow = turret.launchProjectile(Arrow.class);
                    arrow.setVelocity(dir.clone().multiply(3));
                    arrow.setShooter(turret);
                    cooldown = 17 + RandomUtils.nextInt(3);
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    public static void addDigAI(Zombie zombie) {
        new BukkitRunnable() {
            int cooldown = 0;
            @Override
            public void run() {
                if (!zombie.isValid()) {
                    cancel();
                    return;
                }
                if (cooldown-- > 0) return;
                @Nullable LivingEntity target = zombie.getTarget();
                if (target == null) return;
                if (zombie.hasLineOfSight(target)) return;
                Optional<Block> maybeBlock = AI.findDigTargetBlocks(zombie, target.getLocation().toVector())
                        .findFirst();
                if (maybeBlock.isPresent()) {
                    AI.attack(zombie, maybeBlock.get(), 1);
                    cooldown = 40;
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
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
        blocks.add(entity.getLocation().getBlock());
        blocks.add(entity.getLocation().getBlock().getRelative(BlockFace.UP));
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
        return blocks.stream().filter(Block::isSolid);
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
