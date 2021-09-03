package endorh.simple_config.core.entry;

import endorh.simple_config.clothconfig2.api.AbstractConfigListEntry;
import endorh.simple_config.clothconfig2.api.ConfigEntryBuilder;
import endorh.simple_config.clothconfig2.gui.AbstractConfigScreen;
import endorh.simple_config.clothconfig2.gui.entries.EntryButtonListEntry;
import endorh.simple_config.core.AbstractSimpleConfigEntryHolder;
import endorh.simple_config.core.DummyEntryHolder;
import endorh.simple_config.core.ISimpleConfigEntryHolder;
import endorh.simple_config.core.SelectorEntry;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class PresetSwitcherEntry extends GUIOnlyEntry<String, String, PresetSwitcherEntry> {
	private static final Logger LOGGER = LogManager.getLogger();
	
	protected SelectorEntry<String, String, String, StringEntry> inner;
	protected Map<String, Map<String, Object>> presets;
	protected boolean global;
	protected String path;
	
	public PresetSwitcherEntry(
	  ISimpleConfigEntryHolder parent, String name,
	  Map<String, Map<String, Object>> presets, String path, boolean global
	) {
		super(parent, name, firstKey(presets), false, String.class);
		if (!(parent instanceof AbstractSimpleConfigEntryHolder))
			throw new IllegalArgumentException("Invalid parent for Preset Switcher Entry");
		this.presets = presets;
		this.path = path;
		this.global = global;
		inner = DummyEntryHolder.build(parent, Builders.select(
		  Builders.string(firstKey(presets)),
		  new ArrayList<>(presets.keySet())
		).nameProvider(s -> {
			final String nm = translation + "." + s;
			if (I18n.hasKey(nm))
				return new TranslationTextComponent(nm);
			else return new StringTextComponent(s);
		}));
	}
	
	public static class Builder extends GUIOnlyEntry.Builder<String, String, PresetSwitcherEntry, Builder> {
		
		protected Map<String, Map<String, Object>> presets;
		protected String path;
		protected boolean global;
		
		public Builder(Map<String, Map<String, Object>> presets, String path, boolean global) {
			super(firstKey(presets), String.class);
			this.presets = presets;
			this.path = path;
			this.global = global;
		}
		
		@Override protected PresetSwitcherEntry buildEntry(ISimpleConfigEntryHolder parent, String name) {
			return new PresetSwitcherEntry(parent, name, presets, path, global);
		}
		
		@Override protected Builder createCopy() {
			return new Builder(new HashMap<>(presets), path, global);
		}
	}
	
	protected static String firstKey(Map<String, Map<String, Object>> presets) {
		return presets.keySet().stream().findFirst().orElseThrow(
		  () -> new IllegalArgumentException("At least one preset must be specified"));
	}
	
	public void applyPreset(String name) {
		if (!presets.containsKey(name))
			throw new IllegalArgumentException("Unknown preset: \"" + name + "\"");
		final Map<String, Object> preset = presets.get(name);
		final AbstractSimpleConfigEntryHolder h =
		  (global ? parent.getRoot() : (AbstractSimpleConfigEntryHolder) parent).getChild(path);
		AbstractConfigScreen screen = null;
		if (guiEntry != null) {
			screen = guiEntry.getConfigScreenOrNull();
			if (screen != null) screen.getHistory().startBatch(screen, null);
		}
		for (Entry<String, Object> entry : preset.entrySet()) {
			try {
				h.setForGUI(entry.getKey(), entry.getValue());
			} catch (RuntimeException e) {
				LOGGER.warn(
				  "Unable to set preset (" + name + ") entry: \"" + entry.getKey() + "\"\n" +
				  "Details: " + e.getClass().getSimpleName() + ": " + e.getLocalizedMessage());
			}
		}
		if (screen != null) screen.getHistory().saveBatch(screen);
	}
	
	@Override public Optional<AbstractConfigListEntry<String>> buildGUIEntry(
	  ConfigEntryBuilder builder
	) {
		final EntryButtonListEntry<String, ?> entry = new EntryButtonListEntry<>(
		  getDisplayName(), inner.buildChildGUIEntry(builder), this::applyPreset,
		  () -> new TranslationTextComponent("simple-config.label.preset.apply")
		);
		return Optional.of(entry);
	}
}
