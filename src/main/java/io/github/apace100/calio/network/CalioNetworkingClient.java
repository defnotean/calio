package io.github.apace100.calio.network;

import io.github.apace100.calio.registry.DataObjectRegistry;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class CalioNetworkingClient {

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(
            CalioNetworking.SyncDataObjectRegistryPayload.TYPE,
            (payload, context) -> {
                ResourceLocation registryId = payload.registryId();
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                Minecraft minecraft = context.client();
                DataObjectRegistry.getRegistry(registryId).receive(buf,
                    minecraft.hasSingleplayerServer() ? r -> {} : minecraft::execute);
            }
        );
    }
}
