package io.github.skippi.hordetest;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class HordeTestPlugin extends JavaPlugin implements Listener {
    private static ProtocolManager PM;
    private static final BlockHealthManager BLOCK_HEALTH_MANAGER = new BlockHealthManager();
    private static HordeTestPlugin INSTANCE;

    public static ProtocolManager getProtocolManager() {
        return PM;
    }

    public static BlockHealthManager getBlockHealthManager() {
        return BLOCK_HEALTH_MANAGER;
    }

    public static HordeTestPlugin getInstance() { return INSTANCE; }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::trySpawnZombie, 0, 5);
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeArrowTurret()));
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeRepairTurret()));
        INSTANCE = this;
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

    private boolean isArrowTurret(Entity entity) {
        return entity instanceof ArmorStand && entity.getCustomName().startsWith("Arrow Turret");
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
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getItem().setAmount(event.getItem().getAmount() - 1);
        }
    }

    @EventHandler
    private void tryPickupArrowTurret(PlayerInteractAtEntityEvent event) {
        if (!isArrowTurret(event.getRightClicked())) return;
        event.getRightClicked().remove();
        event.getPlayer().getInventory().addItem(makeArrowTurret());
        event.setCancelled(true);
    }

    private void spawnArrowTurret(Location loc) {
        @NotNull ArmorStand turret = loc.getWorld().spawn(loc, ArmorStand.class);
        turret.setItem(EquipmentSlot.HEAD, new ItemStack(Material.BOW));
        turret.setItem(EquipmentSlot.CHEST, new ItemStack(Material.LEATHER_CHESTPLATE));
        turret.setCustomName("Arrow Turret");
        turret.setCustomNameVisible(true);
        turret.setHealth(5);
        new BukkitRunnable() {
            LivingEntity target = null;
            int cooldown = 0;

            private boolean isTargettable(Entity e) {
                return e instanceof Creature && e.isValid() && e.getLocation().distance(turret.getLocation()) < 75 && turret.hasLineOfSight(e);
            }

            @Override
            public void run() {
                if (!turret.isValid()) return;
                if (!isTargettable(target)) {
                    target = (LivingEntity) turret.getWorld().getEntities().stream()
                            .filter(this::isTargettable)
                            .min(Comparator.comparing(e -> turret.getLocation().distanceSquared(e.getLocation())))
                            .orElse(null);
                }
                if (target != null && cooldown-- <= 0) {
                    @NotNull Vector dir = target.getEyeLocation().clone().subtract(turret.getEyeLocation()).toVector().normalize();
                    @NotNull Arrow arrow = turret.launchProjectile(Arrow.class);
                    arrow.setVelocity(dir.clone().multiply(3));
                    cooldown = 20;
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    private static Map<UUID, Inventory> repairTurretInvs = new HashMap<>();

    private void spawnRepairTurret(Location loc) {
        @NotNull ArmorStand turret = loc.getWorld().spawn(loc, ArmorStand.class);
        turret.setItem(EquipmentSlot.HEAD, new ItemStack(Material.STONE));
        turret.setItem(EquipmentSlot.CHEST, new ItemStack(Material.LEATHER_CHESTPLATE));
        turret.setCustomName("Repair Turret");
        turret.setCustomNameVisible(true);
        turret.setHealth(5);
        repairTurretInvs.put(turret.getUniqueId(), Bukkit.createInventory(null, InventoryType.DISPENSER));
    }

    private boolean isRepairTurret(Entity entity) {
        return entity instanceof ArmorStand && entity.getCustomName().startsWith("Repair Turret");
    }

    private boolean isRepairTurret(ItemStack stack) {
        return stack.getType().equals(Material.BOOK) && stack.getItemMeta().getCustomModelData() == 2;
    }

    private ItemStack makeRepairTurret() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(2);
        meta.displayName(Component.text("Repair Turret"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    private void repairTurretRightClick(PlayerInteractAtEntityEvent event) {
        if (!isRepairTurret(event.getRightClicked())) return;
        if (event.getPlayer().isSneaking()) {
            event.getPlayer().openInventory(repairTurretInvs.get(event.getRightClicked().getUniqueId()));
        } else {
            event.getRightClicked().remove();
            event.getPlayer().getInventory().addItem(makeRepairTurret());
        }
        event.setCancelled(true);
    }

    @EventHandler
    private void repairTurretRepair(BlockPreDamageEvent event) {
        Optional<ArmorStand> maybeTurret = event.getBlock().getWorld().getEntities().stream()
                .filter(e -> e.isValid() && isRepairTurret(e))
                .filter(e -> e.getLocation().distance(event.getBlock().getLocation()) <= 10)
                .map(e -> (ArmorStand) e)
                .findFirst();
        if (maybeTurret.isPresent()) {
            ArmorStand turret = maybeTurret.get();
            Inventory inv = repairTurretInvs.get(turret.getUniqueId());
            List<ItemStack> supply = StreamSupport.stream(inv.spliterator(), false)
                    .filter(i -> i != null && i.getType().equals(event.getBlock().getType()))
                    .collect(Collectors.toList());
            for (ItemStack item : supply) {
                if (item.getAmount() > 0 && event.getDamage() >= getBlockHealthManager().getHealth(event.getBlock())) {
                    getBlockHealthManager().reset(event.getBlock());
                    event.setDamage(event.getDamage() - getBlockHealthManager().getHealth(event.getBlock()));
                    item.setAmount(item.getAmount() - 1);
                }
            }
        }
    }

    @EventHandler
    private void placeRepairTurret(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || !isRepairTurret(event.getItem())) return;
        assert event.getClickedBlock() != null;
        spawnRepairTurret(event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5));
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getItem().setAmount(event.getItem().getAmount() - 1);
        }
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
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (entity instanceof ArmorStand) {
            entity.setHealth(Math.max(0, entity.getHealth() - event.getFinalDamage()));
        } else {
            entity.damage(event.getFinalDamage());
        }
        entity.setNoDamageTicks(0);
        event.setCancelled(true);
    }

    @EventHandler
    private void repairTurretCleanup(EntityRemoveFromWorldEvent event) {
        repairTurretInvs.remove(event.getEntity().getUniqueId());
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
                if (monster.getTarget() == null || !monster.getTarget().isValid()) {
                    Stream<Player> players = monster.getWorld().getPlayers()
                            .stream()
                            .filter(p -> !p.getGameMode().equals(GameMode.CREATIVE));
                    Stream<LivingEntity> targetables = monster.getWorld().getEntities().stream()
                            .filter(e -> e instanceof ArmorStand)
                            .map(e -> (LivingEntity) e);
                    LivingEntity target = Stream.concat(players.map(p -> (LivingEntity) p), targetables)
                            .min(Comparator.comparing(p -> p.getLocation().distanceSquared(monster.getLocation())))
                            .orElse(null);
                    monster.setTarget(target);
                }
            }
        }.runTaskTimer(this, 0, 2);
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
    private void creatureTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Creature) {
            event.setCancelled(true);
        }
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
    private void ironGolemInit(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof IronGolem) {
            AI.addTossAI((IronGolem) event.getEntity());
        }
    }

    @EventHandler
    private void zombieInit(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Zombie) {
            Zombie zombie = (Zombie) event.getEntity();
            zombie.setAdult();
            AI.addClimbAI(zombie);
            AI.addDigAI(zombie);
            AI.addSpeedAI(zombie);
        }
    }

    @EventHandler
    private void zombieFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Zombie)) return;
        event.setCancelled(true);
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
    private void hordeRemove(EntityRemoveFromWorldEvent event) {
        hordeIds.remove(event.getEntity().getUniqueId());
        AI.cleanupExposure(event.getEntity().getUniqueId());
    }
}
