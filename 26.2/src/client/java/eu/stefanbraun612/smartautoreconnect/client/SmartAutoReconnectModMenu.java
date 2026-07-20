package eu.stefanbraun612.smartautoreconnect.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfigClient;

public class SmartAutoReconnectModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> AutoConfigClient.getConfigScreen(SmartAutoReconnectConfig.class, parent).get();
	}
}
