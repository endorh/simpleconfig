package endorh.simpleconfig.ui.api;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import endorh.simpleconfig.ui.gui.AbstractDialog;
import net.minecraft.client.gui.INestedGuiEventHandler;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Feature interface for screens that can display an {@link AbstractDialog}.<br>
 * For convenience, extends the {@link IOverlayCapableContainer} and {@link IModalInputCapableScreen},
 * which some dialogs may benefit from.
 * <br><br>
 * Implementations should add hooks in {@link Screen#keyPressed(int, int, int)},
 * {@link Screen#keyReleased(int, int, int)}, {@link Screen#charTyped(char, int)}
 * {@link Screen#mouseClicked(double, double, int)},
 * {@link Screen#mouseReleased(double, double, int)},
 * {@link Screen#mouseScrolled(double, double, double)} and
 * {@link Screen#mouseDragged(double, double, int, double, double)}, calling the respective
 * {@link #handleDialogsKeyPressed(int, int, int)} methods.
 * <br><br>
 * The {@link #handleDialogsEscapeKey()} and {@link #handleDialogsChangeFocus(boolean)} do not
 * need to be called manually, as they're called by {@link #handleDialogsKeyPressed(int, int, int)}.
 * <br><br>
 * Implementations should also add the hooks required by {@link IOverlayCapableContainer},
 * {@link IModalInputCapableScreen} and {@link IMultiTooltipScreen}.
 */
public interface IDialogCapableScreen extends IOverlayCapableContainer, IModalInputCapableScreen, IMultiTooltipScreen {
	SortedDialogCollection getDialogs();
	
	default boolean hasDialogs() {
		return !getDialogs().isEmpty();
	}
	
	default void addDialog(AbstractDialog dialog) {
		dialog.setScreen(this);
		getDialogs().add(dialog);
	}
	
	default void removeDialog(AbstractDialog dialog) {
		getDialogs().remove(dialog);
	}
	
	default void renderDialogs(MatrixStack mStack, int mouseX, int mouseY, float delta) {
		SortedDialogCollection dialogs = getDialogs();
		dialogs.update();
		List<AbstractDialog> current = dialogs.getDialogs();
		int size = current.size();
		int count = 1;
		for (AbstractDialog dialog : current) {
			mStack.push(); {
				mStack.translate(0D, 0D, count * 100D);
				int mX = count == size? mouseX : -1;
				int mY = count == size? mouseY : -1;
				if (!dialog.render(mStack, mX, mY, delta))
					dialogs.remove(dialog);
				count++;
			} mStack.pop();
		}
		dialogs.update();
		current = dialogs.getDialogs();
		if (!current.isEmpty() && this instanceof INestedGuiEventHandler)
			((INestedGuiEventHandler) this).setListener(current.get(current.size() - 1));
	}
	
	default void tickDialogs() {
		List<AbstractDialog> current = getDialogs().getDialogs();
		int i = 0;
		for (AbstractDialog dialog : current) dialog.tick(++i == current.size());
	}
	
	default boolean handleDialogsMouseClicked(double mouseX, double mouseY, int button) {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsMouseScrolled(double mouseX, double mouseY, double delta) {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.mouseScrolled(mouseX, mouseY, delta);
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsMouseDragged(
	  double mouseX, double mouseY, int button, double dragX, double dragY
	) {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.mouseDragged(mouseX, mouseY, button, dragX, dragY);
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsMouseReleased(
	  double mouseX, double mouseY, int button
	) {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.mouseReleased(mouseX, mouseY, button);
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) return handleDialogsEscapeKey(); // Esc
		if (keyCode == 258) return handleDialogsChangeFocus(!Screen.hasShiftDown());
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsCharTyped(char codePoint, int modifiers) {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.charTyped(codePoint, modifiers);
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsKeyReleased(int keyCode, int scanCode, int modifiers) {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.keyReleased(keyCode, scanCode, modifiers);
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsEscapeKey() {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			last.escapeKeyPressed();
			return true;
		}
		return false;
	}
	
	default boolean handleDialogsChangeFocus(boolean forward) {
		AbstractDialog last = getDialogs().getLast();
		if (last != null) {
			if (!last.changeFocus(forward)) last.changeFocus(forward);
			return true;
		}
		return false;
	}
	
	class SortedDialogCollection {
		private final List<AbstractDialog> dialogs = Lists.newArrayList();
		private final List<AbstractDialog> reversed = Lists.reverse(dialogs);
		private final List<AbstractDialog> added = Lists.newArrayList();
		private final List<AbstractDialog> removed = Lists.newArrayList();
		
		public List<AbstractDialog> getDialogs() {
			return dialogs;
		}
		
		public List<AbstractDialog> getDescendingDialogs() {
			return reversed;
		}
		
		public @Nullable AbstractDialog getLast() {
			return reversed.isEmpty()? null : reversed.get(0);
		}
		
		public List<AbstractDialog> getAdded() {
			return added;
		}
		
		public List<AbstractDialog> getRemoved() {
			return removed;
		}
		
		public void update() {
			List<AbstractDialog> dialogs = getDialogs();
			List<AbstractDialog> added = getAdded();
			List<AbstractDialog> removed = getRemoved();
			dialogs.addAll(added);
			dialogs.removeAll(removed);
			added.clear();
			removed.clear();
		}
		
		public boolean isEmpty() {
			return dialogs.isEmpty();
		}
		
		public void add(AbstractDialog dialog) {
			added.add(dialog);
		}
		
		public void remove(AbstractDialog dialog) {
			removed.add(dialog);
		}
	}
}