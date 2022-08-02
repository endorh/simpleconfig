package endorh.simpleconfig.core.entry;

import endorh.simpleconfig.core.AbstractConfigEntry;
import endorh.simpleconfig.core.AbstractConfigEntryBuilder;
import endorh.simpleconfig.core.IErrorEntry;
import endorh.simpleconfig.core.ISimpleConfigEntryHolder;
import endorh.simpleconfig.ui.impl.builders.ListFieldBuilder;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class AbstractListEntry
  <V, Config, Gui, Self extends AbstractListEntry<V, Config, Gui, Self>>
  extends AbstractConfigEntry<List<V>, List<Config>, List<Gui>, Self> {
	protected Class<?> innerType;
	protected Function<V, Optional<ITextComponent>> elemErrorSupplier;
	protected boolean expand;
	protected int minSize = 0;
	protected int maxSize = Integer.MAX_VALUE;
	
	public AbstractListEntry(
	  ISimpleConfigEntryHolder parent, String name, @Nullable List<V> value
	) {
		super(parent, name, value != null ? value : new ArrayList<>());
	}
	
	public static abstract class Builder<V, Config, Gui,
	  Entry extends AbstractListEntry<V, Config, Gui, Entry>,
	  Self extends Builder<V, Config, Gui, Entry, Self>>
	  extends AbstractConfigEntryBuilder<List<V>, List<Config>, List<Gui>, Entry, Self> {
		protected Function<V, Optional<ITextComponent>> elemErrorSupplier = v -> Optional.empty();
		protected boolean expand = false;
		protected Class<?> innerType;
		protected int minSize = 0;
		protected int maxSize = Integer.MAX_VALUE;
		
		public Builder(List<V> value, Class<?> innerType) {
			super(value, List.class);
			this.innerType = innerType;
		}
		
		public Self expand() {
			return expand(true);
		}
		
		public Self expand(boolean expand) {
			Self copy = copy();
			copy.expand = expand;
			return copy;
		}
		
		/**
		 * Set the minimum (inclusive) allowed list size.
		 * @param minSize Inclusive minimum size
		 */
		public Self minSize(int minSize) {
			Self copy = copy();
			copy.minSize = minSize;
			return copy;
		}
		
		/**
		 * Set the maximum (inclusive) allowed list size.
		 * @param maxSize Inclusive maximum size
		 */
		public Self maxSize(int maxSize) {
			Self copy = copy();
			copy.maxSize = maxSize;
			return copy;
		}
		
		/**
		 * Set an error message supplier for the elements of this list entry<br>
		 * You may also use {@link IErrorEntry#error(Function)} to check
		 * instead the whole list<br>
		 * If a single element is deemed invalid, the whole list is considered invalid.
		 * @param errorSupplier Error message supplier. Empty return values indicate
		 *                      correct values
		 */
		public Self elemError(Function<V, Optional<ITextComponent>> errorSupplier) {
			Self copy = copy();
			copy.elemErrorSupplier = errorSupplier;
			return copy;
		}
		
		@Override
		protected Entry build(ISimpleConfigEntryHolder parent, String name) {
			final Entry e = super.build(parent, name);
			e.elemErrorSupplier = elemErrorSupplier;
			e.expand = expand;
			e.innerType = innerType;
			e.minSize = minSize;
			e.maxSize = maxSize;
			return e;
		}
		
		@Override protected Self copy() {
			final Self copy = super.copy();
			copy.elemErrorSupplier = elemErrorSupplier;
			copy.expand = expand;
			copy.innerType = innerType;
			copy.minSize = minSize;
			copy.maxSize = maxSize;
			return copy;
		}
	}
	/**
	 * Expand this list automatically in the GUI
	 */
	public Self expand() {
		return expand(true);
	}
	
	/**
	 * Expand this list automatically in the GUI
	 */
	public Self expand(boolean expand) {
		this.expand = expand;
		return self();
	}
	
	@Override
	public List<Gui> forGui(List<V> list) {
		return list.stream().map(this::elemForGui).collect(Collectors.toList());
	}
	
	@Override public @Nullable List<V> fromGui(@Nullable List<Gui> list) {
		if (list == null) return null;
		List<V> res = new ArrayList<>();
		for (Gui g: list) {
			V v = elemFromGui(g);
			if (v == null) return null;
			res.add(v);
		}
		return res;
	}
	
	@Override public List<Config> forConfig(List<V> list) {
		return list.stream().map(this::elemForConfig).collect(Collectors.toList());
	}
	
	@Override public @Nullable List<V> fromConfig(@Nullable List<Config> list) {
		if (list == null) return null;
		List<V> res = new ArrayList<>();
		for (Config c : list) {
			V v = elemFromConfig(c);
			if (v == null) return null;
			res.add(v);
		}
		return res;
	}
	
	protected Gui elemForGui(V value) {
		//noinspection unchecked
		return (Gui) value;
	}
	
	protected @Nullable V elemFromGui(Gui value) {
		//noinspection unchecked
		return (V) value;
	}
	
	protected Config elemForConfig(V value) {
		//noinspection unchecked
		return (Config) value;
	}
	
	protected @Nullable V elemFromConfig(Config value) {
		//noinspection unchecked
		return (V) value;
	}
	
	protected static ITextComponent addIndex(ITextComponent message, int index) {
		if (index < 0) return message;
		return message.copyRaw().appendString(", ").append(new TranslationTextComponent(
		  "simpleconfig.config.error.at_index",
		  new StringTextComponent(String.format("%d", index + 1)).mergeStyle(TextFormatting.DARK_AQUA)));
	}
	
	@Override public List<ITextComponent> getErrorsFromGUI(List<Gui> value) {
		return Stream.concat(
		  Stream.of(getErrorFromGUI(value)).filter(Optional::isPresent).map(Optional::get),
		  IntStream.range(0, value.size()).boxed()
		    .flatMap(i -> getElementErrors(i, value.get(i)).stream())
		).collect(Collectors.toList());
	}
	
	@Override public Optional<ITextComponent> getErrorFromGUI(List<Gui> value) {
		int size = value.size();
		if (size < minSize) {
			return Optional.of(new TranslationTextComponent(
			  "simpleconfig.config.error.list." + (minSize == 1? "empty" : "too_small"),
			  new StringTextComponent(String.valueOf(minSize)).mergeStyle(TextFormatting.DARK_AQUA)));
		} else if (size > maxSize) {
			return Optional.of(new TranslationTextComponent(
			  "simpleconfig.config.error.list.too_large",
			  new StringTextComponent(String.valueOf(maxSize)).mergeStyle(TextFormatting.DARK_AQUA)));
		}
		return super.getErrorFromGUI(value);
	}
	
	public Optional<ITextComponent> getElementError(int index, Gui value) {
		V elem = elemFromGui(value);
		if (elem == null) return Optional.of(addIndex(new TranslationTextComponent(
		  "simpleconfig.config.error.missing_value"), index));
		return elemErrorSupplier.apply(elem).map(e -> addIndex(e, index));
	}
	
	public List<ITextComponent> getElementErrors(int index, Gui value) {
		return Stream.of(getElementError(index, value))
		  .filter(Optional::isPresent).map(Optional::get)
		  .collect(Collectors.toList());
	}
	
	protected @Nullable String getListTypeComment() {
		return null;
	}
	
	@Override public List<String> getConfigCommentTooltips() {
		List<String> tooltips = super.getConfigCommentTooltips();
		String typeComment = getListTypeComment();
		if (typeComment != null) tooltips.add("List: " + typeComment);
		return tooltips;
	}
	
	@OnlyIn(Dist.CLIENT) protected <F extends ListFieldBuilder<Gui, ?, F>> F decorate(F builder) {
		builder = super.decorate(builder);
		builder.setCellErrorSupplier(this::getElementError)
		  .setExpanded(expand)
		  .setCaptionControlsEnabled(false)
		  .setInsertInFront(false);
		return builder;
	}
}