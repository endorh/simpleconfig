package endorh.simple_config.clothconfig2.impl.builders;

import endorh.simple_config.clothconfig2.api.ConfigEntryBuilder;
import endorh.simple_config.clothconfig2.gui.entries.IntegerListListEntry;
import endorh.simple_config.clothconfig2.gui.entries.IntegerListListEntry.IntegerListCell;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@OnlyIn(value = Dist.CLIENT)
public class IntListBuilder
  extends RangedListFieldBuilder<Integer, IntegerListCell, IntegerListListEntry, IntListBuilder> {
	
	public IntListBuilder(ConfigEntryBuilder builder, ITextComponent name, List<Integer> value) {
		super(builder, name, value);
	}
	
	public IntListBuilder setMin(int min) {
		return super.setMin(min);
	}
	
	public IntListBuilder setMax(int max) {
		return super.setMax(max);
	}
	
	@Override @NotNull public IntegerListListEntry buildEntry() {
		return new IntegerListListEntry(
		  this.fieldNameKey, this.value
		);
	}
}

