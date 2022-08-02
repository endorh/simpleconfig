package endorh.simpleconfig.ui.impl;

import endorh.simpleconfig.ui.api.Modifier;
import endorh.simpleconfig.ui.api.ModifierKeyCode;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OnlyIn(value = Dist.CLIENT)
public class ModifierKeyCodeImpl implements ModifierKeyCode {
	private InputMappings.Input keyCode;
	private Modifier modifier;
	
	@Override public InputMappings.Input getKeyCode() {
		return keyCode;
	}
	
	@Override public Modifier getModifier() {
		return modifier;
	}
	
	@Override public ModifierKeyCode setKeyCode(InputMappings.Input keyCode) {
		this.keyCode = keyCode.getType().getOrMakeInput(keyCode.getKeyCode());
		if (keyCode.equals(InputMappings.INPUT_INVALID)) {
			setModifier(Modifier.none());
		}
		return this;
	}
	
	@Override public ModifierKeyCode setModifier(Modifier modifier) {
		this.modifier = Modifier.of(modifier.getValue());
		return this;
	}
	
	@Override public ModifierKeyCode addModifier(Modifier mod) {
		modifier = modifier.merge(mod);
		return this;
	}
	
	@Override public String toString() {
		return getLocalizedName().getString();
	}
	
	private static final Pattern TITLE_CASE_PATTERN = Pattern.compile("(?<!\\w)\\w");
	private static String toTitleCase(String s) {
		final Matcher m = TITLE_CASE_PATTERN.matcher(s);
		final StringBuffer sb = new StringBuffer();
		while (m.find()) m.appendReplacement(sb, m.group().toUpperCase());
		m.appendTail(sb);
		return sb.toString();
	}
	
	private static final Pattern KEY_PATTERN = Pattern.compile("^key\\.keyboard\\.(?<key>\\w)$");
	
	@Override public ITextComponent getLayoutAgnosticLocalizedName(Style modifiers, Style key) {
		final String name = keyCode.getTranslationKey();
		final Matcher m = KEY_PATTERN.matcher(name);
		ITextComponent base;
		if (m.matches()) {
			base = new StringTextComponent(m.group("key").toUpperCase()).mergeStyle(key);
		} else {
			base = new StringTextComponent(toTitleCase(keyCode.func_237520_d_().getString())).mergeStyle(key);
		}
		if (modifier.hasShift())
			base = new TranslationTextComponent("modifier.cloth-config.shift", base).mergeStyle(modifiers);
		if (modifier.hasAlt())
			base = new TranslationTextComponent("modifier.cloth-config.alt", base).mergeStyle(modifiers);
		if (modifier.hasControl())
			base = new TranslationTextComponent("modifier.cloth-config.ctrl", base).mergeStyle(modifiers);
		return base;
	}
	
	@Override public ITextComponent getLocalizedName(Style modifiers, Style key) {
		ITextComponent base = new StringTextComponent(toTitleCase(keyCode.func_237520_d_().getString())).mergeStyle(key);
		if (modifier.hasShift())
			base = new TranslationTextComponent("modifier.cloth-config.shift", base).mergeStyle(modifiers);
		if (modifier.hasAlt())
			base = new TranslationTextComponent("modifier.cloth-config.alt", base).mergeStyle(modifiers);
		if (modifier.hasControl())
			base = new TranslationTextComponent("modifier.cloth-config.ctrl", base).mergeStyle(modifiers);
		return base;
	}
	
	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ModifierKeyCode)) return false;
		ModifierKeyCode that = (ModifierKeyCode) o;
		return keyCode.equals(that.getKeyCode()) &&
             modifier.equals(that.getModifier());
	}
	
	@Override public int hashCode() {
		int result = keyCode != null ? keyCode.hashCode() : 0;
		result = 31 * result + (modifier != null ? modifier.hashCode() : 0);
		return result;
	}
}
