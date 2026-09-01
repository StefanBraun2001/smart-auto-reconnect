package eu.stefanbraun612.smartautoreconnect.client;

import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

public class ReconnectLogic {
	// Toasts render on top of any screen (disconnect screen, title screen, ConnectScreen), unlike
	// chat messages which only ever show up once back in a world's HUD - needed since the whole
	// retry sequence happens while the player is stuck on exactly those screens.
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(6000L);

	// Rapid-disconnect-loop guard: catches "reconnected fine, then kicked again almost instantly"
	// repeating over and over - a pattern a normal server hiccup doesn't produce, but a client-side
	// problem (ban, crash loop, memory issue) does. Tracked independently of the retry sequence
	// itself (which resets attemptsSoFar back to 0 on every successful join), since otherwise this
	// exact pattern would never accumulate a count at all. Threshold/window are config-driven
	// (A0.4.2) - see SmartAutoReconnectConfig.rapidDisconnectLimit/rapidDisconnectWindowSeconds.
	private static final Deque<Long> recentDisconnectTimestamps = new ArrayDeque<>();

	// Only ever set from a successful ClientPlayConnectionEvents.JOIN, so it always reflects
	// a server actually reachable via the multiplayer server list/direct connect - null for
	// singleplayer/LAN, which are intentionally out of scope (no meaningful "reconnect" there).
	private static ServerData lastServerData = null;

