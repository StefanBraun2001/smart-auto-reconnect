package eu.stefanbraun612.smartautoreconnect.client;

import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class SmartAutoReconnectClient implements ClientModInitializer {
	public static final String MOD_ID = "smartautoreconnect";

	@Override
	public void onInitializeClient() {
		AutoConfig.register(SmartAutoReconnectConfig.class, GsonConfigSerializer::new);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ReconnectLogic.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ReconnectLogic.onDisconnect(client));
		ClientTickEvents.END_CLIENT_TICK.register(ReconnectLogic::tick);
	}
}
