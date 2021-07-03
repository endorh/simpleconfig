package endorh.simple_config.core;

import endorh.simple_config.SimpleConfigMod;
import endorh.simple_config.core.SimpleConfig.IGUIEntry;
import endorh.simple_config.core.SimpleConfig.InvalidConfigValueTypeException;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.FieldBuilder;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import org.jetbrains.annotations.ApiStatus.Internal;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.*;

import static endorh.simple_config.core.ReflectionUtil.setBackingField;

/**
 * An abstract config entry, which may or may not produce an entry in
 * the actual config and/or the config GUI<br>
 * Entries should not be accessed by API users after
 * their config has been registered. Doing so will result in
 * undefined behaviour.<br>
 * In particular, users can not modify the default
 * value/bounds/validators of an entry after the registering phase
 * has ended.<br>
 * Subclasses may override {@link AbstractConfigEntry#buildConfigEntry}
 * and {@link AbstractConfigEntry#buildGUIEntry} to generate the appropriate
 * entries in both ends
 *
 * @param <V> The type of the value held by the entry
 * @param <Config> The type of the associated config entry
 * @param <Gui> The type of the associated GUI config entry
 * @param <Self> The actual subtype of this entry to be
 *              returned by builder-like methods
 */
public abstract class AbstractConfigEntry<V, Config, Gui, Self extends AbstractConfigEntry<V, Config, Gui, Self>>
  implements IGUIEntry, ITooltipEntry<V, Gui, Self>, IErrorEntry<V, Gui, Self> {
	protected final Class<?> typeClass;
	protected String name = null;
	protected @Nullable String translation = null;
	protected @Nullable String tooltip = null;
	protected @Nullable String comment = null;
	protected boolean requireRestart = false;
	protected @Nullable Function<V, Optional<ITextComponent>> errorSupplier = null;
	protected @Nullable Function<V, Optional<ITextComponent[]>> tooltipSupplier = null;
	protected @Nullable Function<Gui, Optional<ITextComponent>> guiErrorSupplier = null;
	protected @Nullable Function<Gui, Optional<ITextComponent[]>> guiTooltipSupplier = null;
	protected @Nullable BiConsumer<Gui, ISimpleConfigEntryHolder> saver = null;
	protected final V value;
	protected @Nullable Field backingField;
	protected ISimpleConfigEntryHolder parent;
	protected boolean dirty = false;
	protected List<Object> nameArgs = new ArrayList<>();
	protected List<Object> tooltipArgs = new ArrayList<>();
	protected @Nullable AbstractConfigListEntry<Gui> guiEntry = null;
	
	protected AbstractConfigEntry(V value, Class<?> typeClass) {
		this.value = value;
		this.typeClass = typeClass;
	}
	
	/**
	 * Used in error messages
	 */
	protected String getPath() {
		if (parent instanceof AbstractSimpleConfigEntryHolder) {
			return ((AbstractSimpleConfigEntryHolder) parent).getPath() + "." + name;
		} else return name;
	}
	
	protected void setParent(ISimpleConfigEntryHolder config) {
		this.parent = config;
	}
	
	@SuppressWarnings("unchecked")
	protected Self self() {
		return (Self) this;
	}
	
	protected Self name(String name) {
		this.name = name;
		return self();
	}
	
	@SuppressWarnings("UnusedReturnValue")
	protected Self translate(String translation) {
		this.translation = translation;
		return self();
	}
	
	@SuppressWarnings("UnusedReturnValue")
	protected Self tooltip(String translation) {
		this.tooltip = translation;
		return self();
	}
	
	@Override public Self guiTooltip(Function<Gui, Optional<ITextComponent[]>> tooltipSupplier) {
		this.guiTooltipSupplier = tooltipSupplier;
		return self();
	}
	
	@Override public Self tooltip(Function<V, Optional<ITextComponent[]>> tooltipSupplier) {
		this.tooltipSupplier = tooltipSupplier;
		return self();
	}
	
	@Override public Self guiError(Function<Gui, Optional<ITextComponent>> errorSupplier) {
		this.guiErrorSupplier = errorSupplier;
		return self();
	}
	
	@Override public Self error(Function<V, Optional<ITextComponent>> errorSupplier) {
		this.errorSupplier = errorSupplier;
		return self();
	}
	
	@OnlyIn(Dist.CLIENT)
	protected String fillArgs(String translation, V value, List<Object> args) {
		final Object[] arr = args.stream().map(a -> {
			if (a instanceof Function) {
				try {
					//noinspection unchecked
					return ((Function<V, ?>) a).apply(value);
				} catch (ClassCastException e) {
					throw new InvalidConfigValueTypeException(
					  getPath(), e, "A translation argument provider expected an invalid value type");
				}
			} else if (a instanceof Supplier) {
				return ((Supplier<?>) a).get();
			} else return a;
		}).toArray();
		return I18n.format(translation, arr);
	}
	
	/**
	 * Set the arguments that will be passed to the tooltip translation key<br>
	 * As a special case, {@code Function}s and {@code Supplier}s passed
	 * will be invoked before being passed as arguments, with the entry value
	 * as argument
	 */
	public Self tooltipArgs(Object... args) {
		for (Object o : args) { // Check function types to fail fast
			if (o instanceof Function) {
				try {
					//noinspection unchecked
					((Function<V, ?>) o).apply(value);
				} catch (ClassCastException e) {
					throw new InvalidConfigValueTypeException(
					  getPath(), e, "A translation argument provider expected an invalid value type");
				}
			}
		}
		tooltipArgs.clear();
		tooltipArgs.addAll(Arrays.asList(args));
		return self();
	}
	
	/**
	 * Set the arguments that will be passed to the name translation key<br>
	 * As a special case, {@code Supplier}s passed
	 * will be invoked before being passed as arguments
	 */
	public Self nameArgs(Object... args) {
		for (Object arg : args) {
			if (arg instanceof Function)
				throw new IllegalArgumentException(
				  "Name args cannot be functions that depend on the value, since names aren't refreshed");
		}
		nameArgs.clear();
		nameArgs.addAll(Arrays.asList(args));
		return self();
	}
	
	/**
	 * Flag this entry as requiring a restart to be effective
	 */
	public Self restart() {
		return restart(true);
	}
	
	/**
	 * Flag this entry as requiring a restart to be effective
	 */
	public Self restart(boolean requireRestart) {
		this.requireRestart = requireRestart;
		return self();
	}
	
	protected Self withSaver(BiConsumer<Gui, ISimpleConfigEntryHolder> saver) {
		this.saver = saver;
		return self();
	}
	
	protected boolean debugTranslations() {
		return SimpleConfigMod.Config.advanced.translation_debug_mode;
	}
	
	@OnlyIn(Dist.CLIENT)
	protected ITextComponent getDisplayName() {
		if (debugTranslations())
			return getDebugDisplayName();
		if (translation != null && I18n.hasKey(translation))
			return new StringTextComponent(fillArgs(translation, null, nameArgs));
		return new StringTextComponent(name);
	}
	
	@OnlyIn(Dist.CLIENT)
	protected ITextComponent getDebugDisplayName() {
		if (translation != null) {
			IFormattableTextComponent status =
			  I18n.hasKey(translation) ? new StringTextComponent("✔ ") : new StringTextComponent("✘ ");
			if (tooltip != null) {
				status = status.append(
				  I18n.hasKey(tooltip)
				  ? new StringTextComponent("✔ ").mergeStyle(TextFormatting.DARK_AQUA)
				  : new StringTextComponent("_ ").mergeStyle(TextFormatting.DARK_AQUA));
			}
			TextFormatting format =
			  I18n.hasKey(translation)? TextFormatting.DARK_GREEN : TextFormatting.RED;
			return new StringTextComponent("").append(status.append(new StringTextComponent(translation)).mergeStyle(format));
		} else return new StringTextComponent("").append(new StringTextComponent("⚠ " + name).mergeStyle(TextFormatting.DARK_RED));
	}
	
	/**
	 * Transform value to Gui type
	 */
	protected Gui forGui(V value) {
		//noinspection unchecked
		return (Gui) value;
	}
	
	/**
	 * Transform value from Gui type
	 */
	protected @Nullable V fromGui(@Nullable Gui value) {
		//noinspection unchecked
		return (V) value;
	}
	
	/**
	 * Transform value from Gui type
	 */
	protected V fromGuiOrDefault(Gui value) {
		final V v = fromGui(value);
		return v != null ? v : this.value;
	}
	
	/**
	 * Transform value to Config type
	 */
	protected Config forConfig(V value) {
		//noinspection unchecked
		return (Config) value;
	}
	
	/**
	 * Transform value from Config type
	 */
	protected @Nullable V fromConfig(@Nullable Config value) {
		//noinspection unchecked
		return (V) value;
	}
	
	/**
	 * Transform value from Config type
	 */
	protected V fromConfigOrDefault(Config value) {
		final V v = self().fromConfig(value);
		return v != null ? v : this.value;
	}
	
	/**
	 * Subclasses may override to prevent nesting at build time
	 */
	protected boolean canBeNested() {
		return true;
	}
	
	protected void markDirty() {
		markDirty(true);
	}
	
	protected void markDirty(boolean dirty) {
		this.dirty = dirty;
		if (dirty) parent.markDirty();
	}
	
	protected Consumer<Gui> saveConsumer(ISimpleConfigEntryHolder c) {
		if (saver != null)
			return g -> saver.accept(g, c);
		final String n = name; // Use the current name
		return g -> {
			guiEntry = null; // Discard the entry
			// The save consumer shouldn't run with invalid values in the first place
			final V v = fromGuiOrDefault(g);
			if (!c.get(n).equals(v)) {
				markDirty();
				c.markDirty().set(n, v);
			}
		};
	}
	
	@OnlyIn(Dist.CLIENT)
	protected Optional<ITextComponent[]> supplyTooltip(Gui value) {
		if (debugTranslations())
			return supplyDebugTooltip(value);
		Optional<ITextComponent[]> o;
		if (guiTooltipSupplier != null) {
			o = guiTooltipSupplier.apply(value);
			if (o.isPresent()) return o;
		}
		final V v = fromGui(value);
		if (tooltipSupplier != null) {
			if (v != null) {
				o = tooltipSupplier.apply(v);
				if (o.isPresent()) return o;
			}
		}
		if (tooltip != null && I18n.hasKey(tooltip))
			return Optional.of(
			  Arrays.stream(fillArgs(tooltip, v, tooltipArgs).split("\n"))
			    .map(StringTextComponent::new).toArray(ITextComponent[]::new));
		return Optional.empty();
	}
	
	protected Optional<ITextComponent> supplyError(Gui value) {
		Optional<ITextComponent> o;
		if (guiErrorSupplier != null) {
			o = guiErrorSupplier.apply(value);
			if (o.isPresent()) return o;
		}
		if (errorSupplier != null) {
			final V v = fromGui(value);
			if (v != null) {
				o = errorSupplier.apply(v);
				if (o.isPresent()) return o;
			}
		}
		return Optional.empty();
	}
	
	protected ForgeConfigSpec.Builder decorate(ForgeConfigSpec.Builder builder) {
		if (comment != null)
			builder = builder.comment(comment);
		// This doesn't work
		// It seems that Config translations are not implemented in Forge's end
		// Also, mods I18n entries load after configs registration, which
		// makes impossible to set the comments to the tooltip translations
		if (tooltip != null)
			builder = builder.translation(tooltip);
		return builder;
	}
	
	@OnlyIn(Dist.CLIENT)
	protected <F extends FieldBuilder<?, ?>> F decorate(F builder) {
		try {
			builder.requireRestart(requireRestart);
		} catch (UnsupportedOperationException ignored) {}
		return builder;
	}
	
	protected Predicate<Object> configValidator() {
		return o -> {
			try {
				//noinspection unchecked
				Config c = (Config) o;
				return fromConfig(c) != null && !supplyError(forGui(fromConfig(c))).isPresent();
			} catch (ClassCastException e) {
				return false;
			}
		};
	}
	
	/**
	 * Build the associated config entry for this entry, if any
	 */
	protected Optional<ConfigValue<?>> buildConfigEntry(ForgeConfigSpec.Builder builder) {
		return Optional.empty();
	}
	
	/**
	 * Build the config for this entry<br>
	 * Subclasses should instead override {@link AbstractConfigEntry#buildConfigEntry(Builder)}
	 * in most cases
	 */
	protected void buildConfig(
	  ForgeConfigSpec.Builder builder, Map<String, ConfigValue<?>> specValues
	) {
		buildConfigEntry(builder).ifPresent(e -> specValues.put(name, e));
	}
	
	/**
	 * Generate an {@link AbstractConfigListEntry} to be added to the GUI, if any
	 * @param builder Entry builder
	 * @param config Config holder
	 */
	@OnlyIn(Dist.CLIENT)
	protected Optional<AbstractConfigListEntry<Gui>> buildGUIEntry(
	  ConfigEntryBuilder builder, ISimpleConfigEntryHolder config
	) {
		return Optional.empty();
	}
	
	/**
	 * Add entry to the GUI<br>
	 * Subclasses should instead override {@link AbstractConfigEntry#buildGUIEntry} in most cases
	 */
	@OnlyIn(Dist.CLIENT)
	@Override @Internal public void buildGUI(
	  ConfigCategory category, ConfigEntryBuilder entryBuilder, ISimpleConfigEntryHolder config
	) {
		buildGUIEntry(entryBuilder, config).ifPresent(e -> {
			this.guiEntry = e;
			category.addEntry(e);
		});
	}
	
	/**
	 * Get the value held by this entry
	 *
	 * @param spec Config spec to look into
	 * @throws ClassCastException if the found value type does not match the expected
	 */
	protected V get(ConfigValue<?> spec) {
		//noinspection unchecked
		return fromConfigOrDefault(((ConfigValue<Config>) spec).get());
	}
	
	/**
	 * Set the value held by this entry
	 *
	 * @param spec Config spec to update
	 * @throws ClassCastException if the found value type does not match the expected
	 */
	protected void set(ConfigValue<?> spec, V value) {
		//noinspection unchecked
		((ConfigValue<Config>) spec).set(forConfig(value));
	}
	
	protected Gui getGUI(ISimpleConfigEntryHolder c) {
		return guiEntry != null? guiEntry.getValue() : forGui(c.get(name));
	}
	
	protected void commitField(ISimpleConfigEntryHolder c) throws IllegalAccessException {
		if (backingField != null) {
			c.set(name, backingField.get(null));
		}
	}
	
	protected void bakeField(ISimpleConfigEntryHolder c) throws IllegalAccessException {
		if (backingField != null) {
			setBackingField(backingField, c.get(name));
		}
	}
	
	/**
	 * Overrides should call super
	 */
	@OnlyIn(Dist.CLIENT)
	protected void addTranslationsDebugInfo(List<ITextComponent> tooltip) {
		if (guiTooltipSupplier != null)
			tooltip.add(new StringTextComponent(" + Has GUI tooltip supplier").mergeStyle(TextFormatting.GRAY));
		if (tooltipSupplier != null)
			tooltip.add(new StringTextComponent(" + Has tooltip supplier").mergeStyle(TextFormatting.GRAY));
		if (guiErrorSupplier != null)
			tooltip.add(new StringTextComponent(" + Has GUI error supplier").mergeStyle(TextFormatting.GRAY));
		if (errorSupplier != null)
			tooltip.add(new StringTextComponent(" + Has error supplier").mergeStyle(TextFormatting.GRAY));
	}
	@OnlyIn(Dist.CLIENT)
	protected static void addTranslationsDebugSuffix(List<ITextComponent> tooltip) {
		tooltip.add(new StringTextComponent(" "));
		tooltip.add(new StringTextComponent(" ⚠ Simple Config translation debug mode active").mergeStyle(TextFormatting.GOLD));
	}
	@OnlyIn(Dist.CLIENT)
	protected Optional<ITextComponent[]> supplyDebugTooltip(Gui value) {
		List<ITextComponent> lines = new ArrayList<>();
		lines.add(new StringTextComponent("Translation key:").mergeStyle(TextFormatting.GRAY));
		if (translation != null) {
			final IFormattableTextComponent status =
			  I18n.hasKey(translation)
			  ? new StringTextComponent("(✔ present)").mergeStyle(TextFormatting.DARK_GREEN)
			  : new StringTextComponent("(✘ missing)").mergeStyle(TextFormatting.RED);
			lines.add(new StringTextComponent("   " + translation + " ")
			            .mergeStyle(TextFormatting.DARK_AQUA).append(status));
		} else lines.add(new StringTextComponent("   Error: couldn't map translation key").mergeStyle(TextFormatting.RED));
		lines.add(new StringTextComponent("Tooltip key:").mergeStyle(TextFormatting.GRAY));
		if (tooltip != null) {
			final IFormattableTextComponent status =
			  I18n.hasKey(tooltip)
			  ? new StringTextComponent("(✔ present)").mergeStyle(TextFormatting.DARK_GREEN)
			  : new StringTextComponent("(not present)").mergeStyle(TextFormatting.GOLD);
			lines.add(new StringTextComponent("   " + tooltip + " ")
			            .mergeStyle(TextFormatting.DARK_AQUA).append(status));
		} else lines.add(new StringTextComponent("   Error: couldn't map tooltip translation key").mergeStyle(TextFormatting.RED));
		addTranslationsDebugInfo(lines);
		addTranslationsDebugSuffix(lines);
		return Optional.of(lines.toArray(new ITextComponent[0]));
	}
}