	private static int ticksUntilNextAttempt = -1; // -1 = not waiting for a scheduled attempt
	private static int attemptsSoFar = 0;
	// True for the whole time a ConnectScreen attempt is in flight - the countdown for the next
	// attempt only starts once this resolves (success or failure), not when this one started, so
	// a slow/hanging connection attempt (e.g. a dead server taking a long time to time out) can't
	// eat into the next attempt's displayed delay.
	private static boolean connecting = false;
	// Distinguishes reconnectNow()'s one-off attempt (after already giving up) from a real final
	// auto-attempt for toast wording - both set attemptsSoFar to config().retryAttempts.
	private static boolean manualOneOff = false;

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
		if (connecting && attemptsSoFar > 0) {
			showToast(client, "Smart Auto Reconnect", "Reconnected successfully.");
		}
		resetSequence();
	}

	private static SmartAutoReconnectConfig config() {
		return AutoConfig.getConfigHolder(SmartAutoReconnectConfig.class).getConfig();
	}

	// Delay before attempt (attemptIndex + 1), attemptIndex zero-based - reproduces the original
	// hardcoded 30/50/70/90/110 sequence when initialRetryDelaySeconds=30, increment=20.
	private static int delaySeconds(int attemptIndex, SmartAutoReconnectConfig config) {
		return config.initialRetryDelaySeconds + attemptIndex * config.retryDelayIncrementSeconds;
	}

	public static void onDisconnect(Minecraft client) {
		if (VoluntaryDisconnectTracker.consumeVoluntary()) {
			return;
		}
		SmartAutoReconnectConfig config = config();
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

	// Jumps straight to a reconnect attempt: skips the rest of the current wait if one's in
	// progress, or fires a single one-off attempt if the sequence already gave up (or was
	// cancelled, or never started) - that one-off attempt schedules no further automatic retries
	// of its own if it fails too.
	public static void reconnectNow(Minecraft client) {
		if (connecting || lastServerData == null) {
			return;
		}
		if (ticksUntilNextAttempt >= 0) {
			ticksUntilNextAttempt = 0;
			return;
		}
		manualOneOff = true;
		attemptsSoFar = config().retryAttempts;
		if (!attemptConnect(client)) {
			manualOneOff = false;
		}
	}

	public static void tick(Minecraft client) {
		if (pendingDisconnect) {
			pendingDisconnect = false;

			SmartAutoReconnectConfig config = config();
			if (config.rapidDisconnectGuardEnabled && recordDisconnectAndCheckRapidLoop(config)) {
				resetSequence();
				recentDisconnectTimestamps.clear();
				showToast(client, "Smart Auto Reconnect", "Aborted - disconnected " + (config.rapidDisconnectLimit + 1) + "+ times within " + formatDuration(config.rapidDisconnectWindowSeconds) + " (likely a client-side issue, not the server).");
				playGiveUpSound(client);
				return;
			}

			attemptsSoFar = 0;
			connecting = false;
			manualOneOff = false;
			ticksUntilNextAttempt = delaySeconds(0, config) * 20;
			showToast(client, "Smart Auto Reconnect", "Disconnected - retrying in " + delaySeconds(0, config) + "s (attempt 1/" + config.retryAttempts + ").");
		}

		resolveConnectingIfNeeded(client);
		if (connecting) {
			return; // still waiting on this attempt to resolve
		}

		if (ticksUntilNextAttempt < 0) {
			return;
		}
		if (--ticksUntilNextAttempt > 0) {
			return;
		}
		attemptsSoFar++;
		attemptConnect(client);
	}

	// A failed connection attempt has no equivalent to pendingDisconnect - there's no event fired
	// before vanilla creates the new DisconnectedScreen for it (unlike an established connection
	// being lost, a failed outbound connect never reaches ClientPlayConnectionEvents.DISCONNECT at
	// all). So relying solely on tick() to notice "no longer on ConnectScreen" risked exactly the
	// same screen-vs-state-update race pendingDisconnect fixes for the very first disconnect: the
	// new DisconnectedScreen's init() could run before tick() got a chance to reschedule/give up,
	// leaving that screen's widgets stuck showing the stale (pre-failure) state. Called both from
	// tick() every tick, and proactively from DisconnectedScreenMixin's init() the moment a new
	// DisconnectedScreen appears, so the state is always current by the time widgets are decided.
	public static void resolveConnectingIfNeeded(Minecraft client) {
		if (!connecting) {
			return;
		}
		if (client.level != null) {
			// Actually succeeded - onJoin() handles (or already handled) resetting everything.
			connecting = false;
			return;
		}
		if (client.gui.screen() instanceof ConnectScreen) {
			return; // still connecting - don't start any countdown until this resolves
		}
		connecting = false;
		SmartAutoReconnectConfig config = config();
		if (attemptsSoFar >= config.retryAttempts) {
			if (manualOneOff) {
				// Not a real give-up (attemptsSoFar only reads as "maxed out" because
				// reconnectNow() reuses that value for its one-off attempt) - reusing giveUp()'s
				// "Gave up after 5 failed attempts" wording here would be bogus, since this was
				// just the one attempt the player asked for.
				resetSequence();
				showToast(client, "Smart Auto Reconnect", "Manual reconnect attempt failed.");
			} else {
				giveUp(client, config);
			}
		} else {
			ticksUntilNextAttempt = delaySeconds(attemptsSoFar, config) * 20;
			showToast(client, "Smart Auto Reconnect", "Attempt failed - retrying in " + delaySeconds(attemptsSoFar, config) + "s (attempt " + (attemptsSoFar + 1) + "/" + config.retryAttempts + ").");
		}
	}

	private static boolean attemptConnect(Minecraft client) {
		String attemptLabel = manualOneOff
				? "manual retry"
				: "attempt " + attemptsSoFar + "/" + config().retryAttempts;
		showToast(client, "Smart Auto Reconnect", "Attempting reconnect (" + attemptLabel + ")...");
		ServerAddress address = ServerAddress.parseString(lastServerData.ip);
		if (address == null) {
			return false;
		}
		// Set before the connection attempt even starts (not on the later JOIN success) so it's
		// already visible to every mod's JOIN listener by the time one actually fires, regardless
		// of cross-mod listener order.
		ReconnectSignal.lastAutoReconnectAtMillis = System.currentTimeMillis();
		// Set before startConnecting() swaps the screen (not after it returns) - DisconnectedScreenMixin's
		// removed() hook reads this flag to tell "we're the ones navigating away" from "the player did,
		// via a vanilla button" apart, and Screen.removed() fires synchronously as part of the screen
		// swap below, not on some later tick.
		connecting = true;
		// Always a fresh TitleScreen, never the currently-shown screen - reusing it would chain
		// each failed attempt's DisconnectedScreen onto the previous one's "Back" target, leaving
		// a stack of error screens the player has to click through one at a time.
		ConnectScreen.startConnecting(new TitleScreen(), client, address, lastServerData, false, null);
		return true;
	}

	private static void giveUp(Minecraft client, SmartAutoReconnectConfig config) {
		resetSequence();
		showToast(client, "Smart Auto Reconnect", "Gave up after " + config.retryAttempts + " failed attempts.");
		playGiveUpSound(client);
	}

	private static void showToast(Minecraft client, String title, String message) {
		SystemToast.addOrUpdate(client.gui.toastManager(), TOAST_ID, Component.literal(title), Component.literal(message));
	}

	// Minutes for whole multiples of 60s (matches the original hardcoded "5 minutes" wording),
	// seconds otherwise - keeps the abort toast readable across arbitrary configured windows.
	private static String formatDuration(int seconds) {
		if (seconds >= 60 && seconds % 60 == 0) {
			int minutes = seconds / 60;
			return minutes + (minutes == 1 ? " minute" : " minutes");
		}
		return seconds + "s";
	}

	private static void playGiveUpSound(Minecraft client) {
		SmartAutoReconnectConfig config = config();
		if (!config.playSoundOnAutoStop) {
			return;
		}
		SoundUtil.play(client, config.autoStopSound);
	}

	// Records "now" as a disconnect and prunes anything outside the rolling window, then reports
	// whether that pushed the count past the limit. Deliberately counts every involuntary
	// disconnect that reaches this point (not just ones that end a retry sequence), since a
	// disconnect right after a successful reconnect is exactly the pattern this guards against.
	private static boolean recordDisconnectAndCheckRapidLoop(SmartAutoReconnectConfig config) {
		long now = System.currentTimeMillis();
		recentDisconnectTimestamps.addLast(now);
		long windowMillis = config.rapidDisconnectWindowSeconds * 1000L;
		while (!recentDisconnectTimestamps.isEmpty()
				&& now - recentDisconnectTimestamps.peekFirst() > windowMillis) {
			recentDisconnectTimestamps.pollFirst();
		}
		return recentDisconnectTimestamps.size() > config.rapidDisconnectLimit;
	}

	private static void resetSequence() {
		ticksUntilNextAttempt = -1;
		attemptsSoFar = 0;
		connecting = false;
		manualOneOff = false;
	}

	// Used by DisconnectedScreenMixin to decide whether to show the status label/Cancel button -
	// only meaningful while an attempt is actually scheduled. The screen never appears while
	// connecting == true (that's when ConnectScreen is up instead), so that state doesn't need
	// to be handled here.
	public static boolean isWaiting() {
		return pendingDisconnect || ticksUntilNextAttempt >= 0;
	}

	// Used by DisconnectedScreenMixin to decide whether to show the Reconnect Now button - always
	// available as long as there's a known server to reconnect to, regardless of sequence state.
	public static boolean canReconnect() {
		return lastServerData != null;
	}

	// Used by DisconnectedScreenMixin's removed() hook to tell apart "this screen is going away
	// because we ourselves just started a scheduled/manual attempt" (connecting was set true in
	// attemptConnect() before the screen swap that triggers removed()) from "the player navigated
	// away some other way" (vanilla back button, another mod's screen, etc.) - only the latter
	// should cancel a pending sequence.
	public static boolean isConnecting() {
		return connecting;
	}

	// Used by DisconnectedScreenMixin for the status label text, refreshed every screen tick so
	// the countdown stays live.
	public static String statusText() {
		SmartAutoReconnectConfig config = config();
		int ticks = ticksUntilNextAttempt >= 0 ? ticksUntilNextAttempt : delaySeconds(0, config) * 20;
		int secondsLeft = (ticks + 19) / 20;
		return "Retrying in " + secondsLeft + "s (attempt " + (attemptsSoFar + 1) + "/" + config.retryAttempts + ")...";
	}
}
