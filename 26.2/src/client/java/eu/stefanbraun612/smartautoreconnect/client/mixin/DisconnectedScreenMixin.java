package eu.stefanbraun612.smartautoreconnect.client.mixin;

import eu.stefanbraun612.smartautoreconnect.client.ReconnectLogic;
import eu.stefanbraun612.smartautoreconnect.client.config.SmartAutoReconnectConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Adds a status label + Cancel button to the vanilla disconnect screen while a retry sequence is
// active, so it's visible (and stoppable) without needing the cancel keybind. Positioned as its
// own fixed-bounds widgets, independent of DisconnectedScreen's own centered LinearLayout, so this
// doesn't need to hook into that layout's private internals.
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
	protected DisconnectedScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void smartautoreconnect$addStatusWidgets(CallbackInfo ci) {
		SmartAutoReconnectConfig config = AutoConfig.getConfigHolder(SmartAutoReconnectConfig.class).getConfig();
		if (config.notificationMode != SmartAutoReconnectConfig.NotificationMode.TOAST_AND_BUTTON) {
			return;
		}
		if (!ReconnectLogic.isRetrying()) {
			return;
		}

		int widgetWidth = 200;
		int x = (this.width - widgetWidth) / 2;
		int y = this.height - 28;

		StringWidget statusLabel = new StringWidget(x, y - 12, widgetWidth, 10,
				Component.literal(ReconnectLogic.statusText()), this.font);
		this.addRenderableWidget(statusLabel);

		Button cancelButton = Button.builder(Component.literal("Cancel Auto-Reconnect"), button -> {
			ReconnectLogic.cancel(this.minecraft);
			button.active = false;
			button.setMessage(Component.literal("Cancelled"));
			statusLabel.setMessage(Component.literal("Smart Auto Reconnect: cancelled."));
		}).bounds(x, y, widgetWidth, 20).build();
		this.addRenderableWidget(cancelButton);
	}
}
