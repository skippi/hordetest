package io.github.skippi.hordetest;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class HordeTestPlugin extends JavaPlugin implements Listener {
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
}
