package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.Objects;

/** One player's main inventory; the default source of a build run by a non-creative player. */
public final class PlayerInventorySource implements ItemSource {

    private static final NamespacedId id = NamespacedId.of("structurecumulus", "player_inventory");

    private final ServerPlayer player;
    private final ItemHandlerSource inventory;

    public PlayerInventorySource(ServerPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
        this.inventory = new ItemHandlerSource(new PlayerMainInvWrapper(player.getInventory()));
    }

    public ServerPlayer player() {
        return player;
    }

    @Override
    public NamespacedId id() {
        return id;
    }

    @Override
    public int available(ItemStack template) {
        return inventory.available(template);
    }

    @Override
    public ItemStack extract(ItemStack template, int count, ExtractionMode mode) {
        return inventory.extract(template, count, mode);
    }

    @Override
    public ItemStack insert(ItemStack stack, ExtractionMode mode) {
        return inventory.insert(stack, mode);
    }
}
