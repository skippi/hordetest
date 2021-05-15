package io.github.skippi.hordetest;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BlockPreDamageEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private Block block;
    private double damage;

    public BlockPreDamageEvent(Block block, double damage) {
        this.block = block;
        this.damage = damage;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public Block getBlock() {
        return block;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }
}
