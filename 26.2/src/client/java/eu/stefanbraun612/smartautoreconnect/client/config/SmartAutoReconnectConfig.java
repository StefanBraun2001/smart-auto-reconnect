package eu.stefanbraun612.smartautoreconnect.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "smartautoreconnect")
public class SmartAutoReconnectConfig implements ConfigData {
	@ConfigEntry.Gui.Tooltip
	public boolean enabled = true;

	public enum NotificationMode {
		TOAST_ONLY,
		TOAST_AND_BUTTON
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public NotificationMode notificationMode = NotificationMode.TOAST_AND_BUTTON;
}
