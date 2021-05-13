package io.github.skippi.hordetest;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

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
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::trySpawnZombie, 0, 5);
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeArrowTurret()));
        PM = ProtocolLibrary.getProtocolManager();
    }

    private ItemStack makeArrowTurret() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(1);
        meta.displayName(Component.text("Arrow Turret"));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isArrowTurret(ItemStack item) {
        return item.getType() == Material.BOOK && item.getItemMeta().getCustomModelData() == 1;
    }

    @EventHandler
    private void trySpawnArrowTurret(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || !isArrowTurret(event.getItem())) return;
        assert event.getClickedBlock() != null;
        spawnArrowTurret(event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5));
    }

    private void spawnArrowTurret(Location loc) {
        @NotNull ArmorStand turret = loc.getWorld().spawn(loc, ArmorStand.class);
        turret.setItem(EquipmentSlot.HEAD, new ItemStack(Material.BOW));
        turret.setItem(EquipmentSlot.CHEST, new ItemStack(Material.LEATHER_CHESTPLATE));
        turret.setCustomName("Arrow Turret");
        turret.setCustomNameVisible(true);
        turret.setHealth(5);
        new BukkitRunnable() {
            Monster target = null;
            int cooldown = 0;

            private boolean isTargettable(Entity e) {
                return e.isValid() && e instanceof Monster && e.getLocation().distance(turret.getLocation()) < 75 && turret.hasLineOfSight(e);
            }

            @Override
            public void run() {
                if (!turret.isValid()) return;
                if (target != null && isTargettable(target) && cooldown-- <= 0) {
                    @NotNull Vector dir = target.getEyeLocation().clone().subtract(turret.getEyeLocation()).toVector().normalize();
                    @NotNull Arrow arrow = turret.launchProjectile(Arrow.class);
                    arrow.setVelocity(dir.clone().multiply(3));
                    cooldown = 20;
                } else {
                    target = (Monster) turret.getWorld().getEntities().stream()
                            .filter(this::isTargettable)
                            .min(Comparator.comparing(e -> turret.getLocation().distance(e.getLocation())))
                            .orElse(null);
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    @EventHandler
    private void skeletonNoFriendlyFire(ProjectileCollideEvent event) {
        if (!(event.getEntity().getShooter() instanceof Skeleton)) return;
        if (event.getCollidedWith() instanceof Player) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void noDamageCooldown(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (event.getEntity() instanceof ArmorStand) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        entity.damage(event.getFinalDamage());
        entity.setNoDamageTicks(0);
        event.setCancelled(true);
    }

    @EventHandler
    private void turretNoFriendlyFire(ProjectileCollideEvent event) {
        if (!(event.getEntity().getShooter() instanceof ArmorStand)) return;
        if (!(event.getCollidedWith() instanceof ArmorStand || event.getCollidedWith() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void turretProjectileCleanup(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof ArmorStand)) return;
        if (event.getHitBlock() == null) return;
        event.getEntity().remove();
    }

    @EventHandler
    private void skeletonBlockBreak(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Skeleton)) return;
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
                Stream<Player> players = monster.getWorld().getPlayers()
                        .stream()
                        .filter(p -> !p.getGameMode().equals(GameMode.CREATIVE));
                Stream<LivingEntity> targetables = monster.getWorld().getEntities().stream()
                        .filter(e -> e.isValid() && e instanceof ArmorStand)
                        .map(e -> (LivingEntity) e);
                Stream.concat(players.map(p -> (LivingEntity) p), targetables)
                    .min(Comparator.comparing(p -> p.getLocation().distance(monster.getLocation())))
                    .ifPresent(monster::setTarget);
            }
        }.runTaskTimer(this, 0, 2);
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

    @EventHandler
    private void spiderBreakBlock(CreatureSpawnEvent event) {
        @NotNull LivingEntity entity = event.getEntity();
        if (!(entity instanceof Spider)) return;
        Spider spider = (Spider) entity;
        new BukkitRunnable() {
            int cooldown = 0;

            @Override
            public void run() {
                if (!spider.isValid()) return;
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
        }.runTaskTimer(this, 0 ,1);
    }

    @EventHandler
    private void zombieClimb(CreatureSpawnEvent event) {
        @NotNull LivingEntity entity = event.getEntity();
        if (!(entity instanceof Zombie)) return;
        Zombie zombie = (Zombie) entity;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!zombie.isValid()) {
                    cancel();
                    return;
                }
                @Nullable LivingEntity target = zombie.getTarget();
                if (target == null) return;
                if (target.getLocation().getY() < zombie.getLocation().getY()) return;
                if (zombie.getWorld().getEntities()
                        .stream()
                        .anyMatch(e -> e.isValid() && e != zombie && e instanceof Zombie && e.getLocation().getY() <= zombie.getLocation().getY() && zombie.getBoundingBox().clone().expand(0.125, 0, 0.125).overlaps(e.getBoundingBox().clone().expand(0.125, 0.0, 0.125)))) {
                    AI.climb(zombie, target.getLocation().toVector());
                }
            }
        }.runTaskTimer(this, 0, 4);
    }

    @EventHandler
    private void zombieFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Zombie)) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void zombieSpeed(CreatureSpawnEvent event) {
        @NotNull LivingEntity entity = event.getEntity();
        if (!(entity instanceof Zombie)) return;
        entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.55);
    }

    @EventHandler
    private void zombieAdults(CreatureSpawnEvent event) {
        @NotNull LivingEntity entity = event.getEntity();
        if (!(entity instanceof Zombie)) return;
        Zombie zombie = (Zombie) entity;
        zombie.setAdult();
    }

    final Set<UUID> hordeIds = new HashSet<>();

    private void trySpawnZombie() {
        if (hordeIds.size() >= Bukkit.getOnlinePlayers().size() * 64) return;
        for (World world : Bukkit.getWorlds()) {
            if (!(13700 <= world.getTime() && world.getTime() < 24000)) return;
            Player player = world.getPlayers().get(RandomUtils.nextInt(world.getPlayerCount()));
            @NotNull Vector dir = new Vector(-1 + RandomUtils.nextFloat() * 2, 0, -1 + RandomUtils.nextFloat() * 2).normalize();
            BlockIterator iter = new BlockIterator(world, player.getLocation().toVector(), dir, 0, 500);
            int count = 0;
            while (iter.hasNext()) {
                @NotNull Block surfaceBlock = world.getHighestBlockAt(iter.next().getLocation()).getRelative(BlockFace.UP);
                if (count++ < 10) continue;
                if (surfaceBlock.getLightFromBlocks() == 0) {
                    @NotNull Zombie zombie = world.spawn(surfaceBlock.getLocation(), Zombie.class);
                    hordeIds.add(zombie.getUniqueId());
                    return;
                }
            }
        }
    }

    @EventHandler
    private void hordeRemove(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        hordeIds.remove(event.getEntity().getUniqueId());
    }
}
