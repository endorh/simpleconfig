package endorh.simpleconfig.api;

import endorh.simpleconfig.api.AbstractRange.DoubleRange;
import endorh.simpleconfig.api.AbstractRange.FloatRange;
import endorh.simpleconfig.api.AbstractRange.IntRange;
import endorh.simpleconfig.api.AbstractRange.LongRange;
import endorh.simpleconfig.api.ConfigBuilderFactory.PresetBuilder;
import endorh.simpleconfig.api.SimpleConfig.Type;
import endorh.simpleconfig.api.entry.*;
import endorh.simpleconfig.api.ui.hotkey.ExtendedKeyBind;
import endorh.simpleconfig.api.ui.hotkey.ExtendedKeyBindProvider;
import endorh.simpleconfig.api.ui.hotkey.KeyBindMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ClientRegistry;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ConfigBuilderFactoryProxy {
	private ConfigBuilderFactoryProxy() {}
	
	private static ConfigBuilderFactory factory = null;
	private static ConfigBuilderFactory getFactory() {
		if (factory != null) return factory;
		try {
			return factory = (ConfigBuilderFactory) Class.forName(
			  "endorh.simpleconfig.core.ConfigBuilderFactoryImpl"
			).getConstructor().newInstance();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(
			  "Missing SimpleConfig runtime. One of your mods depends on " +
			  "Simple Config, which is not present.", e);
		} catch (
		  NoSuchMethodException | IllegalAccessException
		  | InstantiationException | InvocationTargetException e
		) {
			throw new RuntimeException(
			  "Error loading SimpleConfig runtime. You may report this bug to the Simple Config issue tracker.", e);
		}
	}
	
	/**
	 * Create a {@link SimpleConfig} builder<br>
	 * Add entries with {@link SimpleConfigBuilder#add(String, ConfigEntryBuilder)}<br>
	 * Add categories and groups with {@link SimpleConfigBuilder#n(ConfigCategoryBuilder)}
	 * and {@link SimpleConfigBuilder#n(ConfigGroupBuilder)}<br>
	 * Complete the config by calling {@link SimpleConfigBuilder#buildAndRegister()}<br>
	 *
	 * @param modId Your mod id
	 * @param type A {@link Type}, usually either CLIENT or SERVER
	 */
	@NotNull public static SimpleConfigBuilder config(String modId, Type type) {
		return getFactory().builder(modId, type);
	}
	
	/**
	 * Create a {@link SimpleConfig} builder<br>
	 * Add entries with {@link SimpleConfigBuilder#add(String, ConfigEntryBuilder)}<br>
	 * Add categories and groups with {@link SimpleConfigBuilder#n(ConfigCategoryBuilder)}
	 * and {@link SimpleConfigBuilder#n(ConfigGroupBuilder)}<br>
	 * Complete the config by calling {@link SimpleConfigBuilder#buildAndRegister()}<br>
	 *
	 * @param modId Your mod id
	 * @param type A {@link Type}, usually either CLIENT or SERVER
	 * @param configClass Backing class for the config. It will be parsed
	 *   for static backing fields and config annotations
	 */
	@NotNull public static SimpleConfigBuilder config(String modId, Type type, Class<?> configClass) {
		return getFactory().builder(modId, type, configClass);
	}
	
	/**
	 * Create a config group
	 *
	 * @param name Group name, suitable for the config file (without spaces)
	 */
	@NotNull public static ConfigGroupBuilder group(String name) {
		return getFactory().group(name);
	}
	
	/**
	 * Create a config group
	 *
	 * @param name Group name, suitable for the config file (without spaces)
	 * @param expand Whether to expand this group in the GUI automatically (default: no)
	 */
	@NotNull public static ConfigGroupBuilder group(String name, boolean expand) {
		return getFactory().group(name, expand);
	}
	
	/**
	 * Create a config category
	 *
	 * @param name Category name, suitable for the config file (without spaces)
	 */
	@NotNull public static ConfigCategoryBuilder category(String name) {
		return getFactory().category(name);
	}
	
	/**
	 * Create a config category
	 *
	 * @param name Category name, suitable for the config file (without spaces)
	 * @param configClass Backing class for the category, which will be parsed
	 *   for static backing fields and config annotations
	 */
	@NotNull public static ConfigCategoryBuilder category(String name, Class<?> configClass) {
		return getFactory().category(name, configClass);
	}
	
	// Entries
	
	/**
	 * Boolean entry<br>
	 * You may change the text that appears in the button using
	 * {@link BooleanEntryBuilder#text}
	 */
	@NotNull public static BooleanEntryBuilder bool(boolean value) {
		return getFactory().bool(value);
	}
	
	/**
	 * Boolean entry<br>
	 * Uses the labels "Yes" and "No" instead of the usual "true" and "false"<br>
	 * You may also provide your own labels using {@link BooleanEntryBuilder#text}
	 */
	@NotNull public static BooleanEntryBuilder yesNo(boolean value) {
		return getFactory().yesNo(value);
	}
	
	/**
	 * Boolean entry<br>
	 * Uses the labels "Enabled" and "Disabled" instead of the usual "true" and "false"<br>
	 * You may also provide your own labels using {@link BooleanEntryBuilder#text}
	 */
	@NotNull public static BooleanEntryBuilder enable(boolean value) {
		return getFactory().enable(value);
	}
	
	/**
	 * Boolean entry<br>
	 * Uses the labels "ON" and "OFF" instead of the usual "true" and "false"<br>
	 * You may also provide your own labels using {@link BooleanEntryBuilder#text}
	 */
	@NotNull public static BooleanEntryBuilder onOff(boolean value) {
		return getFactory().onOff(value);
	}
	
	/**
	 * String entry
	 */
	@NotNull public static StringEntryBuilder string(String value) {
		return getFactory().string(value);
	}
	
	/**
	 * Enum entry
	 *
	 * @deprecated Use {@link #option} instead
	 */
	@NotNull public static @Deprecated <E extends Enum<E>> EnumEntryBuilder<E> enum_(E value) {
		return getFactory().enum_(value);
	}
	
	/**
	 * Enum entry
	 */
	@NotNull public static <E extends Enum<E>> EnumEntryBuilder<E> option(E value) {
		return getFactory().option(value);
	}
	
	/**
	 * Option entry, like an enum but for arbitrary types.<br>
	 * Supports variable allowed options, mainly intended to support
	 * environment dependent options (that don't change for a given
	 * installation).<br>
	 * The set of allowed values is only checked upon modifications,
	 * so it's not reliable to modify it during a game session.
	 * @see #option(Object, List)
	 */
	@NotNull public static <T> OptionEntryBuilder<T> option(T value, Supplier<List<T>> options) {
		return getFactory().option(value, options);
	}
	
	/**
	 * Option entry, like an enum but for arbitrary types.<br>
	 * Can also support variable allowed options.
	 * @see #option(Object, Supplier)
	 */
	@NotNull public static <T> OptionEntryBuilder<T> option(T value, List<T> options) {
		return getFactory().option(value, options);
	}
	
	/**
	 * Option entry, like an enum but for arbitrary types.<br>
	 * Can also support variable allowed options.
	 * @see #option(Object, Supplier)
	 */
	@SafeVarargs
	@NotNull public static <T> OptionEntryBuilder<T> option(T value, T... options) {
		return getFactory().option(value, options);
	}
	
	/**
	 * Button entry<br>
	 * Displays a button in the GUI that can trigger an arbitrary action.<br>
	 * The action may receive the immediate parent of the entry as parameter.
	 */
	@NotNull public static ButtonEntryBuilder button(Runnable action) {
		return getFactory().button(action);
	}
	
	/**
	 * Button entry<br>
	 * Displays a button in the GUI that can trigger an arbitrary action.<br>
	 * The action may receive the immediate parent of the entry as parameter.
	 */
	@NotNull public static ButtonEntryBuilder button(Consumer<ConfigEntryHolder> action) {
		return getFactory().button(action);
	}
	
	/**
	 * Add a button to another entry.<br>
	 * Not persistent. Useful for GUI screen interaction.
	 */
	@NotNull public static <V, Gui, B extends ConfigEntryBuilder<V, ?, Gui, B> & AtomicEntryBuilder>
	EntryButtonEntryBuilder<V, Gui, B> button(B inner, Consumer<V> action) {
		return getFactory().button(inner, action);
	}
	
	/**
	 * Add a button to another entry.<br>
	 * Not persistent. Useful for GUI screen interaction.
	 */
	@NotNull public static <V, Gui, B extends ConfigEntryBuilder<V, ?, Gui, B> & AtomicEntryBuilder>
	EntryButtonEntryBuilder<V, Gui, B> button(
	  B inner, BiConsumer<V, ConfigEntryHolder> action
	) {
		return getFactory().button(inner, action);
	}
	
	/**
	 * An entry that lets users apply different presets to the entries, using global paths.<br>
	 * Create presets using {@link #presets(PresetBuilder...)} and
	 * {@link #preset(String)}
	 * or just create a map your way.
	 */
	@NotNull public static PresetSwitcherEntryBuilder globalPresetSwitcher(
	  Map<String, Map<String, Object>> presets, String path
	) {
		return getFactory().globalPresetSwitcher(presets, path);
	}
	
	/**
	 * An entry that lets users apply different presets to the entries,
	 * using local paths from the parent of this entry.<br>
	 * Create presets using {@link #presets(PresetBuilder...)} and
	 * {@link #preset(String)}
	 * or just create a map your way.
	 */
	@NotNull public static PresetSwitcherEntryBuilder localPresetSwitcher(
	  Map<String, Map<String, Object>> presets, String path
	) {
		return getFactory().localPresetSwitcher(presets, path);
	}
	
	/**
	 * Create a preset map from a collection of preset builders
	 */
	@NotNull public static Map<String, Map<String, Object>> presets(PresetBuilder... presets) {
		return getFactory().presets(presets);
	}
	
	/**
	 * Preset map builder
	 */
	@NotNull public static PresetBuilder preset(String name) {
		return getFactory().preset(name);
	}
	
	/**
	 * Unbound byte value
	 *
	 * @deprecated Use a bound int entry
	 */
	@NotNull public static @Deprecated ByteEntryBuilder number(byte value) {
		return getFactory().number(value);
	}
	
	/**
	 * Non-negative byte between 0 and {@code max} (inclusive)
	 *
	 * @deprecated Use a bound int entry
	 */
	@NotNull public static @Deprecated ByteEntryBuilder number(byte value, byte max) {
		return getFactory().number(value, max);
	}
	
	/**
	 * Byte value between {@code min} and {@code max} (inclusive)
	 *
	 * @deprecated Use a bound int entry
	 */
	@NotNull public static @Deprecated ByteEntryBuilder number(byte value, byte min, byte max) {
		return getFactory().number(value, min, max);
	}
	
	/**
	 * Unbound short value
	 *
	 * @deprecated Use a bound int entry
	 */
	@NotNull public static @Deprecated ShortEntryBuilder number(short value) {
		return getFactory().number(value);
	}
	
	/**
	 * Non-negative short between 0 and {@code max} (inclusive)
	 *
	 * @deprecated Use a bound int entry
	 */
	@NotNull public static @Deprecated ShortEntryBuilder number(short value, short max) {
		return getFactory().number(value, max);
	}
	
	/**
	 * Short value between {@code min} and {@code max} (inclusive)
	 *
	 * @deprecated Use a bound int entry
	 */
	@NotNull public static @Deprecated ShortEntryBuilder number(short value, short min, short max) {
		return getFactory().number(value, min, max);
	}
	
	/**
	 * Unbound integer value
	 */
	@NotNull public static IntegerEntryBuilder number(int value) {
		return getFactory().number(value); 
	}
	
	/**
	 * Non-negative integer between 0 and {@code max} (inclusive)
	 */
	@NotNull public static IntegerEntryBuilder number(int value, int max) {
		return getFactory().number(value, max); 
	}
	
	/**
	 * Integer value between {@code min} and {@code max} (inclusive)
	 */
	@NotNull public static IntegerEntryBuilder number(int value, int min, int max) {
		return getFactory().number(value, min, max); 
	}
	
	/**
	 * Integer percentage, between 0 and 100 (inclusive)<br>
	 * Displayed as a slider
	 */
	@NotNull public static IntegerEntryBuilder percent(int value) {
		return getFactory().percent(value); 
	}
	
	/**
	 * Unbound long value
	 */
	@NotNull public static LongEntryBuilder number(long value) {
		return getFactory().number(value); 
	}
	
	/**
	 * Non-negative long between 0 and {@code max} (inclusive)
	 */
	@NotNull public static LongEntryBuilder number(long value, long max) {
		return getFactory().number(value, max); 
	}
	
	/**
	 * Long value between {@code min} and {@code max} (inclusive)
	 */
	@NotNull public static LongEntryBuilder number(long value, long min, long max) {
		return getFactory().number(value, min, max); 
	}
	
	/**
	 * Unbound float value
	 */
	@NotNull public static FloatEntryBuilder number(float value) {
		return getFactory().number(value); 
	}
	
	/**
	 * Non-negative float value between 0 and {@code max} (inclusive)
	 */
	@NotNull public static FloatEntryBuilder number(float value, float max) {
		return getFactory().number(value, max); 
	}
	
	/**
	 * Float value between {@code min} and {@code max} inclusive
	 */
	@NotNull public static FloatEntryBuilder number(float value, float min, float max) {
		return getFactory().number(value, min, max); 
	}
	
	/**
	 * Float percentage, between 0 and 100, but stored as a fraction
	 * between 0.0 and 1.0 in the backing field (not the config file).<br>
	 * Displayed as a slider
	 */
	@NotNull public static FloatEntryBuilder percent(float value) {
		return getFactory().percent(value); 
	}
	
	/**
	 * Unbound double value
	 */
	@NotNull public static DoubleEntryBuilder number(double value) {
		return getFactory().number(value); 
	}
	
	/**
	 * Non-negative double value between 0 and {@code max} (inclusive)
	 */
	@NotNull public static DoubleEntryBuilder number(double value, double max) {
		return getFactory().number(value, max); 
	}
	
	/**
	 * Double value between {@code min} and {@code max} inclusive
	 */
	@NotNull public static DoubleEntryBuilder number(double value, double min, double max) {
		return getFactory().number(value, min, max); 
	}
	
	/**
	 * Double percentage, between 0 and 100, but stored as a fraction
	 * between 0.0 and 1.0 in the backing field (not the config file).<br>
	 * Displayed as a slider
	 */
	@NotNull public static DoubleEntryBuilder percent(double value) {
		return getFactory().percent(value); 
	}
	
	/**
	 * Float value between 0 and 1 (inclusive)
	 */
	@NotNull public static FloatEntryBuilder fraction(float value) {
		return getFactory().fraction(value); 
	}
	
	/**
	 * Double value between 0 and 1 (inclusive)
	 */
	@NotNull public static DoubleEntryBuilder fraction(double value) {
		return getFactory().fraction(value); 
	}
	
	/**
	 * Float entry between 0 and 1 (inclusive)<br>
	 * Displays a volume label in the slider instead of the usual "Value: %s"
	 */
	@NotNull public static FloatEntryBuilder volume(float value) {
		return getFactory().volume(value); 
	}
	
	/**
	 * Float entry between 0 and 1 (inclusive) with default value of 1.<br>
	 * Displays a volume label in the slider instead of the usual "Value: %s"
	 */
	@NotNull public static FloatEntryBuilder volume() {
		return getFactory().volume(); 
	}
	
	/**
	 * Double entry between 0 and 1 (inclusive)<br>
	 * Displays a volume label in the slider instead of the usual "Value: %s"
	 */
	@NotNull public static DoubleEntryBuilder volume(double value) {
		return getFactory().volume(value); 
	}
	
	/**
	 * Double range entry, which defines a min and max values, optionally exclusive.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see DoubleRange
	 */
	@NotNull public static DoubleRangeEntryBuilder range(DoubleRange range) {
		return getFactory().range(range); 
	}
	
	/**
	 * Double range entry, which defines a min and max values, inclusive by default.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see DoubleRange
	 */
	@NotNull public static DoubleRangeEntryBuilder range(double min, double max) {
		return getFactory().range(min, max); 
	}
	
	/**
	 * Float range entry, which defines a min and max values, optionally exclusive.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see FloatRange
	 */
	@NotNull public static FloatRangeEntryBuilder range(FloatRange range) {
		return getFactory().range(range); 
	}
	
	/**
	 * Float range entry, which defines a min and max values, inclusive by default.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see FloatRange
	 */
	@NotNull public static FloatRangeEntryBuilder range(float min, float max) {
		return getFactory().range(min, max); 
	}
	
	/**
	 * Long range entry, which defines a min and max values, optionally exclusive.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see LongRange
	 */
	@NotNull public static LongRangeEntryBuilder range(LongRange range) {
		return getFactory().range(range); 
	}
	
	/**
	 * Long range entry, which defines a min and max values, inclusive by default.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see LongRange
	 */
	@NotNull public static LongRangeEntryBuilder range(long min, long max) {
		return getFactory().range(min, max); 
	}
	
	/**
	 * Integer range entry, which defines a min and max values, optionally exclusive.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see IntRange
	 */
	@NotNull public static IntegerRangeEntryBuilder range(IntRange range) {
		return getFactory().range(range); 
	}
	
	/**
	 * Integer range entry, which defines a min and max values, inclusive by default.<br>
	 * You may allow users to change the exclusiveness of the bounds.
	 *
	 * @see IntRange
	 */
	@NotNull public static IntegerRangeEntryBuilder range(int min, int max) {
		return getFactory().range(min, max); 
	}
	
	/**
	 * Color entry<br>
	 * Use {@link ColorEntryBuilder#alpha()} to allow alpha values
	 */
	@NotNull public static ColorEntryBuilder color(Color value) {
		return getFactory().color(value); 
	}
	
	/**
	 * Regex Pattern entry<br>
	 * Will use the flags of the passed regex to compile user input<br>
	 */
	@NotNull public static PatternEntryBuilder pattern(Pattern pattern) {
		return getFactory().pattern(pattern); 
	}
	
	/**
	 * Regex pattern entry with default flags
	 */
	@NotNull public static PatternEntryBuilder pattern(String pattern) {
		return getFactory().pattern(pattern); 
	}
	
	/**
	 * Regex pattern
	 */
	@NotNull public static PatternEntryBuilder pattern(String pattern, int flags) {
		return getFactory().pattern(pattern, flags); 
	}
	
	/**
	 * Entry of a String serializable object
	 */
	@NotNull public static <V> SerializableEntryBuilder<V, ?> entry(
	  V value, Function<V, String> serializer, Function<String, Optional<V>> deserializer
	) {
		return getFactory().entry(value, serializer, deserializer); 
	}
	
	/**
	 * Entry of a String serializable object
	 */
	@NotNull public static <V> ISerializableEntryBuilder<V> entry(V value, IConfigEntrySerializer<V> serializer) {
		return getFactory().entry(value, serializer); 
	}
	
	/**
	 * Entry of a String serializable object
	 */
	@NotNull public static <V extends ISerializableConfigEntry<V>> ISerializableEntryBuilder<V> entry(V value) {
		return getFactory().entry(value); 
	}
	
	/**
	 * Java Bean entry.<br>
	 * Use the builder's methods to define entries to edit the bean's properties.<br>
	 * Most useful for bean lists or maps, rather than as singleton entries.
	 * @param <B> Class of the bean. Must conform to the {@code JavaBean} specification.
	 */
	@NotNull public static <B> BeanEntryBuilder<B> bean(B value) {
		return getFactory().bean(value);
	}
	
	// Minecraft specific types
	
	/**
	 * NBT entry that accepts any kind of NBT, either values or compounds
	 */
	@NotNull public static TagEntryBuilder tag(Tag value) {
		return getFactory().tag(value);
	}
	
	/**
	 * NBT entry that accepts NBT compounds
	 */
	@NotNull public static CompoundTagEntryBuilder compoundTag(CompoundTag value) {
		return getFactory().compoundTag(value);
	}
	
	/**
	 * Generic resource location entry
	 */
	@NotNull public static ResourceLocationEntryBuilder resource(String resourceName) {
		return getFactory().resource(resourceName); 
	}
	
	/**
	 * Generic resource location entry
	 */
	@NotNull public static ResourceLocationEntryBuilder resource(ResourceLocation value) {
		return getFactory().resource(value); 
	}
	
	/**
	 * Key binding entry. Supports advanced key combinations, and other advanced
	 * settings such as exclusivity, order sensitivity, activation on release/repeat/toggle.<br>
	 * Register extended keybinds by registering an {@link ExtendedKeyBindProvider} for them
	 * using {@link ExtendedKeyBindProvider#registerProvider(ExtendedKeyBindProvider)}<br><br>
	 * <b>Consider registering regular {@link KeyMapping}s through
	 * {@link ClientRegistry#registerKeyBinding(KeyMapping)}
	 * </b><br>
	 */
	@NotNull public static @OnlyIn(Dist.CLIENT) KeyBindEntryBuilder key(ExtendedKeyBind keyBind) {
		return getFactory().key(keyBind);
	}
	
	/**
	 * Key binding entry. Supports advanced key combinations, and other advanced
	 * settings such as exclusivity, order sensitivity, activation on release/repeat/toggle.<br>
	 * <b>If you're using this entry as a static keybind for your mod, prefer using
	 * {@link #key(ExtendedKeyBind)}, as it'll provide better overlap detection.</b><br>
	 * Register extended keybinds by registering an {@link ExtendedKeyBindProvider} for them
	 * using {@link ExtendedKeyBindProvider#registerProvider(ExtendedKeyBindProvider)}<br><br>
	 * <b>Consider registering regular {@link KeyMapping}s through
	 * {@link ClientRegistry#registerKeyBinding(KeyMapping)}}
	 * </b><br>
	 */
	@NotNull public static @OnlyIn(Dist.CLIENT) KeyBindEntryBuilder key(KeyBindMapping key) {
		return getFactory().key(key); 
	}
	
	/**
	 * Key binding entry. Supports advanced key combinations, and other advanced
	 * settings such as exclusivity, order sensitivity, activation on release/repeat/toggle.<br>
	 * <b>If you're using this entry as a static keybind for your mod, prefer using
	 * {@link #key(ExtendedKeyBind)}, as it'll provide better overlap detection.</b><br>
	 * Register extended keybinds by registering an {@link ExtendedKeyBindProvider} for them
	 * using {@link ExtendedKeyBindProvider#registerProvider(ExtendedKeyBindProvider)}<br><br>
	 * <b>Consider registering regular {@link KeyMapping}s through
	 * {@link ClientRegistry#registerKeyBinding(KeyMapping)}}
	 * </b><br>
	 */
	@NotNull public static @OnlyIn(Dist.CLIENT) KeyBindEntryBuilder key(String key) {
		return getFactory().key(key); 
	}
	
	/**
	 * Key binding entry. Supports advanced key combinations, and other advanced
	 * settings such as exclusivity, order sensitivity, activation on release/repeat/toggle.<br>
	 * <b>If you're using this entry as a static keybind for your mod, prefer using
	 * {@link #key(ExtendedKeyBind)}, as it'll provide better overlap detection.</b><br>
	 * Register extended keybinds by registering an {@link ExtendedKeyBindProvider} for them
	 * using {@link ExtendedKeyBindProvider#registerProvider(ExtendedKeyBindProvider)}<br><br>
	 * <b>Consider registering regular {@link KeyMapping}s through
	 * {@link ClientRegistry#registerKeyBinding(KeyMapping)}}
	 * </b><br>
	 */
	@NotNull public static @OnlyIn(Dist.CLIENT) KeyBindEntryBuilder key() {
		return getFactory().key(); 
	}
	
	/**
	 * Item entry<br>
	 * Use {@link #itemName} instead to use ResourceLocations as value,
	 * to allow unknown items.
	 */
	@NotNull public static ItemEntryBuilder item(@Nullable Item value) {
		return getFactory().item(value); 
	}
	
	/**
	 * Item name entry<br>
	 * Use {@link #item} instead to use Item objects as value.
	 */
	@NotNull public static ItemNameEntryBuilder itemName(@Nullable ResourceLocation value) {
		return getFactory().itemName(value); 
	}
	
	/**
	 * Item name entry<br>
	 * Use {@link #item} instead to use Item objects as value.
	 */
	@NotNull public static ItemNameEntryBuilder itemName(Item value) {
		return getFactory().itemName(value); 
	}
	
	/**
	 * Block entry<br>
	 * Use {@link #blockName} instead to use ResourceLocations as value,
	 * to allow unknown blocks.
	 */
	@NotNull public static BlockEntryBuilder block(@Nullable Block value) {
		return getFactory().block(value); 
	}
	
	/**
	 * Block name entry<br>
	 * Use {@link #block} instead to use Block objects as value.
	 */
	@NotNull public static BlockNameEntryBuilder blockName(@Nullable ResourceLocation value) {
		return getFactory().blockName(value); 
	}
	
	/**
	 * Block name entry<br>
	 * Use {@link #block} instead to use Block objects as value.
	 */
	@NotNull public static BlockNameEntryBuilder blockName(Block value) {
		return getFactory().blockName(value); 
	}
	
	/**
	 * Fluid entry<br>
	 * Use {@link #fluidName} instead to use ResourceLocations as value,
	 * to allow unknown fluids.
	 */
	@NotNull public static FluidEntryBuilder fluid(@Nullable Fluid value) {
		return getFactory().fluid(value); 
	}
	
	/**
	 * Fluid name entry<br>
	 * Use {@link #fluid} instead to use Fluid objects as value.
	 */
	@NotNull public static FluidNameEntryBuilder fluidName(@Nullable ResourceLocation value) {
		return getFactory().fluidName(value); 
	}
	
	/**
	 * Fluid name entry<br>
	 * Use {@link #fluid} instead to use Fluid objects as value.
	 */
	@NotNull public static FluidNameEntryBuilder fluidName(Fluid value) {
		return getFactory().fluidName(value); 
	}
	
	// Specific lists (for `String`s and `Number` types)
	
	/**
	 * String list<br>
	 * You should use instead general {@link #list}s with a {@link #string} entry,
	 * which provides better control over allowed values.
	 */
	public static @NotNull StringListEntryBuilder stringList(List<String> value) {
		return getFactory().stringList(value); 
	}
	
	/**
	 * Byte list<br>
	 * You should use instead general {@link #list}s with a byte/int {@link #number} entry,
	 * which provides better control over allowed values.
	 * @deprecated Use bound {@link #intList}s or general {@link #list}s
	 */
	@Deprecated public static @NotNull ByteListEntryBuilder byteList(List<Byte> value) {
		return getFactory().byteList(value); 
	}
	
	/**
	 * Short list<br>
	 * You should use instead general {@link #list}s with a short/int {@link #number} entry,
	 * which provides better control over allowed values.
	 * @deprecated Use bound {@link #intList}s, or general {@link #list}s
	 */
	@Deprecated public static @NotNull ShortListEntryBuilder shortList(List<Short> value) {
		return getFactory().shortList(value); 
	}
	
	/**
	 * Integer list<br>
	 * You should use instead general {@link #list}s with an int {@link #number} entry,
	 * which provides better control over allowed values.
	 */
	public static @NotNull IntegerListEntryBuilder intList(List<Integer> value) {
		return getFactory().intList(value); 
	}
	
	/**
	 * Long list<br>
	 * You should use instead general {@link #list}s with a long {@link #number} entry,
	 * which provides better control over allowed values.
	 */
	public static @NotNull LongListEntryBuilder longList(List<Long> value) {
		return getFactory().longList(value);
	}
	
	/**
	 * Float list<br>
	 * You should use instead general {@link #list}s with a float {@link #number} entry,
	 * which provides better control over allowed values.
	 */
	public static @NotNull FloatListEntryBuilder floatList(List<Float> value) {
		return getFactory().floatList(value); 
	}
	
	/**
	 * Double list<br>
	 * You should use instead general {@link #list}s with a double {@link #number} entry,
	 * which provides better control over allowed values.
	 */
	public static @NotNull DoubleListEntryBuilder doubleList(List<Double> value) {
		return getFactory().doubleList(value); 
	}
	
	// General lists
	
	/**
	 * List of other entries. Defaults to an empty list<br>
	 * The nested entry may be other list entry, or even a map entry.<br>
	 * Non-persistent entries cannot be nested
	 */
	public static <V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>>
	@NotNull EntryListEntryBuilder<@NotNull V, C, G, Builder> list(Builder entry) {
		return getFactory().list(entry); 
	}
	
	/**
	 * List of other entries<br>
	 * The nested entry may be other list entry, or even a map entry.<br>
	 * Non-persistent entries cannot be nested
	 */
	public static <V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>>
	@NotNull EntryListEntryBuilder<@NotNull V, C, G, Builder> list(Builder entry, List<V> value) {
		return getFactory().list(entry, value); 
	}
	
	/**
	 * List of other entries<br>
	 * The nested entry may be other list entry, or even a map entry.<br>
	 * Non-persistent entries cannot be nested
	 */
	public static @SuppressWarnings("unchecked") <
	  V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>
	> @NotNull EntryListEntryBuilder<@NotNull V, C, G, Builder> list(Builder entry, V... values) {
		return getFactory().list(entry, values); 
	}
	
	// General sets
	
	/**
	 * Set of other entries. Defaults to an empty set<br>
	 * The nested entry may be other set/list entry, or even a map entry.<br>
	 * Non-persistent entries cannot be nested
	 */
	public static <V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>>
	@NotNull EntrySetEntryBuilder<@NotNull V, C, G, Builder> set(Builder entry) {
		return getFactory().set(entry);
	}
	
	/**
	 * Set of other entries<br>
	 * The nested entry may be other set/list entry, or even a map entry.<br>
	 * Non-persistent entries cannot be nested
	 */
	public static <V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>>
	@NotNull EntrySetEntryBuilder<@NotNull V, C, G, Builder> set(Builder entry, Set<V> value) {
		return getFactory().set(entry, value);
	}
	
	/**
	 * Set of other entries<br>
	 * The nested entry may be other set/list entry, or even a map entry.<br>
	 * Non-persistent entries cannot be nested
	 */
	public static @SuppressWarnings("unchecked") <
	  V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>
	> @NotNull EntrySetEntryBuilder<@NotNull V, C, G, Builder> set(Builder entry, V... values) {
		return getFactory().set(entry, values);
	}
	
	// General maps
	
	/**
	 * Map of other entries<br>
	 * The keys themselves are other entries, as long as they can
	 * serialize as strings. Non string-serializable key entries
	 * won't compile<br>
	 * By default the key is a string entry and the value is an empty map
	 *
	 * @param keyEntry The entry to be used as key
	 * @param entry The entry to be used as value, which may be another map
	 *   entry, or a list entry. Not persistent entries cannot be used
	 */
	public static <K, V, KC, C, KG, G,
	  Builder extends ConfigEntryBuilder<V, C, G, Builder>,
	  KeyBuilder extends ConfigEntryBuilder<K, KC, KG, KeyBuilder> & AtomicEntryBuilder
	> @NotNull EntryMapEntryBuilder<@NotNull K, @NotNull V, KC, C, KG, G, Builder, KeyBuilder> map(
	  KeyBuilder keyEntry, Builder entry
	) {
		return getFactory().map(keyEntry, entry); 
	}
	
	/**
	 * Map of other entries<br>
	 * The keys themselves are other entries, as long as they can
	 * serialize as strings. Non string-serializable key entries
	 * won't compile<br>
	 * By default the key is a string entry and the value is an empty map
	 *
	 * @param keyEntry The entry to be used as key
	 * @param entry The entry to be used as value, which may be another map
	 *   entry, or a list entry. Not persistent entries cannot be used
	 * @param value Entry value
	 */
	public static <K, V, KC, C, KG, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>,
	  KeyBuilder extends ConfigEntryBuilder<K, KC, KG, KeyBuilder> & AtomicEntryBuilder
	> @NotNull EntryMapEntryBuilder<@NotNull K, @NotNull V, KC, C, KG, G, Builder, KeyBuilder> map(
	  KeyBuilder keyEntry, Builder entry, Map<K, V> value
	) {
		return getFactory().map(keyEntry, entry, value); 
	}
	
	/**
	 * Map of other entries<br>
	 * The keys themselves are other entries, as long as they can
	 * serialize as strings. Non string-serializable key entries
	 * won't compile<br>
	 * By default the key is a string entry and the value is an empty map
	 *
	 * @param entry The entry to be used as value, which may be other
	 *   map entry, or a list entry. Not persistent entries cannot be used
	 */
	public static <V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>>
	@NotNull EntryMapEntryBuilder<
	  @NotNull String, @NotNull V, String, C, String, G, Builder, StringEntryBuilder
	> map(Builder entry) {
		return getFactory().map(entry); 
	}
	
	/**
	 * Map of other entries<br>
	 * The keys themselves are other entries, as long as they can
	 * serialize as strings. Non string-serializable key entries
	 * won't compile<br>
	 * By default the key is a string entry and the value is an empty map
	 *
	 * @param entry The entry to be used as value, which may be other
	 *   map entry, or a list entry. Not persistent entries cannot be used
	 * @param value Entry value (default: empty map)
	 */
	public static <V, C, G, Builder extends ConfigEntryBuilder<V, C, G, Builder>>
	@NotNull EntryMapEntryBuilder<
	  @NotNull String, @NotNull V, String, C, String, G, Builder, StringEntryBuilder
	> map(
	  Builder entry, Map<String, V> value
	) {
		return getFactory().map(entry, value); 
	}
	
	// Lists of pairs (non-unique maps)
	
	/**
	 * List of pairs of other entries, like a linked map, but with duplicates<br>
	 * The keys themselves are other entries, as long as they can
	 * serialize as strings. Non string-serializable key entries
	 * won't compile<br>
	 * By default the value is an empty list
	 *
	 * @param keyEntry The entry to be used as key
	 * @param entry The entry to be used as value, which may be another map
	 *   entry, or a list entry. Not persistent entries cannot be used
	 */
	public static <K, V, KC, C, KG, G,
	  Builder extends ConfigEntryBuilder<V, C, G, Builder>,
	  KeyBuilder extends ConfigEntryBuilder<K, KC, KG, KeyBuilder> & AtomicEntryBuilder
	> @NotNull EntryPairListEntryBuilder<
	  @NotNull K, @NotNull V, KC, C, KG, G, Builder, KeyBuilder
	> pairList(KeyBuilder keyEntry, Builder entry) {
		return getFactory().pairList(keyEntry, entry); 
	}
	
	/**
	 * List of pairs of other entries, like a linked map, but with duplicates<br>
	 * The keys themselves are other entries, as long as they can
	 * serialize as strings. Non string-serializable key entries
	 * won't compile<br>
	 * By default the value is an empty list
	 *
	 * @param keyEntry The entry to be used as key
	 * @param entry The entry to be used as value, which may be another map
	 *   entry, or a list entry. Not persistent entries cannot be used
	 * @param value Entry value
	 */
	public static <K, V, KC, C, KG, G,
	  Builder extends ConfigEntryBuilder<V, C, G, Builder>,
	  KeyBuilder extends ConfigEntryBuilder<K, KC, KG, KeyBuilder> & AtomicEntryBuilder
	> @NotNull EntryPairListEntryBuilder<
	  @NotNull K, @NotNull V, KC, C, KG, G, Builder, KeyBuilder
	> pairList(
	  KeyBuilder keyEntry, Builder entry, List<Pair<K, V>> value
	) {
		return getFactory().pairList(keyEntry, entry, value); 
	}
	
	// Collection captions
	
	/**
	 * Attach an entry as the caption of a list entry<br>
	 * Changes the value to a {@link Pair} of the caption's value and the list's value
	 */
	public static <V, C, G, B extends ListEntryBuilder<V, C, G, B>,
	  CV, CC, CG, CB extends ConfigEntryBuilder<CV, CC, CG, CB> & AtomicEntryBuilder
	> @NotNull CaptionedCollectionEntryBuilder<
	  @NotNull List< @NotNull V>, List<C>, G, B, CV, CC, CG, CB
	> caption(CB caption, B list) {
		return getFactory().caption(caption, list);
	}
	
	/**
	 * Attach an entry as the caption of a set entry<br>
	 * Changes the value to a {@link Pair} of the caption's value and the set's value
	 */
	public static <V, C, G, B extends ConfigEntryBuilder<V, C, G, B>,
	  CV, CC, CG, CB extends ConfigEntryBuilder<CV, CC, CG, CB> & AtomicEntryBuilder
	> @NotNull CaptionedCollectionEntryBuilder<
	  @NotNull Set<@NotNull V>, Set<C>, G, EntrySetEntryBuilder<V, C, G, B>, CV, CC, CG, CB
	> caption(CB caption, EntrySetEntryBuilder<V, C, G, B> set) {
		return getFactory().caption(caption, set);
	}
	
	/**
	 * Attach an entry as the caption of a map entry<br>
	 * Changes the value to a {@link Pair} of the caption's value and the map's value
	 */
	public static <K, V, KC, C, KG, G,
	  KB extends ConfigEntryBuilder<K, KC, KG, KB> & AtomicEntryBuilder,
	  B extends ConfigEntryBuilder<V, C, G, B>,
	  CV, CC, CG, CB extends ConfigEntryBuilder<CV, CC, CG, CB> & AtomicEntryBuilder
	> @NotNull CaptionedCollectionEntryBuilder<
	  @NotNull Map<@NotNull K, @NotNull V>, Map<KC, C>, Pair<KG, G>,
	  EntryMapEntryBuilder<K, V, KC, C, KG, G, B, KB>,
	  CV, CC, CG, CB
	> caption(CB caption, EntryMapEntryBuilder<K, V, KC, C, KG, G, B, KB> map) {
		return getFactory().caption(caption, map);
	}
	
	/**
	 * Attach an entry as the caption of a pairList entry<br>
	 * Changes the value to a {@link Pair} of the caption's value and the pairList's value
	 */
	public static <K, V, KC, C, KG, G,
	  KB extends ConfigEntryBuilder<K, KC, KG, KB> & AtomicEntryBuilder,
	  B extends ConfigEntryBuilder<V, C, G, B>,
	  CV, CC, CG, CB extends ConfigEntryBuilder<CV, CC, CG, CB> & AtomicEntryBuilder
	> @NotNull CaptionedCollectionEntryBuilder<
	  @NotNull List<@NotNull Pair<@NotNull K, @NotNull V>>, List<Pair<KC, C>>, Pair<KG, G>,
	  EntryPairListEntryBuilder<K, V, KC, C, KG, G, B, KB>,
	  CV, CC, CG, CB
	> caption(CB caption, EntryPairListEntryBuilder<K, V, KC, C, KG, G, B, KB> pairList) {
		return getFactory().caption(caption, pairList);
	}
	
	// General pairs
	
	/**
	 * Pair of other two entries.<br>
	 * Keys can be any valid key entry for maps.<br>
	 * Non string-serializable entries won't compile.<br>
	 * Like maps, currently serializes to NBT in the config file.<br>
	 * The value can be explicitly specified, or inferred from the entries.<br>
	 *
	 * @param leftEntry The entry for the left
	 * @param rightEntry The entry for the right
	 */
	@NotNull public static <L, R, LC, RC, LG, RG,
	  LB extends ConfigEntryBuilder<L, LC, LG, LB> & AtomicEntryBuilder,
	  RB extends ConfigEntryBuilder<R, RC, RG, RB> & AtomicEntryBuilder
	> EntryPairEntryBuilder<@NotNull L, @NotNull R, LC, RC, LG, RG> pair(
	  LB leftEntry, RB rightEntry
	) {
		return getFactory().pair(leftEntry, rightEntry); 
	}
	
	/**
	 * Pair of other two entries.<br>
	 * Keys can be any valid key entry for maps.<br>
	 * Non string-serializable entries won't compile.<br>
	 * Like maps, currently serializes to NBT in the config file.<br>
	 *
	 * @param leftEntry The entry for the left
	 * @param rightEntry The entry for the right
	 * @param value Entry value (if omitted, it's inferred from the values of the entries)
	 */
	@NotNull public static <L, R, LC, RC, LG, RG,
	  LB extends ConfigEntryBuilder<L, LC, LG, LB> & AtomicEntryBuilder,
	  RB extends ConfigEntryBuilder<R, RC, RG, RB> & AtomicEntryBuilder
	> EntryPairEntryBuilder<@NotNull L, @NotNull R, LC, RC, LG, RG> pair(
	  LB leftEntry, RB rightEntry, Pair<L, R> value
	) {
		return getFactory().pair(leftEntry, rightEntry, value); 
	}
	
	// General triples
	
	/**
	 * Triple of other three entries.<br>
	 * Keys can be any valid key entry for maps.<br>
	 * Non string-serializable entries won't compile.<br>
	 * Like maps, currently serializes to NBT in the config file.<br>
	 * The value can be explicitly specified, or inferred from the entries.<br>
	 *
	 * @param leftEntry The entry for the left
	 * @param middleEntry The entry for the middle
	 * @param rightEntry The entry for the right
	 */
	@NotNull public static <L, M, R, LC, MC, RC, LG, MG, RG,
	  LB extends ConfigEntryBuilder<L, LC, LG, LB> & AtomicEntryBuilder,
	  MB extends ConfigEntryBuilder<M, MC, MG, MB> & AtomicEntryBuilder,
	  RB extends ConfigEntryBuilder<R, RC, RG, RB> & AtomicEntryBuilder
	> EntryTripleEntryBuilder<
	  @NotNull L, @NotNull M, @NotNull R, LC, MC, RC, LG, MG, RG
	> triple(LB leftEntry, MB middleEntry, RB rightEntry) {
		return getFactory().triple(leftEntry, middleEntry, rightEntry);
	}
	
	/**
	 * Triple of other three entries.<br>
	 * Keys can be any valid key entry for maps.<br>
	 * Non string-serializable entries won't compile.<br>
	 * Like maps, currently serializes to NBT in the config file.<br>
	 *
	 * @param leftEntry The entry for the left
	 * @param middleEntry The entry for the middle
	 * @param rightEntry The entry for the right
	 * @param value Entry value (if omitted, it's inferred from the values of the entries)
	 */
	@NotNull public static <L, M, R, LC, MC, RC, LG, MG, RG,
	  LB extends ConfigEntryBuilder<L, LC, LG, LB> & AtomicEntryBuilder,
	  MB extends ConfigEntryBuilder<M, MC, MG, MB> & AtomicEntryBuilder,
	  RB extends ConfigEntryBuilder<R, RC, RG, RB> & AtomicEntryBuilder
	> EntryTripleEntryBuilder<
	  @NotNull L, @NotNull M, @NotNull R, LC, MC, RC, LG, MG, RG
	> triple(LB leftEntry, MB middleEntry, RB rightEntry, Triple<L, M, R> value) {
		return getFactory().triple(leftEntry, middleEntry, rightEntry, value);
	}
}
