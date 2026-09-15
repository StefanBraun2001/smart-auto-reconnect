package eu.stefanbraun612.smartautoreconnect.client;

// Public, stable class/field name that Smart Auto Attack and Smart Auto Mine check for
// via reflection (no compile-time dependency either way) to tell a scripted reconnect
// from a manual one. Keep this class/field name unchanged if touched again.
public class ReconnectSignal {
	public static volatile long lastAutoReconnectAtMillis = -1;
}
