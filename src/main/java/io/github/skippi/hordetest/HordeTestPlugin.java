package io.github.skippi.hordetest;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class HordeTestPlugin extends JavaPlugin implements Listener {
    private Map<Block, Float> blockHealths = new HashMap<>();
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
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
        damageBlock(block, 1);
        event.getEntity().remove();
    }

    private void damageBlock(Block block, float damage) {
        float health = blockHealths.computeIfAbsent(block, b -> b.getType().getHardness());
        float newHealth = health - damage;
        if (newHealth <= 0) {
            blockHealths.remove(block);
            block.breakNaturally();
        } else {
            blockHealths.put(block, Math.max(0, newHealth));
        }
    }
}
