package eu.stefanbraun612.smartautoreconnect.client;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

// Bundled custom sound event, registered on client init so it's resolvable by ID
// the same way vanilla sounds are (see SoundUtil.play's BuiltInRegistries lookup).
public class SmartAutoReconnectSounds {
	public static final SoundEvent AUTO_STOP = register("auto_stop");

	public static void init() {
		// No-op - just forces this class (and its static SoundEvent registration) to load.
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(SmartAutoReconnectClient.MOD_ID, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
