package eu.stefanbraun612.smartautoreconnect.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "smartautoreconnect")
public class SmartAutoReconnectConfig implements ConfigData {
	public boolean enabled = true;

	public enum NotificationMode {
		TOAST_ONLY,
		TOAST_AND_BUTTON
	}

	public NotificationMode notificationMode = NotificationMode.TOAST_AND_BUTTON;

	// Attempt N's delay (seconds) = initialRetryDelaySeconds + (N-1) * retryDelayIncrementSeconds.
	// Defaults reproduce the original hardcoded sequence: 30, 50, 70, 90, 110.
	public int retryAttempts = 5;
	public int initialRetryDelaySeconds = 30;
	public int retryDelayIncrementSeconds = 20;

	public boolean rapidDisconnectGuardEnabled = true;
	public int rapidDisconnectLimit = 4;
	public int rapidDisconnectWindowSeconds = 300;
}
