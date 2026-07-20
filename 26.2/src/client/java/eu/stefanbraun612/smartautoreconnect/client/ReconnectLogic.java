package eu.stefanbraun612.smartautoreconnect.client;

import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
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

	// Only ever set from a successful ClientPlayConnectionEvents.JOIN, so it always reflects
	// a server actually reachable via the multiplayer server list/direct connect - null for
	// singleplayer/LAN, which are intentionally out of scope (no meaningful "reconnect" there).
	private static ServerData lastServerData = null;

	private static int ticksUntilNextAttempt = -1; // -1 = no retry sequence in progress
	private static int attemptsSoFar = 0;

	public static void onJoin(Minecraft client) {
		lastServerData = client.getCurrentServer();
		// attemptsSoFar > 0 guards against misattributing a coincidental manual reconnect
		// that happens to land during the pre-first-attempt wait as a scripted one.
		if (ticksUntilNextAttempt >= 0 && attemptsSoFar > 0) {
			ReconnectSignal.lastAutoReconnectAtMillis = System.currentTimeMillis();
		}
		resetSequence();
	}

	public static void onDisconnect() {
		if (VoluntaryDisconnectTracker.consumeVoluntary()) {
			return;
		}
		SmartAutoReconnectConfig config = AutoConfig.getConfigHolder(SmartAutoReconnectConfig.class).getConfig();
		if (!config.enabled || lastServerData == null) {
			return;
		}
		attemptsSoFar = 0;
		ticksUntilNextAttempt = DELAY_SECONDS[0] * 20;
	}

	public static void tick(Minecraft client) {
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
		ServerAddress address = ServerAddress.parseString(lastServerData.ip);
		if (address == null) {
			return;
		}
		Screen parent = client.gui.screen() != null ? client.gui.screen() : new TitleScreen();
		ConnectScreen.startConnecting(parent, client, address, lastServerData, false, null);
	}

	private static void giveUp(Minecraft client) {
		resetSequence();
		client.gui.hud.getChat().addClientSystemMessage(Component.literal("Smart Auto Reconnect: gave up after 5 failed attempts."));
		playGiveUpSound(client);
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
}
