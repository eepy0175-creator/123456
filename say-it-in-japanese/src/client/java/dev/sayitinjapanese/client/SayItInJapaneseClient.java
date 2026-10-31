package dev.sayitinjapanese.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SayItInJapaneseClient implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("sayitinjapanese");

	@Override
	public void onInitializeClient() {
		Config.load();

		ClientTickEvents.END_CLIENT_TICK.register(Challenges::tick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sayitinjapanese", "challenge"), ChallengeHud::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Challenges.reset());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> Commands.register(dispatcher));
	}
}
