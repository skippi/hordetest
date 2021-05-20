package io.github.skippi.hordetest;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import io.github.skippi.hordetest.gravity.PhysicsScheduler;
import io.github.skippi.hordetest.gravity.StressSystem;
import io.github.skippi.hordetest.gravity.UpdateNeighborStressAction;
import io.github.skippi.hordetest.gravity.UpdateStressAction;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class HordeTestPlugin extends JavaPlugin implements Listener {
    private static ProtocolManager PM;
    private static final BlockHealthManager BLOCK_HEALTH_MANAGER = new BlockHealthManager();
    private static HordeTestPlugin INSTANCE;
    private static PhysicsScheduler PHYSICS_SCHEDULER = new PhysicsScheduler();
    public static StressSystem SS = new StressSystem();
    public static Set<LivingEntity> turrets = new HashSet<>();
    public static Set<Block> torches = new HashSet<>();

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
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, PHYSICS_SCHEDULER::tick, 0, 1);
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeArrowTurret()));
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeRepairTurret()));
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeSquarePaintBrush()));
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeCirclePaintBrush()));
        Bukkit.getOnlinePlayers().forEach(HordeTestPlugin::addNoCooldownAttacks);
        INSTANCE = this;
        PM = ProtocolLibrary.getProtocolManager();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                AI.init(entity);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof ArmorStand) {
                    turrets.add(entity);
                }
            }
        }
    }

    private static void addNoCooldownAttacks(Player player) {
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(16);
    }

    @EventHandler
    private void playerNoCooldownAttacks(PlayerJoinEvent event) {
        addNoCooldownAttacks(event.getPlayer());
    }

    @EventHandler
    private void torchPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        if (type.equals(Material.TORCH) || type.equals(Material.WALL_TORCH)) {
            torches.add(event.getBlock());
        }
    }

    @EventHandler
    private void torchRemove(BlockDestroyEvent event) {
        torches.remove(event.getBlock());
    }

    @EventHandler
    private void quickCutTree(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) return;
        @NotNull ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!((Blocks.isLog(event.getBlock()) || Blocks.isLeaves(event.getBlock()))
                && tool.getType().toString().contains("_AXE"))) {
            return;
        }
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(event.getBlock());
        int i = 0;
        while (i < 256 && !queue.isEmpty()) {
            Block it = queue.remove();
            if (visited.contains(it)) continue;
            if (!(Blocks.isLog(it) || Blocks.isLeaves(it))) continue;
            visited.add(it);
            ItemMeta meta = tool.getItemMeta();
            if (!it.equals(event.getBlock()) && Blocks.isLog(it)) {
                ((Damageable) meta).setDamage(((Damageable) meta).getDamage() + 1);
            }
            if (((Damageable) meta).getDamage() >= tool.getType().getMaxDurability()) {
                tool.setAmount(0);
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
                break;
            }
            tool.setItemMeta(meta);
            it.breakNaturally(event.getPlayer().getInventory().getItemInMainHand());
            Stream.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH, BlockFace.EAST)
                    .map(it::getRelative)
                    .forEach(queue::add);
            ++i;
        }
    }

    @EventHandler
    private void turretRegister(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            turrets.add((LivingEntity) event.getEntity());
        }
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
        AI.addArrowTurretAI(turret);
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
        Optional<ArmorStand> maybeTurret = event.getBlock().getWorld().getLivingEntities().stream()
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
            if (event.getDamager() instanceof Player) {
                entity.setVelocity(entity.getLocation().clone().subtract(event.getEntity().getLocation()).toVector().normalize().multiply(0.5));
            }
        }
        if (event.getDamager() instanceof Projectile) {
            event.getDamager().remove();
        }
        entity.setNoDamageTicks(0);
        event.setCancelled(true);
    }

    @EventHandler
    private void turretTakeDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            ArmorStand turret = (ArmorStand) event.getEntity();
            turret.setHealth(Math.max(0, turret.getHealth() - event.getFinalDamage()));
            turret.setNoDamageTicks(turret.getMaximumNoDamageTicks());
            event.setCancelled(true);
        }
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
    private void initEntity(CreatureSpawnEvent event) {
        AI.init(event.getEntity());
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
    private void entityCleanup(EntityRemoveFromWorldEvent event) {
        hordeIds.remove(event.getEntity().getUniqueId());
        turrets.remove(event.getEntity());
        AI.cleanupExposure(event.getEntity().getUniqueId());
    }

    @EventHandler
    private void gravityPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        PHYSICS_SCHEDULER.schedule(new UpdateNeighborStressAction(block));
        PHYSICS_SCHEDULER.schedule(new UpdateStressAction(block));
    }

    @EventHandler
    private void creeperDeathSuicide(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Creeper)) return;
        Creeper creeper = (Creeper) event.getEntity();
        if (event.getDamager() instanceof Player
                || (event.getDamager() instanceof Projectile
                    && (((Projectile) event.getDamager()).getShooter() instanceof ArmorStand
                        || ((Projectile) event.getDamager()).getShooter() instanceof Player))
                || event.getFinalDamage() < creeper.getHealth()) return;
        creeper.explode();
    }

    @EventHandler
    private void creeperDeathBlockSuicide(EntityDamageByBlockEvent event) {
        if (!(event.getEntity() instanceof Creeper)) return;
        Creeper creeper = (Creeper) event.getEntity();
        if (event.getFinalDamage() < creeper.getHealth()) return;
        creeper.explode();
    }

    @EventHandler
    private void creeperBlockDamage(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper)) return;
        int radius = ((Creeper) event.getEntity()).getExplosionRadius();
        int blockDamage = radius * 2;
        @NotNull Location loc;
        for (int i = -radius; i <= radius; ++i) {
            for (int j = -radius; j <= radius; ++j) {
                for (int k = -radius; k <= radius; ++k) {
                    loc = event.getEntity().getLocation().clone().add(i, j, k);
                    if (loc.distance(event.getEntity().getLocation()) <= radius) {
                        @NotNull Block block = loc.getBlock();
                        if (block.getBlockData() instanceof Levelled) {
                            if (((Levelled) block.getBlockData()).getLevel() != 0) {
                                block.setType(Material.AIR);
                                getBlockHealthManager().reset(block);
                            }
                        } else {
                            getBlockHealthManager().damage(block, blockDamage);
                        }
                    }
                }
            }
        }
    }

    private static ItemStack makeSquarePaintBrush() {
        ItemStack brush = new ItemStack(Material.FEATHER);
        ItemMeta meta = brush.getItemMeta();
        meta.setCustomModelData(1);
        meta.displayName(Component.text("Paint Brush (Square)"));
        brush.setItemMeta(meta);
        return brush;
    }

    private static boolean isSquarePaintBrush(@NotNull ItemStack stack) {
        return stack.getType().equals(Material.FEATHER) && stack.getItemMeta().getCustomModelData() == 1;
    }

    private static ItemStack makeCirclePaintBrush() {
        ItemStack brush = new ItemStack(Material.FEATHER);
        ItemMeta meta = brush.getItemMeta();
        meta.setCustomModelData(2);
        meta.displayName(Component.text("Paint Brush (Circle)"));
        brush.setItemMeta(meta);
        return brush;
    }

    private static boolean isCirclePaintBrush(@NotNull ItemStack stack) {
        return stack.getType().equals(Material.FEATHER) && stack.getItemMeta().getCustomModelData() == 2;
    }

    @EventHandler
    private void paintBrushPlace(BlockPlaceEvent event) {
        @NotNull Player player = event.getPlayer();
        @NotNull ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isSquarePaintBrush(offHand)) {
            int radius = offHand.getAmount();
            @NotNull Material type = event.getBlockPlaced().getType();
            StreamSupport.stream(player.getInventory().spliterator(), false)
                    .filter(s -> s.getType().equals(type))
                    .findFirst()
                    .ifPresent(s -> s.setAmount(s.getAmount() - 1));
            for (int i = -radius; i <= radius; ++i) {
                for (int k = -radius; k <= radius; ++k) {
                    if (i == 0 && k == 0) continue;
                    Block it = event.getBlockPlaced().getRelative(i, 0, k);
                    Optional<ItemStack> maybeSupply = StreamSupport.stream(player.getInventory().spliterator(), false)
                            .filter(s -> s != null && s.getType().equals(type))
                            .findFirst();
                    if (!it.isSolid() && (player.getGameMode().equals(GameMode.CREATIVE) || maybeSupply.isPresent())) {
                        it.setType(type);
                        maybeSupply.ifPresent(s -> s.setAmount(s.getAmount() - 1));
                    }
                }
            }
        } else if (isCirclePaintBrush(offHand)) {
            int radius = offHand.getAmount();
            @NotNull Material type = event.getBlockPlaced().getType();
            StreamSupport.stream(player.getInventory().spliterator(), false)
                    .filter(s -> s.getType().equals(type))
                    .findFirst()
                    .ifPresent(s -> s.setAmount(s.getAmount() - 1));
            for (int i = -radius; i <= radius; ++i) {
                for (int k = -radius; k <= radius; ++k) {
                    if (i == 0 && k == 0) continue;
                    Block it = event.getBlockPlaced().getRelative(i, 0, k);
                    if (i * i + k * k > Math.pow(radius, 2)) continue;
                    Optional<ItemStack> maybeSupply = StreamSupport.stream(player.getInventory().spliterator(), false)
                            .filter(s -> s != null && s.getType().equals(type))
                            .findFirst();
                    if (!it.isSolid() && (player.getGameMode().equals(GameMode.CREATIVE) || maybeSupply.isPresent())) {
                        it.setType(type);
                        maybeSupply.ifPresent(s -> s.setAmount(s.getAmount() - 1));
                    }
                }
            }
        }
    }
}
