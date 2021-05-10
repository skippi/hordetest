package io.github.skippi.hordetest;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class HordeTestPlugin extends JavaPlugin implements Listener {
    private static ProtocolManager PM;
    private static final BlockHealthManager BLOCK_HEALTH_MANAGER = new BlockHealthManager();

    public static ProtocolManager getProtocolManager() {
        return PM;
    }

    public static BlockHealthManager getBlockHealthManager() {
        return BLOCK_HEALTH_MANAGER;
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        PM = ProtocolLibrary.getProtocolManager();
    }

    @EventHandler
    private void skeletonNoFriendlyFire(ProjectileCollideEvent event) {
        if (!(event.getEntity().getShooter() instanceof Skeleton)) return;
        if (event.getCollidedWith() instanceof Player) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void skeletonBlockBreak(ProjectileHitEvent event) {
        final Block block = event.getHitBlock();
        if (block == null) return;
        getBlockHealthManager().damage(block, 0.25);
        event.getEntity().remove();
    }

    @EventHandler
    private void skeletonAlwaysFire(EntitySpawnEvent event) {
        @NotNull Entity entity = event.getEntity();
        if (!(entity instanceof Skeleton)) return;
        Skeleton skeleton = (Skeleton) entity;
        BukkitRunnable task = new BukkitRunnable() {
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
        };
        task.runTaskTimer(this, 0, 1);
    }

    @EventHandler
    private void monsterAcquire(CreatureSpawnEvent event) {
        @NotNull Entity entity = event.getEntity();
        if (!(entity instanceof Monster)) return;
        Monster monster = (Monster) entity;
        new BukkitRunnable() {
            @Override
            public void run() {
                getNearestPlayer(monster.getWorld(), monster.getLocation()).ifPresent(monster::setTarget);
            }
        }.runTaskTimer(this, 0, 2);
    }

    private static Optional<Player> getNearestPlayer(World world, Location origin) {
        return world.getPlayers().stream().min(Comparator.comparing(p -> p.getLocation().distance(origin)));
    }

    @EventHandler
    private void zombieDig(CreatureSpawnEvent event) {
        @NotNull LivingEntity entity = event.getEntity();
        if (!(entity instanceof Zombie)) return;
        Zombie zombie = (Zombie) entity;
        new BukkitRunnable() {
            int cooldown = 0;

            @Override
            public void run() {
                if (!zombie.isValid()) {
                    cancel();
                    return;
                }
                if (cooldown-- > 0) {
                    return;
                }
                @Nullable LivingEntity target = zombie.getTarget();
                if (target == null) return;
                if (zombie.hasLineOfSight(target)) return;
                @NotNull Vector dir = target.getLocation().toVector().subtract(zombie.getLocation().toVector()).normalize();
                int x = zombie.getLocation().getBlockX();
                int z = zombie.getLocation().getBlockZ();
                if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                    x += (dir.getX() < 0) ? -1 : 1;
                } else {
                    z += (dir.getZ() < 0) ? -1 : 1;
                }
                Block footBlock = zombie.getWorld().getBlockAt(x, zombie.getLocation().getBlockY(), z);
                @NotNull Block faceBlock = footBlock.getRelative(BlockFace.UP);
                List<Block> blocks = new ArrayList<>();
                blocks.add(faceBlock);
                if (dir.getY() > 0) {
                    blocks.add(zombie.getWorld().getBlockAt(zombie.getEyeLocation()).getRelative(BlockFace.UP));
                    blocks.add(faceBlock.getRelative(BlockFace.UP));
                } else if (dir.getY() < 0) {
                    blocks.add(footBlock);
                    blocks.add(footBlock.getRelative(BlockFace.DOWN));
                } else {
                    blocks.add(footBlock);
                }
                Optional<Block> maybeBlock = blocks.stream().filter(b -> !b.getType().isAir()).findFirst();
                if (maybeBlock.isPresent()) {
                    zombie.swingMainHand();
                    zombie.getWorld().playSound(maybeBlock.get().getLocation(), maybeBlock.get().getSoundGroup().getHitSound(), 0.5f, 0);
                    getBlockHealthManager().damage(maybeBlock.get(), 1);
                    cooldown = 40;
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    @EventHandler
    private void creeperPatience(CreatureSpawnEvent event) {
        @NotNull LivingEntity entity = event.getEntity();
        if (!(entity instanceof Creeper)) return;
        Creeper creeper = (Creeper) entity;
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
                System.out.println(dist);
                if (dist < 3) {
                    creeper.ignite();
                }
                lastPos = creeper.getLocation();
                timer = 0;
            }
        }.runTaskTimer(this, 0, 1);
    }

    @EventHandler
    private void creeperNoFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Creeper)) return;
        if (!(event.getEntity() instanceof Monster)) return;
        event.setCancelled(true);
    }
}
