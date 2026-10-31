package dev.sayitinjapanese;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Common entrypoint. Registers the "delete item" packet and the server-side handler that
 * actually removes the items (so it also works on dedicated servers that have the mod).
 */
public class SayItInJapanese implements ModInitializer {
	public static final String MOD_ID = "sayitinjapanese";

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(DeleteItemPayload.TYPE, DeleteItemPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DeleteItemPayload.TYPE, (payload, context) ->
				context.player().level().getServer().execute(() -> deleteItems(context.player(), payload)));
	}

	private static void deleteItems(ServerPlayer player, DeleteItemPayload payload) {
		Identifier id;
		try {
			id = Identifier.parse(payload.itemId());
		} catch (RuntimeException e) {
			return; // malformed id from a misbehaving client
		}
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item == null) return;

		int remaining = Math.min(Math.max(payload.count(), 0), 4096);
		Inventory inv = player.getInventory();

		// Remove from the back of the inventory first (hotbar is at the front).
		for (int i = inv.getContainerSize() - 1; i >= 0 && remaining > 0; i--) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty() || !stack.is(item)) continue;
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
			if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
		}
		player.containerMenu.broadcastChanges();
	}
}
