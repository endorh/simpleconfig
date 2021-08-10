package endorh.simple_config.clothconfig2.gui.entries;

import com.mojang.blaze3d.matrix.MatrixStack;
import endorh.simple_config.clothconfig2.gui.AbstractConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@OnlyIn(value = Dist.CLIENT)
public class TextListEntry extends TooltipListEntry<Void> {
	public static final int LINE_HEIGHT = 12;
	private final FontRenderer textRenderer;
	private final int color;
	private final ITextComponent text;
	private int savedWidth;
	private int savedX;
	private int savedY;
	private List<IReorderingProcessor> wrappedLines;
	
	@Deprecated
	@ApiStatus.Internal
	public TextListEntry(ITextComponent fieldName, ITextComponent text) {
		this(fieldName, text, -1);
	}
	
	@Deprecated
	@ApiStatus.Internal
	public TextListEntry(ITextComponent fieldName, ITextComponent text, int color) {
		this(fieldName, text, color, null);
	}
	
	@Deprecated
	@ApiStatus.Internal
	public TextListEntry(
	  ITextComponent fieldName, ITextComponent text, int color,
	  Supplier<Optional<ITextComponent[]>> tooltipSupplier
	) {
		super(fieldName, tooltipSupplier);
		this.textRenderer = Minecraft.getInstance().fontRenderer;
		this.savedWidth = -1;
		this.savedX = -1;
		this.savedY = -1;
		this.text = text;
		this.color = color;
		this.wrappedLines = Collections.emptyList();
	}
	
	@Override
	public void render(
	  MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
	  int mouseY, boolean isHovered, float delta
	) {
		super.render(
		  matrices, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
		if (this.savedWidth != entryWidth || this.savedX != x || this.savedY != y) {
			this.wrappedLines =
			  this.textRenderer.trimStringToWidth(this.text, entryWidth);
			this.savedWidth = entryWidth;
			this.savedX = x;
			this.savedY = y;
		}
		int yy = y + 4;
		for (IReorderingProcessor string : this.wrappedLines) {
			Minecraft.getInstance().fontRenderer.func_238407_a_(
			  matrices, string, (float) x, (float) yy, this.color);
			Objects.requireNonNull(Minecraft.getInstance().fontRenderer);
			yy += 9 + 3;
		}
		Style style = this.getTextAt(mouseX, mouseY);
		AbstractConfigScreen configScreen = this.getConfigScreen();
		if (style != null && configScreen != null) {
			configScreen.renderComponentHoverEffect(matrices, style, mouseX, mouseY);
		}
	}
	
	@Override
	public int getItemHeight() {
		if (this.savedWidth == -1) {
			return 12;
		}
		int lineCount = this.wrappedLines.size();
		return lineCount == 0 ? 0 : 15 + lineCount * 12;
	}
	
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			Style style = this.getTextAt(mouseX, mouseY);
			AbstractConfigScreen configScreen = this.getConfigScreen();
			if (configScreen != null && configScreen.handleComponentClicked(style)) {
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	@Nullable
	private Style getTextAt(double x, double y) {
		int lineCount = this.wrappedLines.size();
		if (lineCount > 0) {
			int line;
			int textX = MathHelper.floor(x - (double) this.savedX);
			int textY = MathHelper.floor(y - 4.0 - (double) this.savedY);
			if (textX >= 0 && textY >= 0 && textX <= this.savedWidth &&
			    textY < 12 * lineCount + lineCount && (line = textY / 12) < this.wrappedLines.size()) {
				IReorderingProcessor orderedText = this.wrappedLines.get(line);
				return this.textRenderer.getCharacterManager().func_243239_a(orderedText, textX);
			}
		}
		return null;
	}
	
	@Override
	public void save() {
	}
	
	@Override public Void getValue() {
		return null;
	}
	
	@Override public void setValue(Void value) {}
	
	@Override
	public Optional<Void> getDefaultValue() {
		return Optional.empty();
	}
	
	public @NotNull List<? extends IGuiEventListener> getEventListeners() {
		return Collections.emptyList();
	}
}

