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
import org.apache.commons.lang.math.IntRange;
import org.apache.commons.lang.math.RandomUtils;
import org.bukkit.*;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.inventory.*;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.BoundingBox;
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
    private static PhysicsScheduler PHYSICS_SCHEDULER = new PhysicsScheduler();
    public static StressSystem SS = new StressSystem();
    public static Set<LivingEntity> turrets = new HashSet<>();
    public static Set<Block> torches = new HashSet<>();
    private static int BORDER_SIZE = 400;
    private static Map<Player, Location> PLAYER_DEATH_LOCATIONS = new HashMap<>();

    public static ProtocolManager getProtocolManager() {
        return PM;
    }

    public static BlockHealthManager getBlockHealthManager() {
        return BLOCK_HEALTH_MANAGER;
    }

    public static HordeTestPlugin getInstance() { return INSTANCE; }

    private static boolean hasSurvivors(World world) {
        return world.getPlayers().stream().anyMatch(p -> p.getGameMode().equals(GameMode.SURVIVAL));
    }

    private static Map<World, Double> WORLD_HEIGHT_AVERAGES = new HashMap<>();

    private static void spawnHerd(World world, EntityType type) {
        int x = RandomUtils.nextInt(16);
        int z = RandomUtils.nextInt(16);
        int innerChunkRadius = Math.max(0, getChunkRadius(world) - 1);
        @NotNull Chunk chunk = world.getChunkAt(RandomUtils.nextInt(2 * innerChunkRadius) - innerChunkRadius, RandomUtils.nextInt(2 * innerChunkRadius) - innerChunkRadius);
        Location spawnLoc = world.getHighestBlockAt(chunk.getBlock(x, 0, z).getLocation(), HeightMap.MOTION_BLOCKING_NO_LEAVES)
                .getRelative(BlockFace.UP)
                .getLocation();
        for (int j = 0; j < RandomUtils.nextInt(3) + 5; ++j) {
            world.spawnEntity(spawnLoc, type);
        }
        world.getPlayers().stream()
                .filter(p -> p.getGameMode().equals(GameMode.SURVIVAL))
                .forEach(p -> p.sendMessage(Component.text(String.format("%s herd spotted at (%d, %d, %d) [%dm]", type.getEntityClass().getSimpleName(), spawnLoc.getBlockX(), spawnLoc.getBlockY(), spawnLoc.getBlockZ(), Math.round(spawnLoc.distance(p.getLocation()))))));
    }

    private static void spawnMorningHerds(World world) {
        List<EntityType> animalTypes = Arrays.asList(EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN);
        long survivorCount = world.getPlayers().stream().filter(p -> p.getGameMode().equals(GameMode.SURVIVAL)).count();
        for (int i = 0; i < survivorCount; ++i) {
            spawnHerd(world, EntityType.CHICKEN);
        }
        for (int i = 0; i < survivorCount * 2; ++i) {
            EntityType type = animalTypes.get(RandomUtils.nextInt(animalTypes.size()));
            spawnHerd(world, type);
        }
    }

    private static void populateSugarCane(@NotNull Chunk chunk, @NotNull Random random) {
        @NotNull World world = chunk.getWorld();
        List<Material> growBlocks = Arrays.asList(Material.SAND, Material.GRASS_BLOCK, Material.RED_SAND);
        int count = 0;
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                if (count > 3) break;
                @NotNull Block block = world.getHighestBlockAt(chunk.getBlock(i, 255, j).getLocation(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (!growBlocks.contains(block.getType())) continue;
                boolean hasWater = false;
                for (int x = -1; x <= 1; ++x) {
                    for (int z = -1; z <= 1; ++z) {
                        if (0 <= i + x && i + x < 16 && 0 <= j + z && j + z < 16 && chunk.getBlock(i + x, block.getY(), j + z).getType().equals(Material.WATER)) {
                            hasWater = true;
                            break;
                        }
                    }
                }
                if (!hasWater) continue;
                if (!(random.nextFloat() <= 0.1)) continue;
                int stalkHeight = random.nextInt(2) + 2;
                for (int k = 0; k < stalkHeight; ++k) {
                    block = block.getRelative(BlockFace.UP);
                    block.setType(Material.SUGAR_CANE, false);
                }
                ++count;
            }
        }
    }

    private static void populateCobweb(@NotNull Chunk chunk, @NotNull Random random, IntRange sizeRange) {
        int maxCount = 3;
        for (int i = 0; i < 16; ++i) {
            for (int k = 0; k < 16; ++k) {
                for (int j = 0; j < 96; ++j) {
                    if (maxCount <= 0) break;
                    @NotNull Block block = chunk.getBlock(i, j, k);
                    if (!block.isEmpty()) continue;
                    Optional<Block> above = getRelativeWithinChunk(block, 0, 1, 0);
                    if (!(above.isPresent() && above.get().getType().equals(Material.STONE))) continue;
                    if (!(random.nextFloat() <= 0.005)) continue;
                    int veinSize = random.nextInt(sizeRange.getMaximumInteger() - sizeRange.getMinimumInteger()) + sizeRange.getMinimumInteger();
                    Queue<Block> queue = new ArrayDeque<>();
                    queue.add(block);
                    while (!queue.isEmpty() && veinSize > 0) {
                        Block curr = queue.remove();
                        if (!curr.isEmpty()) continue;
                        Optional<Block> currAbove = getRelativeWithinChunk(curr, 0, 1, 0);
                        if (!(currAbove.isPresent() && currAbove.get().getType().equals(Material.STONE))) continue;
                        --veinSize;
                        curr.setType(Material.COBWEB, false);
                        getRelativeWithinChunk(curr, 0, 1, 0).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 1, 0, 0).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 0, 0, 1).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, -1, 0, 0).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 0, 0, -1).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 0, -1, 0).ifPresent(queue::add);
                    }
                    --maxCount;
                }
            }
        }
    }

    private static void populateExtraOre(@NotNull Chunk chunk, @NotNull Random random, Material ore, double chance, IntRange sizeRange, IntRange heightRange) {
        for (int i = 0; i < 16; ++i) {
            for (int k = 0; k < 16; ++k) {
                for (int j = heightRange.getMinimumInteger(); j < heightRange.getMaximumInteger(); ++j) {
                    @NotNull Block block = chunk.getBlock(i, j, k);
                    if (!block.getType().equals(Material.STONE)) continue;
                    if (!(random.nextFloat() <= chance)) continue;
                    int veinSize = random.nextInt(sizeRange.getMaximumInteger() - sizeRange.getMinimumInteger()) + sizeRange.getMinimumInteger();
                    Queue<Block> queue = new ArrayDeque<>();
                    queue.add(block);
                    while (!queue.isEmpty() && veinSize > 0) {
                        Block curr = queue.remove();
                        if (!curr.getType().equals(Material.STONE)) continue;
                        --veinSize;
                        curr.setType(ore, false);
                        getRelativeWithinChunk(curr, 0, 1, 0).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 1, 0, 0).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 0, 0, 1).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, -1, 0, 0).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 0, 0, -1).ifPresent(queue::add);
                        getRelativeWithinChunk(curr, 0, -1, 0).ifPresent(queue::add);
                    }
                }
            }
        }
    }

    private static Optional<Block> getRelativeWithinChunk(Block block, int x, int y, int z) {
        int relX = (block.getX() & 0xF) + x;
        int relY = (block.getY() & 0xFF) + y;
        int relZ = (block.getZ() & 0xF) + z;
        if (!(0 <= relX && relX < 16 && 0 <= relY && relY < 256 && 0 <= relZ && relZ < 16)) return Optional.empty();
        return Optional.of(block.getChunk().getBlock(relX, relY, relZ));
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            @Nullable World world = Bukkit.getWorld("world");
            if (23460 <= world.getTime() && world.getTime() < 23470) {
                world.setTime(23470);
                ++STAGE;
                world.getPlayers().stream()
                        .filter(p -> p.getGameMode().equals(GameMode.SPECTATOR))
                        .forEach(p -> {
                            Location spawnLoc = world.getHighestBlockAt(PLAYER_DEATH_LOCATIONS.getOrDefault(p, world.getSpawnLocation()))
                                    .getLocation().clone()
                                    .add(0, 1, 0);
                            p.teleport(spawnLoc);
                            p.setGameMode(GameMode.SURVIVAL);
                            PLAYER_DEATH_LOCATIONS.remove(p);
                        });
                world.getLivingEntities().stream()
                        .filter(e -> !(e instanceof Player || e instanceof ArmorStand))
                        .forEach(Entity::remove);
                spawnMorningHerds(world);
            }
        }, 0, 1);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> Bukkit.getWorlds().forEach(w -> w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, hasSurvivors(w))), 0, 1);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, HordeTestPlugin::tickHordeSpawns, 0, 1);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, PHYSICS_SCHEDULER::tick, 0, 1);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (!world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)) continue;
                if ((isHordeTime(world.getTime()) || STAGE >= 2) && world.getTime() % 2 == 0) {
                    world.setTime(world.getTime() + 1);
                }
            }
        }, 0, 1);
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeArrowTurret()));
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeRepairTurret()));
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeSquarePaintBrush()));
        Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(makeCirclePaintBrush()));
        Bukkit.getOnlinePlayers().forEach(HordeTestPlugin::addNoCooldownAttacks);
        getCommand("stage").setExecutor(new StageCommand());
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
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 24);
            world.getWorldBorder().setCenter(world.getSpawnLocation().getX(), world.getSpawnLocation().getZ());
            world.getWorldBorder().setSize(BORDER_SIZE, 0);
            for (int x = -getChunkRadius(world); x <= getChunkRadius(world); ++x) {
                for (int z = -getChunkRadius(world); z <= getChunkRadius(world); ++z) {
                    @NotNull Chunk chunk = world.getChunkAt(x, z);
                    chunk.setForceLoaded(true);
                    for (int i = 0; i < 16; ++i) {
                        for (int j = 0; j < 16; ++j) {
                            double newAvg = WORLD_HEIGHT_AVERAGES.getOrDefault(world, 0.0);
                            newAvg += world.getHighestBlockYAt(chunk.getBlock(i, 255, j).getLocation(), HeightMap.MOTION_BLOCKING_NO_LEAVES) / (256.0 * (Math.pow(getChunkRadius(world) * 2 + 1, 2)));
                            WORLD_HEIGHT_AVERAGES.put(world, newAvg);
                        }
                    }
                }
            }
            spawnMorningHerds(world);
        }
        makeFlaxRecipes().forEach(getServer()::addRecipe);
        getServer().addRecipe(new StonecuttingRecipe(new NamespacedKey(this, "flint_cobblestone"), new ItemStack(Material.FLINT, 1), Material.COBBLESTONE));
    }

    @EventHandler
    private void worldInit(WorldInitEvent event) {
        event.getWorld().getPopulators().add(new BlockPopulator() {
            @Override
            public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk source) {
                populateSugarCane(source, random);
                populateCobweb(source, random, new IntRange(5, 10));
                populateExtraOre(source, random, Material.IRON_ORE, 5.0 / 15360, new IntRange(4, 10), new IntRange(0, 128));
                populateExtraOre(source, random, Material.COAL_ORE, 8.0 / 15360, new IntRange(4, 10), new IntRange(0, 96));
                populateExtraOre(source, random, Material.GOLD_ORE, 5.0 / 15360, new IntRange(4, 10), new IntRange(0, 80));
                populateExtraOre(source, random, Material.REDSTONE_ORE, 10.0 / 15360, new IntRange(6, 12), new IntRange(0, 128));
                populateExtraOre(source, random, Material.DIAMOND_ORE, 1.0 / 15360, new IntRange(1, 4), new IntRange(0, 16));
                populateExtraOre(source, random, Material.EMERALD_ORE, 1.0 / 15360, new IntRange(1, 4), new IntRange(0, 33));
                populateExtraOre(source, random, Material.LAPIS_ORE, 4.0 / 15360, new IntRange(4, 8), new IntRange(0, 64));
            }
        });
    }

    private static List<ShapedRecipe> makeFlaxRecipes() {
        List<ShapedRecipe> recipes = new ArrayList<>();
        List<Material> flowers = Arrays.asList(Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY);
        for (Material flower1 : flowers) {
            for (Material flower2 : flowers) {
                for (Material flower3 : flowers) {
                    NamespacedKey key = new NamespacedKey(HordeTestPlugin.getInstance(), String.format("flax_%s_%s_%s", flower1.toString().toLowerCase(Locale.ROOT), flower2.toString().toLowerCase(Locale.ROOT), flower3.toString().toLowerCase(Locale.ROOT)));
                    ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.STRING, 1))
                            .shape("ABC")
                            .setIngredient('A', flower1)
                            .setIngredient('B', flower2)
                            .setIngredient('C', flower3);
                    recipes.add(recipe);
                }
            }
        }
        return recipes;
    }

    private static int getChunkRadius(World world) {
        int borderRadius = (int) world.getWorldBorder().getSize() / 2;
        return (borderRadius - (borderRadius % 16)) / 16;
    }

    public static int STAGE = 1;
    private static final Set<Entity> HORDE_ENTITIES = new HashSet<>();

    private static void tryHordeSpawn(Player player, EntityType type, int perPlayerLimit, double chance) {
        assert 0.0 <= chance && chance <= 1.0;
        if (RandomUtils.nextFloat() >= chance) return;
        final long unitLimit = (long) (Bukkit.getOnlinePlayers().stream()
                .mapToDouble(p -> {
                    double multiplier = 1.0;
                    if (p.getGameMode().equals(GameMode.SURVIVAL)) {
                        multiplier += Math.min(2, Math.abs(WORLD_HEIGHT_AVERAGES.get(p.getWorld()) - p.getLocation().getY()) / 50.0);
                    }
                    return multiplier;
                })
                .sum() * perPlayerLimit * Bukkit.getOnlinePlayers().size());
        final long unitCount = HORDE_ENTITIES.stream().filter(e -> e.getType().equals(type)).count();
        if (unitCount < unitLimit) {
            genHostileSpawnLocation(player).ifPresent(loc -> HORDE_ENTITIES.add(player.getWorld().spawnEntity(loc, type)));
        }
    }

    @EventHandler
    private void deathSpectator(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getFinalDamage() < player.getHealth()) return;
        if (!isHordeTime(player.getWorld().getTime())) return;
        event.setCancelled(true);
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * (player.getBedSpawnLocation() != null ? 1 : 0.3));
        if (player.getBedSpawnLocation() != null) {
            player.teleport(player.getBedSpawnLocation());
            player.getPotentialBedLocation().getBlock().setType(Material.AIR);
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            return;
        }
        StreamSupport.stream(player.getInventory().spliterator(), false)
                .filter(Objects::nonNull)
                .forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        PLAYER_DEATH_LOCATIONS.put(player, player.getLocation());
    }

    private static boolean isHordeTime(long time) {
        return 13200 <= time && time < 23460;
    }

    private static void tickHordeSpawns() {
        List<Player> survivors = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getGameMode().equals(GameMode.SPECTATOR))
                .collect(Collectors.toList());
        for (int i = 0; i < survivors.size(); ++i) {
            Player player = survivors.get(RandomUtils.nextInt(survivors.size()));
            @NotNull World world = player.getWorld();
            if (!isHordeTime(world.getTime())) return;
            if (STAGE == 1) {
                tryHordeSpawn(player, EntityType.ZOMBIE, 16, 1.0 / 40);
            } else if (STAGE == 2) {
                tryHordeSpawn(player, EntityType.ZOMBIE, 17, 1.0 / 40);
                tryHordeSpawn(player, EntityType.SPIDER, 3, 1.0 / 60);
            } else if (STAGE == 3) {
                tryHordeSpawn(player, EntityType.ZOMBIE, 18, 1.0 / 40);
                tryHordeSpawn(player, EntityType.SPIDER, 3, 1.0 / 60);
                tryHordeSpawn(player, EntityType.SKELETON, 3, 1.0 / 60);
            } else if (STAGE == 4) {
                tryHordeSpawn(player, EntityType.ZOMBIE, 19, 1.0 / 40);
                tryHordeSpawn(player, EntityType.SKELETON, 3, 1.0 / 60);
                tryHordeSpawn(player, EntityType.SPIDER, 3, 1.0 / 60);
                tryHordeSpawn(player, EntityType.CREEPER, 2, 1.0 / 80);
            } else if (STAGE == 5) {
                tryHordeSpawn(player, EntityType.ZOMBIE, 20, 1.0 / 40);
                tryHordeSpawn(player, EntityType.SKELETON, 4, 1.0 / 60);
                tryHordeSpawn(player, EntityType.SPIDER, 4, 1.0 / 60);
                tryHordeSpawn(player, EntityType.CREEPER, 2, 1.0 / 80);
                tryHordeSpawn(player, EntityType.IRON_GOLEM, 1, 1.0 / 160);
            } else {
                tryHordeSpawn(player, EntityType.ZOMBIE, 20, 1.0 / 40);
                tryHordeSpawn(player, EntityType.SKELETON, 4, 1.0 / 60);
                tryHordeSpawn(player, EntityType.SPIDER, 4, 1.0 / 60);
                tryHordeSpawn(player, EntityType.CREEPER, 2, 1.0 / 80);
                tryHordeSpawn(player, EntityType.IRON_GOLEM, 1, 1.0 / 160);
                tryHordeSpawn(player, EntityType.PHANTOM, 2, 1.0 / 160);
            }
        }
    }

    private static void addNoCooldownAttacks(Player player) {
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(16);
    }

    @EventHandler
    private void noSleep(PlayerBedEnterEvent event) {
        event.setUseBed(Event.Result.DENY);
    }

    private static @NotNull Optional<Location> genHostileSpawnLocation(Player player) {
        @NotNull World world = player.getWorld();
        @NotNull Vector dir = new Vector(-1 + RandomUtils.nextFloat() * 2, 0, -1 + RandomUtils.nextFloat() * 2).normalize();
        BlockIterator iter = new BlockIterator(world, player.getLocation().toVector(), dir, 0, 500);
        int count = 0;
        while (iter.hasNext()) {
            @NotNull Block surfaceBlock = world.getHighestBlockAt(iter.next().getLocation()).getRelative(BlockFace.UP);
            if (count++ < 10) continue;
            if (surfaceBlock.getLightFromBlocks() == 0) {
                return Optional.of(surfaceBlock.getLocation());
            }
        }
        return Optional.empty();
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

    private boolean isArrowTurret(ItemStack item) {
        return item.getType() == Material.BOOK && item.getItemMeta().getCustomModelData() == 1;
    }

    @EventHandler
    private void trySpawnArrowTurret(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || !isArrowTurret(event.getItem())) return;
        assert event.getClickedBlock() != null;
        @NotNull Location loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        loc.getWorld().spawn(loc, ArmorStand.class, turret -> {
            turret.setItem(EquipmentSlot.HEAD, new ItemStack(Material.BOW));
            turret.setItem(EquipmentSlot.CHEST, new ItemStack(Material.LEATHER_CHESTPLATE));
            turret.setCustomName("Arrow Turret");
            turret.setCustomNameVisible(true);
            turret.setHealth(5);
        });
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getItem().setAmount(event.getItem().getAmount() - 1);
        }
    }

    @EventHandler
    private void placeOnTopEntity(BlockCanBuildEvent event) {
        if (event.getPlayer().getBoundingBox().overlaps(BoundingBox.of(event.getBlock()))) return;
        event.setBuildable(true);
    }

    @EventHandler
    private void tryPickupArrowTurret(PlayerInteractAtEntityEvent event) {
        if (!AI.isArrowTurret(event.getRightClicked())) return;
        event.getRightClicked().remove();
        event.getPlayer().getInventory().addItem(makeArrowTurret());
        event.setCancelled(true);
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
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (entity instanceof ArmorStand) {
            entity.setHealth(Math.max(0, entity.getHealth() - event.getFinalDamage()));
        }
        if (event.getDamager() instanceof Projectile) {
            event.getDamager().remove();
        }
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

    private static boolean isComposedOf(Material self, Material composeType) {
        if (self.toString().contains("FENCE")) {
            return composeType.toString().endsWith("PLANKS");
        } else if (self.toString().contains("COBBLESTONE")) {
            return composeType.equals(Material.COBBLESTONE);
        } else if (self.toString().contains("STONE_BRICK")) {
            return composeType.equals(Material.STONE);
        } else if (self.equals(Material.IRON_DOOR) || self.equals(Material.IRON_TRAPDOOR)) {
            return composeType.equals(Material.IRON_BARS);
        } else if (self.toString().contains("DOOR")) {
            return composeType.toString().endsWith("PLANKS");
        }
        return false;
    }

    @EventHandler
    private void doRepair(PlayerInteractEvent event) {
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        if (!(event.getItem() != null && event.getClickedBlock().isValidTool(event.getItem()))) return;
        if (getBlockHealthManager().getHealth(event.getClickedBlock()) >= getBlockHealthManager().getMaxHealth(event.getClickedBlock())) return;
        Stream<ItemStack> composed = StreamSupport.stream(event.getPlayer().getInventory().spliterator(), false)
                .filter(i -> i != null && isComposedOf(event.getClickedBlock().getType(), i.getType()));
        Stream<ItemStack> exact = StreamSupport.stream(event.getPlayer().getInventory().spliterator(), false)
                .filter(i -> i != null && i.getType().equals(event.getClickedBlock().getType()));
        Optional<ItemStack> maybeSupply = Stream.concat(composed, exact).findFirst();
        if (!maybeSupply.isPresent()) return;
        getBlockHealthManager().reset(event.getClickedBlock());
        maybeSupply.get().setAmount(maybeSupply.get().getAmount() - 1);
        event.getPlayer().swingMainHand();
        event.getClickedBlock().getWorld().playSound(event.getClickedBlock().getLocation(), event.getClickedBlock().getSoundGroup().getPlaceSound(), 0.5f, 0.5f);
    }

    @EventHandler
    private void renderBlockBreaking(BlockDamageEvent event) {
        getBlockHealthManager().render(event.getBlock());
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

    @EventHandler
    private void entityCleanup(EntityRemoveFromWorldEvent event) {
        HORDE_ENTITIES.remove(event.getEntity());
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
                || (event.getDamager() instanceof Creeper)
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
        int radius = 3 * ((Creeper) event.getEntity()).getExplosionRadius();
        int blockDamage = radius * 2;
        @NotNull Location loc;
        for (int i = -radius; i <= radius; ++i) {
            for (int j = -radius; j <= radius; ++j) {
                for (int k = -radius; k <= radius; ++k) {
                    loc = event.getEntity().getLocation().clone().add(i, j, k);
                    if (loc.distance(event.getEntity().getLocation()) <= radius) {
                        @NotNull Block block = loc.getBlock();
                        if (block.getBlockData() instanceof Levelled) {
                            if (block.getType().equals(Material.WATER) || ((Levelled) block.getBlockData()).getLevel() != 0) {
                                block.setType(Material.AIR);
                                getBlockHealthManager().reset(block);
                            }
                        } else if (radius / 3 <= i && i <= radius / 3
                                && radius / 3 <= j && j <= radius / 3
                                && radius / 3 <= k && k <= radius / 3
                        ) {
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
                    .filter(s -> s != null && s.getType().equals(type))
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
                    .filter(s -> s != null && s.getType().equals(type))
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
