package endorh.simpleconfig.clothconfig2.gui;

import endorh.simpleconfig.clothconfig2.gui.widget.TintedButton;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static endorh.simpleconfig.clothconfig2.gui.ExternalChangesDialog.ExternalChangeResponse.*;
import static endorh.simpleconfig.core.SimpleConfigTextUtil.splitTtc;

public class ExternalChangesDialog extends ConfirmDialog {
	TintedButton acceptAllButton;
	protected ModConfig.Type type;
	protected @Nullable Consumer<ExternalChangeResponse> responseAction;
	
	public static ExternalChangesDialog create(
	  Type type, IOverlayCapableScreen screen, Consumer<ExternalChangeResponse> handler
	) {
		return create(type, screen, handler, null);
	}
	
	public static ExternalChangesDialog create(
	  Type type, IOverlayCapableScreen screen, Consumer<ExternalChangeResponse> handler,
	  @Nullable Consumer<ExternalChangesDialog> builder
	) {
		ExternalChangesDialog dialog = new ExternalChangesDialog(type, screen, handler);
		if (builder != null) builder.accept(dialog);
		return dialog;
	}
	
	protected ExternalChangesDialog(
	  Type type, IOverlayCapableScreen screen, @NotNull Consumer<ExternalChangeResponse> action
	) {
		super(screen, new TranslationTextComponent(
		  type == Type.SERVER? "simpleconfig.ui.remote_changes_detected.title" :
		  "simpleconfig.ui.external_changes_detected.title"));
		this.type = type;
		this.responseAction = action;
		setPersistent(true);
		setBody(Stream.concat(
		  splitTtc(type == Type.SERVER? "simpleconfig.ui.remote_changes_detected.body" :
		           "simpleconfig.ui.external_changes_detected.body"
		  ).stream(), Stream.of(
			 StringTextComponent.EMPTY, new TranslationTextComponent(
				"simpleconfig.ui.prompt_accept_changes"))).collect(Collectors.toList()));
		
		acceptAllButton = new TintedButton(
		  0, 0, 120, 20, new TranslationTextComponent("simpleconfig.ui.action.accept_all_changes"),
		  p -> {
			  if (responseAction != null) {
				  responseAction.accept(ACCEPT_ALL);
				  responseAction = null;
				  cancel(true);
			  }
		  });
		addButton(1, acceptAllButton);
		cancelButton.setTintColor(0x807F2424);
		acceptAllButton.setTintColor(0x8081542F);
		confirmButton.setTintColor(0x80683498);
		withAction(this::action);
		
		setCancelText(new TranslationTextComponent("simpleconfig.ui.action.reject_changes"));
		setConfirmText(new TranslationTextComponent("simpleconfig.ui.action.accept_non_conflicting_changes"));
	}
	
	public void action(boolean acceptUnedited) {
		if (responseAction != null)
			responseAction.accept(acceptUnedited? ACCEPT_NON_CONFLICTING : REJECT);
	}
	
	public enum ExternalChangeResponse {
		REJECT, ACCEPT_ALL, ACCEPT_NON_CONFLICTING
	}
}
