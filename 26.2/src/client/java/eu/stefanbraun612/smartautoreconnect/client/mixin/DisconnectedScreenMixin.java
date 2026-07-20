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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Adds status/control widgets to the vanilla disconnect screen: a status label + Cancel button
// while a retry sequence is scheduled, and a Reconnect Now button whenever there's a known server
// to reconnect to (regardless of sequence state - covers "skip the wait" and "already gave up,
// try once more"). Added as extra children of DisconnectedScreen's own LinearLayout (right before
// it arranges/centers itself), rather than positioned with manual pixel math - that math was fine
// for a single fixed-width button but a wider auto-sized label came out badly off-center.
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
	@Shadow
	@Final
	private LinearLayout layout;

	@Shadow
	protected abstract void repositionElements();

	@Unique
	private StringWidget smartautoreconnect$statusLabel;
	@Unique
	private Button smartautoreconnect$cancelButton;

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

		if (ReconnectLogic.isWaiting()) {
			// Auto-sized to the text (no fixed width), so it can't clip - unlike the fixed-bounds
			// StringWidget constructor, which clips text that doesn't fit rather than wrapping it.
			this.smartautoreconnect$statusLabel = new StringWidget(Component.literal(ReconnectLogic.statusText()), this.font);
			this.layout.addChild((LayoutElement) this.smartautoreconnect$statusLabel);

			this.smartautoreconnect$cancelButton = Button.builder(Component.literal("Cancel Auto-Reconnect"), button -> {
				ReconnectLogic.cancel(this.minecraft);
				button.active = false;
				button.setMessage(Component.literal("Cancelled"));
				this.smartautoreconnect$statusLabel.setMessage(Component.literal("Smart Auto Reconnect: cancelled."));
				this.layout.arrangeElements();
				this.repositionElements();
			}).width(200).build();
			this.layout.addChild((LayoutElement) this.smartautoreconnect$cancelButton);
		}

		if (ReconnectLogic.canReconnect()) {
			Button reconnectNowButton = Button.builder(Component.literal("Reconnect Now"),
					button -> ReconnectLogic.reconnectNow(this.minecraft)).width(200).build();
			this.layout.addChild((LayoutElement) reconnectNowButton);
		}
	}

	@Override
	public void tick() {
		super.tick();
		// Keeps the countdown live - the label's auto-sized width changes as the digit count
		// changes ("in 9s" vs. "in 10s"), so the whole layout needs to re-arrange/re-center itself
		// each time, not just have its text swapped in place.
		if (this.smartautoreconnect$statusLabel != null && this.smartautoreconnect$cancelButton != null
				&& this.smartautoreconnect$cancelButton.active) {
			this.smartautoreconnect$statusLabel.setMessage(Component.literal(ReconnectLogic.statusText()));
			this.layout.arrangeElements();
			this.repositionElements();
		}
	}
}
