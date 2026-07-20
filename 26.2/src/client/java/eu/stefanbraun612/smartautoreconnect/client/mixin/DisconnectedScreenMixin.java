package eu.stefanbraun612.smartautoreconnect.client.mixin;

import eu.stefanbraun612.smartautoreconnect.client.ReconnectLogic;
import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Adds a status label + Cancel button to the vanilla disconnect screen while a retry sequence is
// active, so it's visible (and stoppable) without needing the cancel keybind. Added as extra
// children of DisconnectedScreen's own LinearLayout (right before it arranges/centers itself),
// rather than positioned with manual pixel math - that math was fine for the button (same width
// as vanilla's own) but the wider status label came out badly off-center.
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
	@Shadow
	@Final
	private LinearLayout layout;

	protected DisconnectedScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V"))
	private void smartautoreconnect$addStatusWidgets(CallbackInfo ci) {
		SmartAutoReconnectConfig config = AutoConfig.getConfigHolder(SmartAutoReconnectConfig.class).getConfig();
		if (config.notificationMode != SmartAutoReconnectConfig.NotificationMode.TOAST_AND_BUTTON) {
			return;
		}
		if (!ReconnectLogic.isRetrying()) {
			return;
		}

		// Auto-sized to the text (no fixed width), so it can't clip - unlike the fixed-bounds
		// StringWidget constructor, which clips text that doesn't fit rather than wrapping it.
		StringWidget statusLabel = new StringWidget(Component.literal(ReconnectLogic.statusText()), this.font);
		this.layout.addChild((LayoutElement) statusLabel);

		Button cancelButton = Button.builder(Component.literal("Cancel Auto-Reconnect"), button -> {
			ReconnectLogic.cancel(this.minecraft);
			button.active = false;
			button.setMessage(Component.literal("Cancelled"));
			statusLabel.setMessage(Component.literal("Smart Auto Reconnect: cancelled."));
		}).width(200).build();
		this.layout.addChild((LayoutElement) cancelButton);
	}
}
