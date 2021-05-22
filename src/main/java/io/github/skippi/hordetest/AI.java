package io.github.skippi.hordetest;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.google.common.primitives.Ints;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
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
            Spider spider = (Spider) entity;
            Bukkit.getMobGoals().removeGoal(spider, VanillaGoal.LEAP_AT_TARGET);
            addLeapAI(spider);
            addDigAI(spider);
        } else if (entity instanceof Phantom) {
            Phantom phantom = (Phantom) entity;
            addTorchBreakAI(phantom);
        }
    }

    private static void addLeapAI(Spider spider) {
        new BukkitRunnable() {
            int cooldown = 0;
            @Override
            public void run() {
                if (!spider.isValid()) {
                    cancel();
                    return;
                }
                if (cooldown-- > 0 || spider.getTarget() == null) return;
                double dist = spider.getTarget().getLocation().distance(spider.getLocation());
                if (4 <= dist && dist <= 15) {
                    @NotNull Location to = spider.getTarget().getLocation().clone();
                    double gravity = -0.4;
                    double heightDelta = to.getY() - spider.getLocation().getY();
                    double risingHeight = (heightDelta > 0) ? Math.max(4, heightDelta + 2) : Math.max(2, heightDelta + 4);
                    final double vy = Math.sqrt(Math.abs(2 * gravity * risingHeight));
                    double fallingTime = Math.sqrt(Math.abs(2 * (heightDelta - risingHeight) / gravity));
                    double risingTime = -(vy / gravity);
                    final Vector vh = to.toVector().setY(0).subtract(spider.getLocation().toVector().setY(0));
                    launch(spider, vh.clone().multiply(1 / (risingTime + fallingTime)).setY(vy), gravity);
                    cooldown = 20 * 4;
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    private static void addTorchBreakAI(Phantom phantom) {
        new BukkitRunnable() {
            Block target = null;
            ArmorStand dummyTarget = null;
            int cooldown = 0;

            @Override
            public void run() {
                if (!phantom.isValid()) {
                    cancel();
                    return;
                }
                if (target == null || !target.getType().equals(Material.TORCH)) {
                    target = HordeTestPlugin.torches.stream()
                            .min(Comparator.comparing(b -> b.getLocation().distanceSquared(phantom.getLocation())))
                            .orElse(null);
                    if (dummyTarget != null) {
                        dummyTarget.remove();
                        dummyTarget = null;
                    }
                }
                if (target != null && cooldown-- <= 0) {
                    if (dummyTarget == null) {
                        dummyTarget = phantom.getWorld().spawn(target.getLocation().clone().add(0.5, 0, 0.5), ArmorStand.class, e -> {
                            e.setCanMove(false);
                            e.setVisible(false);
                            e.setInvulnerable(false);
                            e.setMarker(true);
                        });
                    }
                    phantom.setTarget(dummyTarget);
                    if (phantom.getLocation().distance(target.getLocation()) < 2) {
                        @NotNull BlockData data = Bukkit.createBlockData(Material.AIR);
                        BlockDestroyEvent event = new BlockDestroyEvent(target, data, false);
                        Bukkit.getPluginManager().callEvent(event);
                        if (!event.isCancelled()) {
                            target.getWorld().dropItemNaturally(target.getLocation(), new ItemStack(Material.TORCH));
                            target.setBlockData(data);
                            target = null;
                            phantom.setTarget(null);
                            dummyTarget.remove();
                            dummyTarget = null;
                            cooldown = 20 * 5;
                        }
                    }
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
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
                if (timer++ < 80 || creeper.getTarget() == null) return;
                double dist = creeper.getLocation().distance(lastPos);
                if (dist < 1.5) {
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
                            @NotNull Location to = golem.getTarget().getLocation().clone().add(-areaDeviation / 2 + RandomUtils.nextFloat() * areaDeviation, 0, -areaDeviation / 2 + RandomUtils.nextFloat() * areaDeviation);
                            double heightDelta = to.getY() - yeeted.getLocation().getY();
                            double risingHeight = (heightDelta > 0) ? Math.max(60, heightDelta + 20) : Math.max(10, heightDelta + 60);
                            final double gravity = -0.08;
                            final double vy = Math.sqrt(Math.abs(2 * gravity * risingHeight));
                            double fallingTime = Math.sqrt(Math.abs(2 * (heightDelta - risingHeight) / gravity));
                            double risingTime = -(vy / gravity);
                            final Vector vh = to.toVector().setY(0).subtract(yeeted.getLocation().toVector().setY(0));
                            launch(yeeted, vh.clone().multiply(1 / (risingTime + fallingTime)).setY(vy), gravity);
                            pocket = Optional.empty();
                            golem.setTarget(null);
                            cooldown = 60;
                        }
                    }
                }
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    private static void launch(LivingEntity entity, Vector velocity, double gravity) {
        assert gravity < 0.0;
        entity.setCollidable(false);
        entity.setGravity(false);
        @NotNull Vector newVelocity = velocity.clone();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid()) {
                    cancel();
                    return;
                }
                if (entity.isOnGround() && newVelocity.getY() <= 0) {
                    cancel();
                    entity.setVelocity(new Vector(0, 0, 0));
                    entity.setGravity(true);
                    entity.setCollidable(true);
                    return;
                }
                entity.setVelocity(newVelocity);
                entity.setFallDistance(0);
                newVelocity.setY(newVelocity.getY() + gravity);
            }
        }.runTaskTimer(HordeTestPlugin.getInstance(), 0, 1);
    }

    private static Optional<LivingEntity> getNearestHumanTarget(Location loc) {
        Stream<Player> players = loc.getWorld().getPlayers()
                .stream()
                .filter(p -> !p.getGameMode().equals(GameMode.CREATIVE));
        return Stream.concat(players.map(p -> (LivingEntity) p), HordeTestPlugin.turrets.stream())
                .min(Comparator.comparing(p -> p.getLocation().distanceSquared(loc)));
    }

    private static Stream<Entity> getNearbyEntities(Entity entity, double radius) {
        final int floorRad = (int) radius;
        final int chunkRadius = floorRad < 16 ? 1 : (floorRad - (floorRad % 16)) / 16;
        final ArrayList<Entity[]> chunkEntities = new ArrayList<>();
        for (int i = -chunkRadius; i <= chunkRadius; ++i) {
            for (int j = -chunkRadius; j <= chunkRadius; ++j) {
                chunkEntities.add(entity.getLocation().clone().add(i * 16, 0, j * 16).getChunk().getEntities());
            }
        }
        return chunkEntities.stream()
                .flatMap(Arrays::stream)
                .filter(e -> e != entity && e.getLocation().distanceSquared(entity.getLocation()) <= radius * radius);
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
                    creature.setTarget(getNearestHumanTarget(creature.getLocation()).orElse(null));
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
                if (getNearbyEntities(zombie, 3).anyMatch(e -> e instanceof Zombie
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
                return e instanceof Creature && e.getLocation().distanceSquared(turret.getLocation()) <= 75 * 75 && turret.hasLineOfSight(e);
            }

            @Override
            public void run() {
                if (!turret.isValid()) {
                    cancel();
                    return;
                }
                if (!isTargettable(target)) {
                    target = getNearbyEntities(turret, 75)
                            .filter(this::isTargettable)
                            .map(e -> (LivingEntity) e)
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
