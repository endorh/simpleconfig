package endorh.simpleconfig.core.entry;

import endorh.simpleconfig.core.ISimpleConfigEntryHolder;
import endorh.simpleconfig.ui.api.ConfigEntryBuilder;
import endorh.simpleconfig.ui.impl.builders.DoubleListBuilder;
import endorh.simpleconfig.ui.impl.builders.FieldBuilder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class DoubleListEntry extends RangedListEntry<Double, Number, Double, DoubleListEntry> {
	@Internal public DoubleListEntry(
	  ISimpleConfigEntryHolder parent, String name,
	  @Nullable List<Double> value
	) {
		super(parent, name, value);
	}
	
	public static class Builder extends RangedListEntry.Builder<Double, Number, Double, DoubleListEntry, Builder> {
		public Builder(List<Double> value) {
			super(value, Double.class);
		}
		
		/**
		 * Set the minimum allowed value for the elements of this list entry (inclusive)
		 */
		@Contract(pure=true) public Builder min(double min) {
			return super.min(min);
		}
		
		/**
		 * Set the maximum allowed value for the elements of this list entry (inclusive)
		 */
		@Contract(pure=true) public Builder max(double max) {
			return super.max(max);
		}
		
		/**
		 * Set the minimum and the maximum allowed for the elements of this list entry (inclusive)
		 */
		@Contract(pure=true) public Builder range(double min, double max) {
			return super.range(min, max);
		}
		
		@Override
		protected void checkBounds() {
			min = min != null ? min : Double.NEGATIVE_INFINITY;
			max = max != null ? max : Double.POSITIVE_INFINITY;
		}
		
		@Override
		protected DoubleListEntry buildEntry(ISimpleConfigEntryHolder parent, String name) {
			return new DoubleListEntry(parent, name, value);
		}
		
		@Override protected Builder createCopy() {
			return new Builder(value);
		}
	}
	
	@Override
	protected Double elemFromConfig(Number value) {
		return value != null? value.doubleValue() : null;
	}
	
	@OnlyIn(Dist.CLIENT) @Override
	public Optional<FieldBuilder<List<Double>, ?, ?>> buildGUIEntry(ConfigEntryBuilder builder) {
		final DoubleListBuilder valBuilder = builder
		  .startDoubleList(getDisplayName(), get());
		return Optional.of(decorate(valBuilder));
	}
}
