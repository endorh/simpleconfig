package endorh.simpleconfig.ui.gui.entries;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import endorh.simpleconfig.api.AbstractRange;
import endorh.simpleconfig.ui.api.*;
import endorh.simpleconfig.ui.gui.WidgetUtils;
import endorh.simpleconfig.ui.gui.widget.ToggleImageButton;
import endorh.simpleconfig.api.ui.icon.Icon;
import endorh.simpleconfig.api.ui.icon.SimpleConfigIcons;
import endorh.simpleconfig.ui.impl.ISeekableComponent;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;

public class RangeListEntry<
  V extends Comparable<V>, R extends AbstractRange<V, R>,
  E extends AbstractConfigField<V> & IChildListEntry
> extends TooltipListEntry<R> implements IChildListEntry, IEntryHolder {
	private final E minEntry;
	private final E maxEntry;
	protected Icon middleIcon;
	protected Icon comparisonIcon = SimpleConfigIcons.Entries.LESS_EQUAL;
	protected ToggleImageButton minExclusivenessButton;
	protected ToggleImageButton maxExclusivenessButton;
	protected List<IGuiEventListener> listeners;
	protected List<AbstractConfigField<?>> heldEntries;
	protected List<ISeekableComponent> seekableChildren;
	
	public RangeListEntry(
	  ITextComponent fieldName, R value, final E minEntry, final E maxEntry
	) {
		super(fieldName);
		this.minEntry = minEntry;
		this.maxEntry = maxEntry;
		minExclusivenessButton = ToggleImageButton.of(
		  value.isExclusiveMin(), 18, comparisonIcon);
		maxExclusivenessButton = ToggleImageButton.of(
		  value.isExclusiveMax(), 18, comparisonIcon);
		minExclusivenessButton.setHoverOverlayColor(0x42808080);
		maxExclusivenessButton.setHoverOverlayColor(0x42808080);
		minEntry.setChildSubEntry(true);
		maxEntry.setChildSubEntry(true);
		minEntry.setParentEntry(this);
		maxEntry.setParentEntry(this);
		minEntry.setName("min");
		maxEntry.setName("max");
		setValue(value);
		setDisplayedValue(value);
		listeners = Lists.newArrayList(
		  minEntry, minExclusivenessButton, maxExclusivenessButton, maxEntry, sideButtonReference);
		heldEntries = Lists.newArrayList(minEntry, maxEntry);
		seekableChildren = Lists.newArrayList(minEntry, maxEntry);
	}
	
	@Override public R getDisplayedValue() {
		final V left = minEntry.getDisplayedValue();
		final V right = maxEntry.getDisplayedValue();
		return left == null && right == null? null : getValue().create(
		  left, right,
		  minExclusivenessButton.getValue(),
		  maxExclusivenessButton.getValue());
	}
	
	@Override public void setDisplayedValue(R value) {
		minEntry.setDisplayedValue(value != null ? value.getMin() : null);
		maxEntry.setDisplayedValue(value != null ? value.getMax() : null);
		minExclusivenessButton.setToggle(value != null && value.isExclusiveMin());
		maxExclusivenessButton.setToggle(value != null && value.isExclusiveMax());
	}
	
	@Override public void updateFocused(boolean isFocused) {
		super.updateFocused(isFocused);
		minEntry.updateFocused(isFocused && getListener() == minEntry);
		maxEntry.updateFocused(isFocused && getListener() == maxEntry);
		WidgetUtils.forceSetFocus(minExclusivenessButton, isFocused && getListener() == minExclusivenessButton);
		WidgetUtils.forceSetFocus(maxExclusivenessButton, isFocused && getListener() == maxExclusivenessButton);
	}
	
	@Override public boolean isGroup() {
		return false;
	}
	
	@Override public List<EntryError> getEntryErrors() {
		List<EntryError> errors = super.getEntryErrors();
		errors.addAll(IEntryHolder.super.getErrors());
		return errors;
	}
	
	@Override public void renderChildEntry(
	  MatrixStack mStack, int x, int y, int w, int h, int mouseX, int mouseY, float delta
	) {
		int iconWidth = middleIcon != null? middleIcon.w : 0;
		int buttonWidth = minExclusivenessButton.getWidth();
		int centerWidth = buttonWidth * 2 + 2 * 2 + iconWidth;
		int leftWidth = (int) ((w - centerWidth) * 0.5D);
		int rightWidth = w - centerWidth - leftWidth;
		minExclusivenessButton.x = x + leftWidth + 2;
		minExclusivenessButton.y = y + 1;
		maxExclusivenessButton.x = x + leftWidth + buttonWidth + 2 + iconWidth;
		maxExclusivenessButton.y = y + 1;
		if (middleIcon != null)
			middleIcon.renderCentered(mStack, x + leftWidth, y, middleIcon.w, h);
		minEntry.renderChild(
		  mStack, x, y, leftWidth, h, mouseX, mouseY, delta);
		minExclusivenessButton.render(mStack, mouseX, mouseY, delta);
		if (middleIcon != null) middleIcon.renderCentered(
		  mStack, x + leftWidth + buttonWidth + 2, y, iconWidth, 20, 0);
		maxExclusivenessButton.render(mStack, mouseX, mouseY, delta);
		maxEntry.renderChild(
		  mStack, x + leftWidth + centerWidth, y, rightWidth, h,
		  mouseX, mouseY, delta);
	}
	
	@Override public int getExtraScrollHeight() {
		return max(minEntry.getExtraScrollHeight(), maxEntry.getExtraScrollHeight());
	}
	
	@Override public List<INavigableTarget> getNavigableSubTargets() {
		List<INavigableTarget> targets = new ArrayList<>();
		List<INavigableTarget> minTargets = minEntry.getNavigableSubTargets();
		if (!minTargets.isEmpty()) targets.addAll(minTargets);
		else targets.add(minEntry);
		List<INavigableTarget> maxTargets = maxEntry.getNavigableSubTargets();
		if (!maxTargets.isEmpty()) targets.addAll(maxTargets);
		else targets.add(maxEntry);
		return targets;
	}
	
	@Override public boolean charTyped(char chr, int modifiers) {
		IGuiEventListener f = getListener();
		ToggleImageButton button =
		  f == minEntry || f == minExclusivenessButton
		  ? minExclusivenessButton :
		  f == maxEntry || f == maxExclusivenessButton ? maxExclusivenessButton : null;
		if (button != null && button.active) switch (chr) {
			case '<':
				button.setToggle(true);
				return true;
			case '=':
				button.setToggle(false);
				return true;
		}
		return super.charTyped(chr, modifiers);
	}
	
	@Override public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
		return super.onKeyPressed(keyCode, scanCode, modifiers);
	}
	
	@Override protected List<ISeekableComponent> seekableChildren() {
		return seekableChildren;
	}
	
	@Override protected @NotNull List<? extends IGuiEventListener> getEntryListeners() {
		return listeners;
	}
	
	@Override public List<AbstractConfigField<?>> getHeldEntries() {
		return heldEntries;
	}
	
	public E getMinEntry() {
		return minEntry;
	}
	
	public E getMaxEntry() {
		return maxEntry;
	}
	
	public boolean isMinExclusivenessEditable() {
		return minExclusivenessButton.active;
	}
	
	public boolean isMaxExclusivenessEditable() {
		return maxExclusivenessButton.active;
	}
	
	public void setMinExclusivenessEditable(boolean editable) {
		minExclusivenessButton.active = editable;
		minExclusivenessButton.setTint(editable? 0xF0A0BDFF : 0);
	}
	
	public void setMaxExclusivenessEditable(boolean editable) {
		maxExclusivenessButton.active = editable;
		maxExclusivenessButton.setTint(editable? 0xF0A0BDFF : 0);
	}
	
	public ToggleImageButton getMinExclusivenessButton() {
		return minExclusivenessButton;
	}
	
	public ToggleImageButton getMaxExclusivenessButton() {
		return maxExclusivenessButton;
	}
	
	public void setComparisonIcon(@NotNull Icon icon) {
		comparisonIcon = icon;
		minExclusivenessButton.setIcon(icon);
		maxExclusivenessButton.setIcon(icon);
	}
	
	public void setMiddleIcon(@Nullable Icon icon) {
		middleIcon = icon;
	}
}
