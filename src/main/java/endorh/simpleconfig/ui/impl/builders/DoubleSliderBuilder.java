package endorh.simpleconfig.ui.impl.builders;

import endorh.simpleconfig.ui.api.ConfigEntryBuilder;
import endorh.simpleconfig.ui.gui.entries.DoubleSliderEntry;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class DoubleSliderBuilder extends SliderFieldBuilder<Double, DoubleSliderEntry, DoubleSliderBuilder> {
	
	@Internal public DoubleSliderBuilder(ConfigEntryBuilder builder, ITextComponent name, double value, double min, double max) {
		super(builder, name, value, min, max);
	}
	
	@Override @NotNull public DoubleSliderEntry buildEntry() {
		return new DoubleSliderEntry(fieldNameKey, min, max, value);
	}
}

