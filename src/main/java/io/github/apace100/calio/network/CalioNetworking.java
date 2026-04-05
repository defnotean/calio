package io.github.apace100.calio.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class CalioNetworking {

    public static final Identifier SYNC_DATA_OBJECT_REGISTRY_ID = Identifier.fromNamespaceAndPath("calio", "sync_data_object_registry");

    public record SyncDataObjectRegistryPayload(Identifier registryId, byte[] data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncDataObjectRegistryPayload> TYPE =
            new CustomPacketPayload.Type<>(SYNC_DATA_OBJECT_REGISTRY_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncDataObjectRegistryPayload> CODEC =
            StreamCodec.of(
                (buf, payload) -> {
                    buf.writeIdentifier(payload.registryId);
                    buf.writeByteArray(payload.data);
                },
                buf -> {
                    Identifier registryId = buf.readIdentifier();
                    byte[] data = buf.readByteArray();
                    return new SyncDataObjectRegistryPayload(registryId, data);
                }
            );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        try {
            // Try Fabric API PayloadTypeRegistry if available
            Class<?> registryClass = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
            // PLAY_S2C is the static field in newer versions, playS2C() is the method in older
            try {
                Object s2c = registryClass.getMethod("playS2C").invoke(null);
                s2c.getClass().getMethod("register", CustomPacketPayload.Type.class, net.minecraft.network.codec.StreamCodec.class)
                    .invoke(s2c, SyncDataObjectRegistryPayload.TYPE, SyncDataObjectRegistryPayload.CODEC);
            } catch (Exception e) {
                // Fallback: try PLAY_S2C field
                Object s2c = registryClass.getField("PLAY_S2C").get(null);
                s2c.getClass().getMethod("register", CustomPacketPayload.Type.class, net.minecraft.network.codec.StreamCodec.class)
                    .invoke(s2c, SyncDataObjectRegistryPayload.TYPE, SyncDataObjectRegistryPayload.CODEC);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to register Calio networking payload", e);
        }
    }
}
