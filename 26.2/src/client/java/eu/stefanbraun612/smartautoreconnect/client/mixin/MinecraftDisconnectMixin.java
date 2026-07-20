package eu.stefanbraun612.smartautoreconnect.client.mixin;

import eu.stefanbraun612.smartautoreconnect.client.VoluntaryDisconnectTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Minecraft.disconnectFromWorld(Component) has exactly one caller in the whole client:
// the pause-menu "Disconnect" button (PauseScreen). Any other disconnect (server kick,
// timeout, connection loss) never goes through this method, which makes it a clean,
// unambiguous signal for "the player did this on purpose."
@Mixin(Minecraft.class)
public class MinecraftDisconnectMixin {
	@Inject(method = "disconnectFromWorld", at = @At("HEAD"))
	private void smartautoreconnect$onVoluntaryDisconnect(Component message, CallbackInfo ci) {
		VoluntaryDisconnectTracker.markVoluntary();
	}
}
