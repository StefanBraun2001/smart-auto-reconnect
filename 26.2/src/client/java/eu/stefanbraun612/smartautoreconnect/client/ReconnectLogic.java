package eu.stefanbraun612.smartautoreconnect.client;

import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class ReconnectLogic {
	// Gap (seconds) before each successive attempt: 30, then +20 each time (50, 70, 90, 110).
	private static final int[] DELAY_SECONDS = {30, 50, 70, 90, 110};
	// How long to wait after the final attempt before declaring the whole sequence failed -
	// there's no attempt afterward to naturally trigger this check, so it needs its own timer.
	private static final int GIVE_UP_GRACE_SECONDS = 20;
	// Toasts render on top of any screen (disconnect screen, title screen, ConnectScreen), unlike
	// chat messages which only ever show up once back in a world's HUD - needed since the whole
	// retry sequence happens while the player is stuck on exactly those screens.
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(6000L);

	// Only ever set from a successful ClientPlayConnectionEvents.JOIN, so it always reflects
	// a server actually reachable via the multiplayer server list/direct connect - null for
	// singleplayer/LAN, which are intentionally out of scope (no meaningful "reconnect" there).
	private static ServerData lastServerData = null;

	private static int ticksUntilNextAttempt = -1; // -1 = no retry sequence in progress
	private static int attemptsSoFar = 0;

	// Set from onDisconnect(), consumed by tick(). Deliberately just a plain flag rather than
	// scheduling work via Minecraft's own client.execute() task queue - for an involuntary
	// disconnect, Minecraft.disconnect() calls dropAllTasks() moments after this event fires,
	// which was silently wiping out a queued runnable (state update + toast) before it ever ran.
	// tick() already runs reliably on the main thread every client tick regardless, so it just
	// picks this up itself instead.
	private static volatile boolean pendingDisconnect = false;

	public static void onJoin(Minecraft client) {
		lastServerData = client.getCurrentServer();
		// The signal itself is set in attemptConnect(), not here - Fabric doesn't guarantee JOIN
		// listener order across mods, so setting it reactively on this same JOIN event risked
		// Smart Auto Attack/Mine checking it before this listener had run.
		if (ticksUntilNextAttempt >= 0 && attemptsSoFar > 0) {
			showToast(client, "Smart Auto Reconnect", "Reconnected successfully.");
		}
		resetSequence();
	}

	public static void onDisconnect(Minecraft client) {
		if (VoluntaryDisconnectTracker.consumeVoluntary()) {
			return;
		}
		SmartAutoReconnectConfig config = AutoConfig.getConfigHolder(SmartAutoReconnectConfig.class).getConfig();
		if (!config.enabled || lastServerData == null) {
			return;
		}
		pendingDisconnect = true;
	}

	// Manually cancels an in-progress retry sequence (e.g. known server maintenance) - bound to a
	// keybind rather than a client command since there's no chat box available on the disconnect/
	// title screen where this is actually needed.
	public static void cancel(Minecraft client) {
		if (ticksUntilNextAttempt < 0) {
			return;
		}
		resetSequence();
		showToast(client, "Smart Auto Reconnect", "Reconnect attempts cancelled.");
	}

	public static void tick(Minecraft client) {
		if (pendingDisconnect) {
			pendingDisconnect = false;
			attemptsSoFar = 0;
			ticksUntilNextAttempt = DELAY_SECONDS[0] * 20;
			showToast(client, "Smart Auto Reconnect", "Disconnected - retrying in " + DELAY_SECONDS[0] + "s (attempt 1/" + DELAY_SECONDS.length + ").");
		}
		if (ticksUntilNextAttempt < 0) {
			return;
		}
		if (--ticksUntilNextAttempt > 0) {
			return;
		}
		if (attemptsSoFar >= DELAY_SECONDS.length) {
			giveUp(client);
			return;
		}
		attemptConnect(client);
		attemptsSoFar++;
		ticksUntilNextAttempt = (attemptsSoFar < DELAY_SECONDS.length
				? DELAY_SECONDS[attemptsSoFar]
				: GIVE_UP_GRACE_SECONDS) * 20;
	}

	private static void attemptConnect(Minecraft client) {
		showToast(client, "Smart Auto Reconnect", "Attempting reconnect (attempt " + (attemptsSoFar + 1) + "/" + DELAY_SECONDS.length + ")...");
		ServerAddress address = ServerAddress.parseString(lastServerData.ip);
		if (address == null) {
			return;
		}
		// Set before the connection attempt even starts (not on the later JOIN success) so it's
		// already visible to every mod's JOIN listener by the time one actually fires, regardless
		// of cross-mod listener order.
		ReconnectSignal.lastAutoReconnectAtMillis = System.currentTimeMillis();
		// Always a fresh TitleScreen, never the currently-shown screen - reusing it would chain
		// each failed attempt's DisconnectedScreen onto the previous one's "Back" target, leaving
		// a stack of error screens the player has to click through one at a time.
		ConnectScreen.startConnecting(new TitleScreen(), client, address, lastServerData, false, null);
	}

	private static void giveUp(Minecraft client) {
		resetSequence();
		showToast(client, "Smart Auto Reconnect", "Gave up after " + DELAY_SECONDS.length + " failed attempts.");
		client.gui.hud.getChat().addClientSystemMessage(Component.literal("Smart Auto Reconnect: gave up after " + DELAY_SECONDS.length + " failed attempts."));
		playGiveUpSound(client);
	}

	private static void showToast(Minecraft client, String title, String message) {
		SystemToast.addOrUpdate(client.gui.toastManager(), TOAST_ID, Component.literal(title), Component.literal(message));
	}

	private static void playGiveUpSound(Minecraft client) {
		Identifier id = Identifier.tryParse("minecraft:block.bell.use");
		SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(id);
		if (sound != null) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
		}
	}

	private static void resetSequence() {
		ticksUntilNextAttempt = -1;
		attemptsSoFar = 0;
	}

	// Used by DisconnectedScreenMixin to decide whether to show the status label/Cancel button.
	// Includes pendingDisconnect, not just ticksUntilNextAttempt >= 0 - on the very first
	// DisconnectedScreen after a fresh disconnect, tick() hasn't consumed that flag yet (the
	// screen is already showing by the time the next client tick runs), so isRetrying() alone
	// would still read false and the widgets would never appear on that first screen.
	public static boolean willRetry() {
		return pendingDisconnect || ticksUntilNextAttempt >= 0;
	}

	// Used by DisconnectedScreenMixin for the status label text, refreshed every screen tick so
	// the countdown stays live.
	public static String statusText() {
		if (attemptsSoFar >= DELAY_SECONDS.length && ticksUntilNextAttempt >= 0) {
			return "Giving up soon...";
		}
		int ticks = ticksUntilNextAttempt >= 0 ? ticksUntilNextAttempt : DELAY_SECONDS[0] * 20;
		int secondsLeft = (ticks + 19) / 20;
		return "Retrying in " + secondsLeft + "s (attempt " + (attemptsSoFar + 1) + "/" + DELAY_SECONDS.length + ")...";
	}
}
