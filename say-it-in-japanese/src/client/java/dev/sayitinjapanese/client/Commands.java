package dev.sayitinjapanese.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;

/** {@code /sayjp ...} - change settings without leaving the game. Everything is saved to the config file. */
final class Commands {
	private Commands() {}

	static void register(CommandDispatcher<FabricClientCommandSource> d) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("sayjp");

		root.then(ClientCommands.literal("script")
				.then(scriptCmd("kanji", Config.Script.KANJI))
				.then(scriptCmd("hiragana", Config.Script.HIRAGANA))
				.then(scriptCmd("romaji", Config.Script.ROMAJI)));

		root.then(ClientCommands.literal("seconds")
				.then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1, 60)).executes(c -> {
					Config.get().seconds = IntegerArgumentType.getInteger(c, "seconds");
					Config.save();
					c.getSource().sendFeedback(Component.literal("Time limit: " + Config.get().seconds + "s"));
					return 1;
				})));

		root.then(ClientCommands.literal("strictness")
				.then(ClientCommands.argument("percent", DoubleArgumentType.doubleArg(0, 100)).executes(c -> {
					Config.get().strictness = DoubleArgumentType.getDouble(c, "percent") / 100.0;
					Config.save();
					c.getSource().sendFeedback(Component.literal(
							"Strictness: " + Math.round(Config.get().strictness * 100) + "% (lower = more forgiving)"));
					return 1;
				})));

		root.then(ClientCommands.literal("enabled")
				.then(ClientCommands.argument("value", BoolArgumentType.bool()).executes(c -> {
					Config.get().enabled = BoolArgumentType.getBool(c, "value");
					Config.save();
					c.getSource().sendFeedback(Component.literal("Enabled: " + Config.get().enabled));
					return 1;
				})));

		root.then(ClientCommands.literal("delete")
				.then(ClientCommands.argument("value", BoolArgumentType.bool()).executes(c -> {
					Config.get().deleteOnFail = BoolArgumentType.getBool(c, "value");
					Config.save();
					c.getSource().sendFeedback(Component.literal(
							"Delete items on failure: " + Config.get().deleteOnFail + " (false = practice mode)"));
					return 1;
				})));

		root.then(ClientCommands.literal("mic")
				.then(ClientCommands.literal("list").executes(c -> {
					List<String> mics = Speech.microphones();
					c.getSource().sendFeedback(Component.literal(mics.isEmpty()
							? "No microphones found."
							: "Microphones: " + String.join(" | ", mics)));
					return 1;
				}))
				.then(ClientCommands.literal("default").executes(c -> {
					Config.get().microphone = "";
					Config.save();
					c.getSource().sendFeedback(Component.literal("Using the system default microphone."));
					return 1;
				}))
				.then(ClientCommands.literal("use")
						.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> {
							Config.get().microphone = StringArgumentType.getString(c, "name");
							Config.save();
							c.getSource().sendFeedback(Component.literal("Microphone: " + Config.get().microphone));
							return 1;
						}))));

		root.then(ClientCommands.literal("download").executes(c -> {
			ModelManager.ensureAsync(true);
			return 1;
		}));

		root.then(ClientCommands.literal("test").executes(c -> {
			Challenges.test();
			return 1;
		}));

		root.then(ClientCommands.literal("status").executes(c -> {
			Config cfg = Config.get();
			c.getSource().sendFeedback(Component.literal(
					"enabled=" + cfg.enabled + ", script=" + cfg.script.name().toLowerCase()
							+ ", seconds=" + cfg.seconds + ", strictness=" + Math.round(cfg.strictness * 100) + "%"
							+ ", delete=" + cfg.deleteOnFail + ", speechModel=" + (Speech.ready() ? "ready" : "not loaded")));
			return 1;
		}));

		d.register(root);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> scriptCmd(String name, Config.Script script) {
		return ClientCommands.literal(name).executes(c -> {
			Config.get().script = script;
			Config.save();
			c.getSource().sendFeedback(Component.literal("Words will now be shown in " + name + "."));
			return 1;
		});
	}
}
