package endorh.simpleconfig.ui.gui.widget.combobox.wrapper;

import endorh.simpleconfig.api.ui.TextFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ResourceLocationTypeWrapper implements TypeWrapper<ResourceLocation> {
	
	boolean hasIcon = false;
	int iconHeight = 20;
	int iconWidth = 20;
	
	public ResourceLocationTypeWrapper() {}
	
	protected ResourceLocationTypeWrapper(int iconSize) {
		this(iconSize, iconSize);
	}
	
	protected ResourceLocationTypeWrapper(int iconWidth, int iconHeight) {
		hasIcon = true;
		this.iconHeight = iconHeight;
		this.iconWidth = iconWidth;
	}
	
	@Override public boolean hasIcon() {
		return hasIcon;
	}
	
	@Override public int getIconHeight() {
		return hasIcon ? iconHeight : TypeWrapper.super.getIconHeight();
	}
	
	@Override public int getIconWidth() {
		return hasIcon ? iconWidth : TypeWrapper.super.getIconWidth();
	}
	
	@Override public Pair<Optional<ResourceLocation>, Optional<Component>> parseElement(
	  @NotNull String text
	) {
		try {
			return Pair.of(Optional.of(new ResourceLocation(text)), Optional.empty());
		} catch (ResourceLocationException e) {
			return Pair.of(
			  Optional.empty(), Optional.of(new TextComponent(e.getLocalizedMessage())));
		}
	}
	
	@Override public Component getDisplayName(@NotNull ResourceLocation element) {
		if (element.getNamespace().equals("minecraft"))
			return new TextComponent(element.getPath());
		return new TextComponent(element.getNamespace()).withStyle(ChatFormatting.GRAY)
		  .append(new TextComponent(":").withStyle(ChatFormatting.GRAY))
		  .append(new TextComponent(element.getPath()).withStyle(ChatFormatting.WHITE));
	}
	
	@Override public String getName(@NotNull ResourceLocation element) {
		return element.getNamespace().equals("minecraft") ? element.getPath() : element.toString();
	}
	
	@Override public @Nullable TextFormatter getTextFormatter() {
		return TextFormatter.forResourceLocation();
	}
}
