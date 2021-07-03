package endorh.simple_config.core;

import endorh.simple_config.SimpleConfigMod.Config;
import endorh.simple_config.core.SimpleConfig.ConfigReflectiveOperationException;
import endorh.simple_config.core.SimpleConfig.IGUIEntry;
import endorh.simple_config.core.SimpleConfig.NoSuchConfigGroupError;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import org.jetbrains.annotations.ApiStatus.Internal;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A Config category<br>
 * Has its own tab in the GUI, and it's no different to a top-level group in the config<br>
 * Each type of config (Client/Server (and Common)) has its default category too,
 * containing all the root entries/groups<br>
 * You may access any config entry under a category with {@link SimpleConfigCategory#get(String)},
 * which accepts dot-separated paths to entries, taking into account their group structure
 */
public class SimpleConfigCategory extends AbstractSimpleConfigEntryHolder {
	public final SimpleConfig parent;
	public final String name;
	protected final String title;
	protected final @Nullable Consumer<SimpleConfigCategory> baker;
	protected Map<String, SimpleConfigGroup> groups;
	protected List<IGUIEntry> order;
	protected @Nullable BiConsumer<SimpleConfigCategory, ConfigCategory> decorator;
	protected @Nullable ResourceLocation background;
	
	@Internal protected SimpleConfigCategory(
	  SimpleConfig parent, String name, String title, @Nullable Consumer<SimpleConfigCategory> baker
	) {
		this.parent = parent;
		this.name = name;
		this.title = title;
		this.baker = baker;
		root = parent;
	}
	
	@Internal protected void build(
	  Map<String, AbstractConfigEntry<?, ?, ?, ?>> entries, Map<String, SimpleConfigGroup> groups,
	  Map<String, ConfigValue<?>> specValues,
	  List<IGUIEntry> order
	) {
		if (this.entries != null)
			throw new IllegalStateException("Called build() twice");
		this.entries = entries;
		this.groups = groups;
		children = groups;
		this.specValues = specValues;
		this.order = order;
	}
	
	@Override protected String getPath() {
		return parent != null? parent.getPath() + "." + name : name;
	}
	
	@Override public void markDirty(boolean dirty) {
		super.markDirty(dirty);
		if (dirty) parent.markDirty(true);
	}
	
	/**
	 * Get a config group
	 *
	 * @param path Name or dot-separated path to the group
	 * @throws NoSuchConfigGroupError if the group is not found
	 */
	@SuppressWarnings("unused")
	public SimpleConfigGroup getGroup(String path) {
		if (path.contains(".")) {
			final String[] split = path.split("\\.", 2);
			if (groups.containsKey(split[0]))
				return groups.get(split[0]).getGroup(split[1]);
		} else if (groups.containsKey(path))
			return groups.get(path);
		throw new NoSuchConfigGroupError(path);
	}
	
	@OnlyIn(Dist.CLIENT)
	protected void buildGUI(ConfigBuilder builder, ConfigEntryBuilder entryBuilder) {
		ConfigCategory category = builder.getOrCreateCategory(getTitle());
		if (background != null)
			category.setBackground(background);
		else if (parent.background != null)
			category.setBackground(parent.background);
		if (!order.isEmpty()) {
			for (IGUIEntry entry : order)
				entry.buildGUI(category, entryBuilder, this);
		}
		if (decorator != null)
			decorator.accept(this, category);
	}
	
	@Override
	protected void bake() {
		for (SimpleConfigGroup group : groups.values())
			group.bake();
		if (baker != null)
			baker.accept(this);
	}
	
	/**
	 * Bakes all the backing fields<br>
	 */
	protected void bakeFields() {
		try {
			for (SimpleConfigGroup group : groups.values())
				group.bakeFields();
			for (AbstractConfigEntry<?, ?, ?, ?> entry : entries.values())
				entry.bakeField(this);
		} catch (IllegalAccessException e) {
			throw new ConfigReflectiveOperationException(
			  "Could not access mod config field during config bake\n  Details: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Commits any changes in the backing fields to the actual config file<br>
	 * You may also call this method on the root {@link SimpleConfig}
	 */
	public void commitFields() {
		try {
			for (SimpleConfigGroup group : groups.values())
				group.commitFields();
			for (AbstractConfigEntry<?, ?, ?, ?> entry : entries.values())
				entry.commitField(this);
		} catch (IllegalAccessException e) {
			throw new ConfigReflectiveOperationException(
			  "Could not access mod config field during config commit\n  Details: " + e.getMessage(), e);
		}
	}
	
	protected ITextComponent getTitle() {
		if (Config.advanced.translation_debug_mode)
			return new StringTextComponent(title);
		return new TranslationTextComponent(title);
	}
}