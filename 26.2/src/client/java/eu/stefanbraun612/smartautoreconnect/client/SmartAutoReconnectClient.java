package eu.stefanbraun612.smartautoreconnect.client;

import com.mojang.blaze3d.platform.InputConstants;
import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SmartAutoReconnectClient implements ClientModInitializer {
	public static final String MOD_ID = "smartautoreconnect";

	private static KeyMapping cancelKey;

	@Override
	public void onInitializeClient() {
		AutoConfig.register(SmartAutoReconnectConfig.class, GsonConfigSerializer::new);

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		cancelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.smartautoreconnect.cancel",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				category
		));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ReconnectLogic.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ReconnectLogic.onDisconnect(client));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (cancelKey.consumeClick()) {
				ReconnectLogic.cancel(client);
			}
			ReconnectLogic.tick(client);
		});
	}
}
