package endorh.simple_config.clothconfig2.gui.entries;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

@OnlyIn(value = Dist.CLIENT)
public class LongListEntry
  extends TextFieldListEntry<Long> implements IRangedEntry<Long> {
	private static final Function<String, String> stripCharacters = s -> {
		StringBuilder stringBuilder_1 = new StringBuilder();
		char[] var2 = s.toCharArray();
		int var3 = var2.length;
		for (char c : var2) {
			if (!Character.isDigit(c) && c != '-') continue;
			stringBuilder_1.append(c);
		}
		return stringBuilder_1.toString();
	};
	private long minimum = Long.MIN_VALUE;
	private long maximum = Long.MAX_VALUE;
	
	@Internal public LongListEntry(ITextComponent fieldName, Long value) {
		super(fieldName, value, false);
	}
	
	@Override
	protected String stripAddText(String s) {
		return stripCharacters.apply(s);
	}
	
	@Override
	protected void textFieldPreRender(TextFieldWidget widget) {
		try {
			double i = Long.parseLong(widget.getText());
			if (i < (double) this.minimum || i > (double) this.maximum) {
				widget.setTextColor(0xFF5555);
			} else {
				widget.setTextColor(0xE0E0E0);
			}
		} catch (NumberFormatException ex) {
			widget.setTextColor(0xFF5555);
		}
	}
	
	@Override public void setMinimum(Long minimum) {
		this.minimum = minimum != null ? minimum : Long.MIN_VALUE;
	}
	
	@Override public void setMaximum(Long maximum) {
		this.maximum = maximum != null ? maximum : Long.MAX_VALUE;
	}
	
	@Override protected @Nullable Long fromString(String s) {
		try {
			return Long.valueOf(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	@Internal @Override public Optional<ITextComponent> getErrorMessage() {
		try {
			long i = Long.parseLong(getText());
			if (i > this.maximum)
				return Optional.of(new TranslationTextComponent("text.cloth-config.error.too_large", this.maximum));
			if (i < this.minimum)
				return Optional.of(new TranslationTextComponent("text.cloth-config.error.too_small", this.minimum));
		} catch (NumberFormatException ex) {
			return Optional.of(
			  new TranslationTextComponent("text.cloth-config.error.not_valid_number_long"));
		}
		return super.getErrorMessage();
	}
}
