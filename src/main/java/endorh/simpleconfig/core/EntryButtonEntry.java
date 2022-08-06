package endorh.simpleconfig.core;

import endorh.simpleconfig.core.entry.GUIOnlyEntry;
import endorh.simpleconfig.ui.api.AbstractConfigListEntry;
import endorh.simpleconfig.ui.api.ConfigEntryBuilder;
import endorh.simpleconfig.ui.gui.entries.EntryButtonListEntry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class EntryButtonEntry<V, Gui, Inner extends AbstractConfigEntry<V, ?, Gui, Inner> & IKeyEntry<Gui>>
  extends GUIOnlyEntry<V, Gui, EntryButtonEntry<V, Gui, Inner>> {
	
	protected Inner inner;
	protected BiConsumer<V, ISimpleConfigEntryHolder> action;
	protected Supplier<ITextComponent> buttonLabelSupplier;
	
	public EntryButtonEntry(
	  ISimpleConfigEntryHolder parent, String name,
	  AbstractConfigEntryBuilder<V, ?, Gui, Inner, ?> innerBuilder,
	  V value, BiConsumer<V, ISimpleConfigEntryHolder> action,
	  Class<?> typeClass
	) {
		super(parent, name, value, false, typeClass);
		this.inner = DummyEntryHolder.build(parent, innerBuilder);
		this.action = action;
	}
	
	public static class Builder<V, Gui, Inner extends AbstractConfigEntry<V, ?, Gui, Inner> & IKeyEntry<Gui>>
	  extends GUIOnlyEntry.Builder<V, Gui, EntryButtonEntry<V, Gui, Inner>, Builder<V, Gui, Inner>> {
		
		protected AbstractConfigEntryBuilder<V, ?, Gui, Inner, ?> inner;
		protected BiConsumer<V, ISimpleConfigEntryHolder> action;
		protected Supplier<ITextComponent> buttonLabelSupplier = () -> new StringTextComponent("✓");
		
		public Builder(
		  AbstractConfigEntryBuilder<V, ?, Gui, Inner, ?> inner,
		  BiConsumer<V, ISimpleConfigEntryHolder> action
		) {
			super(inner.value, inner.typeClass);
			this.inner = inner;
			this.action = action;
		}
		
		@Contract(pure=true) public Builder<V, Gui, Inner> label(String translation) {
			Builder<V, Gui, Inner> copy = copy();
			final TranslationTextComponent ttc = new TranslationTextComponent(translation);
			copy.buttonLabelSupplier = () -> ttc;
			return copy;
		}
		
		@Contract(pure=true) public Builder<V, Gui, Inner> label(ITextComponent label) {
			Builder<V, Gui, Inner> copy = copy();
			copy.buttonLabelSupplier = () -> label;
			return copy;
		}
		
		@Contract(pure=true) public Builder<V, Gui, Inner> label(Supplier<ITextComponent> label) {
			Builder<V, Gui, Inner> copy = copy();
			copy.buttonLabelSupplier = label;
			return copy;
		}
		
		@Override protected final EntryButtonEntry<V, Gui, Inner> buildEntry(
		  ISimpleConfigEntryHolder parent, String name
		) {
			final EntryButtonEntry<V, Gui, Inner> entry = new EntryButtonEntry<>(
			  parent, name, inner, value, action, typeClass);
			entry.buttonLabelSupplier = buttonLabelSupplier;
			return entry;
		}
		
		@Override protected Builder<V, Gui, Inner> createCopy() {
			final Builder<V, Gui, Inner> copy = new Builder<>(inner, action);
			copy.buttonLabelSupplier = buttonLabelSupplier;
			return copy;
		}
	}
	
	@Override public Gui forGui(V value) {
		return inner.forGui(value);
	}
	
	@Nullable @Override public V fromGui(@Nullable Gui value) {
		return inner.fromGui(value);
	}
	
	@OnlyIn(Dist.CLIENT) @Override public Optional<AbstractConfigListEntry<Gui>> buildGUIEntry(
	  ConfigEntryBuilder builder
	) {
		final EntryButtonListEntry<Gui, ?> entry = new EntryButtonListEntry<>(
		  getDisplayName(), inner.buildChildGUIEntry(builder),
		  g -> action.accept(fromGuiOrDefault(g), parent), buttonLabelSupplier);
		entry.entry.setIgnoreEdits(true);
		return Optional.of(entry);
	}
}
