package eu.stefanbraun612.smartautoreconnect.client.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hand-built (not annotation-generated) Cloth Config screen, so that fields can be
 * grouped into tabs and dependent fields can be hidden via Requirement - neither is
 * possible with AutoConfig's reflection-based screen generation.
 */
public class SmartAutoReconnectConfigScreen {

	private static final String PREFIX = "text.autoconfig.smartautoreconnect.";

	private static Component option(String field) {
		return Component.translatable(PREFIX + "option." + field);
	}

	private static Component tooltip(String field) {
		return Component.translatable(PREFIX + "option." + field + ".@Tooltip");
	}

	private static Component category(String key) {
		return Component.translatable(PREFIX + "category." + key);
	}

	public static Screen build(Screen parent) {
		ConfigHolder<SmartAutoReconnectConfig> holder = AutoConfig.getConfigHolder(SmartAutoReconnectConfig.class);
		SmartAutoReconnectConfig config = holder.getConfig();
		SmartAutoReconnectConfig defaults = new SmartAutoReconnectConfig();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable(PREFIX + "title"))
				.setSavingRunnable(holder::save);
		ConfigEntryBuilder entryBuilder = builder.entryBuilder();

		// --- General tab ---

		ConfigCategory general = builder.getOrCreateCategory(category("general"));

		general.addEntry(entryBuilder
				.startBooleanToggle(option("enabled"), config.enabled)
				.setDefaultValue(defaults.enabled)
				.setTooltip(tooltip("enabled"))
				.setSaveConsumer(v -> config.enabled = v)
				.build());

		general.addEntry(entryBuilder
				.startEnumSelector(option("notificationMode"), SmartAutoReconnectConfig.NotificationMode.class, config.notificationMode)
				.setDefaultValue(defaults.notificationMode)
				.setTooltip(tooltip("notificationMode"))
				.setSaveConsumer(v -> config.notificationMode = v)
				.build());

		// --- Retry Attempts tab ---

		ConfigCategory retry = builder.getOrCreateCategory(category("retry"));

		retry.addEntry(entryBuilder
				.startIntField(option("retryAttempts"), config.retryAttempts)
				.setDefaultValue(defaults.retryAttempts)
				.setTooltip(tooltip("retryAttempts"))
				.setMin(1)
				.setSaveConsumer(v -> config.retryAttempts = v)
				.build());

		retry.addEntry(entryBuilder
				.startIntField(option("initialRetryDelaySeconds"), config.initialRetryDelaySeconds)
				.setDefaultValue(defaults.initialRetryDelaySeconds)
				.setTooltip(tooltip("initialRetryDelaySeconds"))
				.setMin(1)
				.setSaveConsumer(v -> config.initialRetryDelaySeconds = v)
				.build());

		retry.addEntry(entryBuilder
				.startIntField(option("retryDelayIncrementSeconds"), config.retryDelayIncrementSeconds)
				.setDefaultValue(defaults.retryDelayIncrementSeconds)
				.setTooltip(tooltip("retryDelayIncrementSeconds"))
				.setMin(0)
				.setSaveConsumer(v -> config.retryDelayIncrementSeconds = v)
				.build());

		// --- Reconnect-Loop Safeguard tab ---

		ConfigCategory safeguard = builder.getOrCreateCategory(category("safeguard"));

		BooleanListEntry rapidDisconnectGuardEnabled = entryBuilder
				.startBooleanToggle(option("rapidDisconnectGuardEnabled"), config.rapidDisconnectGuardEnabled)
				.setDefaultValue(defaults.rapidDisconnectGuardEnabled)
				.setTooltip(tooltip("rapidDisconnectGuardEnabled"))
				.setSaveConsumer(v -> config.rapidDisconnectGuardEnabled = v)
				.build();
		safeguard.addEntry(rapidDisconnectGuardEnabled);

		safeguard.addEntry(entryBuilder
				.startIntField(option("rapidDisconnectLimit"), config.rapidDisconnectLimit)
				.setDefaultValue(defaults.rapidDisconnectLimit)
				.setTooltip(tooltip("rapidDisconnectLimit"))
				.setMin(1)
				.setSaveConsumer(v -> config.rapidDisconnectLimit = v)
				.setDisplayRequirement(Requirement.isTrue(rapidDisconnectGuardEnabled))
				.build());

		safeguard.addEntry(entryBuilder
				.startIntField(option("rapidDisconnectWindowSeconds"), config.rapidDisconnectWindowSeconds)
				.setDefaultValue(defaults.rapidDisconnectWindowSeconds)
				.setTooltip(tooltip("rapidDisconnectWindowSeconds"))
				.setMin(1)
				.setSaveConsumer(v -> config.rapidDisconnectWindowSeconds = v)
				.setDisplayRequirement(Requirement.isTrue(rapidDisconnectGuardEnabled))
				.build());

		return builder.build();
	}
}
