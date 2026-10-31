package dev.sayitinjapanese;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "I failed the challenge, delete {@code count} of {@code itemId} from my inventory." */
public record DeleteItemPayload(String itemId, int count) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DeleteItemPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SayItInJapanese.MOD_ID, "delete_item"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeleteItemPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, DeleteItemPayload::itemId,
			ByteBufCodecs.VAR_INT, DeleteItemPayload::count,
			DeleteItemPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
