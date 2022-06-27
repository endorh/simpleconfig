package endorh.simpleconfig.clothconfig2.impl.builders;

import endorh.simpleconfig.clothconfig2.api.AbstractConfigListEntry;
import endorh.simpleconfig.clothconfig2.api.ConfigEntryBuilder;
import endorh.simpleconfig.clothconfig2.api.IChildListEntry;
import endorh.simpleconfig.clothconfig2.gui.entries.AbstractListListEntry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public abstract class ListFieldBuilder<V, Entry extends AbstractListListEntry<V, ?, Entry>,
  Self extends ListFieldBuilder<V, Entry, Self>> extends FieldBuilder<List<V>, Entry, Self> {
	
	@NotNull protected Function<V, Optional<ITextComponent>> cellErrorSupplier = v -> Optional.empty();
	protected ITextComponent[] addTooltip = new ITextComponent[] {
	  new TranslationTextComponent("simpleconfig.help.list.insert"),
	  new TranslationTextComponent("simpleconfig.help.list.insert:key")
	};
	protected ITextComponent[] removeTooltip = new ITextComponent[] {
	  new TranslationTextComponent("simpleconfig.help.list.remove"),
	  new TranslationTextComponent("simpleconfig.help.list.remove:key")
	};
	protected boolean expanded = false;
	protected boolean insertInFront = false;
	protected boolean deleteButtonEnabled = true;
	protected boolean captionControlsEnabled = false;
	// protected @Nullable AbstractConfigListEntry<?> heldEntry = null;
	
	protected ListFieldBuilder(
	  ConfigEntryBuilder builder, ITextComponent name, List<V> value
	) {
		super(builder, name, value);
	}
	
	public Self setCellErrorSupplier(@NotNull Function<V, Optional<ITextComponent>> cellError) {
		cellErrorSupplier = cellError;
		return self();
	}
	
	public Self setAddButtonTooltip(ITextComponent[] addTooltip) {
		this.addTooltip = addTooltip;
		return self();
	}
	
	public Self setRemoveButtonTooltip(ITextComponent[] removeTooltip) {
		this.removeTooltip = removeTooltip;
		return self();
	}
	
	public Self setExpanded(boolean expanded) {
		this.expanded = expanded;
		return self();
	}
	
	public Self setInsertInFront(boolean insertInFront) {
		this.insertInFront = insertInFront;
		return self();
	}
	
	public Self setDeleteButtonEnabled(boolean enabled) {
		this.deleteButtonEnabled = enabled;
		return self();
	}
	
	public Self setCaptionControlsEnabled(boolean enabled) {
		this.captionControlsEnabled = enabled;
		return self();
	}
	
	// public <E extends AbstractConfigListEntry<?> & IChildListEntry> Self setHeldEntry(E entry) {
	// 	heldEntry = entry;
	// 	return self();
	// }
	//
	// protected <E extends AbstractConfigListEntry<?> & IChildListEntry> E getHeldEntry() {
	// 	//noinspection unchecked
	// 	return (E) heldEntry;
	// }
	
	@Override public @NotNull Entry build() {
		final Entry entry = super.build();
		entry.setCellErrorSupplier(cellErrorSupplier);
		entry.setExpanded(expanded);
		entry.setAddTooltip(addTooltip);
		entry.setRemoveTooltip(removeTooltip);
		entry.setDeleteButtonEnabled(deleteButtonEnabled);
		entry.setInsertInFront(insertInFront);
		entry.setCaptionControlsEnabled(captionControlsEnabled);
		// if (heldEntry != null)
		// 	entry.setHeldEntry(getHeldEntry());
		return entry;
	}
}
