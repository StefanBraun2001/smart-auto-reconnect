package eu.stefanbraun612.smartautoreconnect.client;

// Set by MinecraftDisconnectMixin the instant the pause-menu "Disconnect" button is
// clicked - the only caller of Minecraft.disconnectFromWorld() in the whole client -
// so ClientPlayConnectionEvents.DISCONNECT can tell voluntary disconnects apart from
// server kicks/timeouts/connection loss, which reach the event the same generic way.
public class VoluntaryDisconnectTracker {
	private static volatile boolean voluntary = false;

	public static void markVoluntary() {
		voluntary = true;
	}

	public static boolean consumeVoluntary() {
		boolean was = voluntary;
		voluntary = false;
		return was;
	}
}
