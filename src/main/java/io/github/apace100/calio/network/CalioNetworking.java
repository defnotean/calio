package io.github.apace100.calio.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class CalioNetworking {

    public static final ResourceLocation SYNC_DATA_OBJECT_REGISTRY_ID = ResourceLocation.fromNamespaceAndPath("calio", "sync_data_object_registry");

    public record SyncDataObjectRegistryPayload(ResourceLocation registryId, byte[] data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncDataObjectRegistryPayload> TYPE =
            new CustomPacketPayload.Type<>(SYNC_DATA_OBJECT_REGISTRY_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncDataObjectRegistryPayload> CODEC =
            StreamCodec.of(
                (buf, payload) -> {
                    buf.writeResourceLocation(payload.registryId);
                    buf.writeByteArray(payload.data);
                },
                buf -> {
                    ResourceLocation registryId = buf.readResourceLocation();
                    byte[] data = buf.readByteArray();
                    return new SyncDataObjectRegistryPayload(registryId, data);
                }
            );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(SyncDataObjectRegistryPayload.TYPE, SyncDataObjectRegistryPayload.CODEC);
    }
}
