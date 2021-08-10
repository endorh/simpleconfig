package endorh.simple_config.clothconfig2.gui.entries;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import endorh.simple_config.clothconfig2.ClothConfigInitializer;
import endorh.simple_config.clothconfig2.api.ScissorsHandler;
import endorh.simple_config.clothconfig2.api.ScrollingContainer;
import endorh.simple_config.clothconfig2.math.Rectangle;
import endorh.simple_config.clothconfig2.math.impl.PointHelper;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class DropdownBoxEntry<T> extends TooltipListEntry<T> {
   protected Button resetButton;
   protected DropdownBoxEntry.SelectionElement<T> selectionElement;
   @NotNull
   private final Supplier<T> defaultValue;
   @Nullable
   private final Consumer<T> saveConsumer;
   private boolean suggestionMode = true;

   /** @deprecated */
   @Deprecated
   @Internal
   public DropdownBoxEntry(ITextComponent fieldName, @NotNull ITextComponent resetButtonKey, @Nullable Supplier<Optional<ITextComponent[]>> tooltipSupplier, boolean requiresRestart, @Nullable Supplier<T> defaultValue, @Nullable Consumer<T> saveConsumer, @Nullable Iterable<T> selections, @NotNull DropdownBoxEntry.SelectionTopCellElement<T> topRenderer, @NotNull DropdownBoxEntry.SelectionCellCreator<T> cellCreator) {
      super(fieldName, tooltipSupplier, requiresRestart);
      this.defaultValue = defaultValue;
      this.saveConsumer = saveConsumer;
      this.resetButton = new Button(0, 0, Minecraft.getInstance().fontRenderer.getStringPropertyWidth(resetButtonKey) + 6, 20, resetButtonKey, (widget) -> {
         this.selectionElement.topRenderer.setValue(defaultValue.get());
      });
      this.selectionElement = new DropdownBoxEntry.SelectionElement(this, new Rectangle(0, 0, 150, 20), new DropdownBoxEntry.DefaultDropdownMenuElement(selections == null ? ImmutableList.of() : ImmutableList.copyOf(selections)), topRenderer, cellCreator);
   }

   public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
      super.render(matrices, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
      MainWindow window = Minecraft.getInstance().getMainWindow();
      this.resetButton.active = this.isEditable() && this.getDefaultValue().isPresent() && (!this.defaultValue.get().equals(this.getValue()) || this.getConfigError().isPresent());
      this.resetButton.y = y;
      this.selectionElement.active = this.isEditable();
      this.selectionElement.bounds.y = y;
      ITextComponent displayedFieldName = this.getDisplayedFieldName();
      if (Minecraft.getInstance().fontRenderer.getBidiFlag()) {
         Minecraft.getInstance().fontRenderer.func_238407_a_(matrices, displayedFieldName.func_241878_f(), (float)(window.getScaledWidth() - x - Minecraft.getInstance().fontRenderer.getStringPropertyWidth(displayedFieldName)), (float)(y + 6), this.getPreferredTextColor());
         this.resetButton.x = x;
         this.selectionElement.bounds.x = x + this.resetButton.getWidth() + 1;
      } else {
         Minecraft.getInstance().fontRenderer.func_238407_a_(matrices, displayedFieldName.func_241878_f(), (float)x, (float)(y + 6), this.getPreferredTextColor());
         this.resetButton.x = x + entryWidth - this.resetButton.getWidth();
         this.selectionElement.bounds.x = x + entryWidth - 150 + 1;
      }

      this.selectionElement.bounds.width = 150 - this.resetButton.getWidth() - 4;
      this.resetButton.render(matrices, mouseX, mouseY, delta);
      this.selectionElement.render(matrices, mouseX, mouseY, delta);
   }

   public boolean isEdited() {
      return this.selectionElement.topRenderer.isEdited();
   }

   public boolean isSuggestionMode() {
      return this.suggestionMode;
   }

   public void setSuggestionMode(boolean suggestionMode) {
      this.suggestionMode = suggestionMode;
   }

   public void updateSelected(boolean isSelected) {
      this.selectionElement.topRenderer.isSelected = isSelected;
      this.selectionElement.menu.isSelected = isSelected;
   }

   @NotNull
   public ImmutableList<T> getSelections() {
      return this.selectionElement.menu.getSelections();
   }

   public T getValue() {
      return this.selectionElement.getValue();
   }

   /** @deprecated */
   @Deprecated
   public DropdownBoxEntry.SelectionElement<T> getSelectionElement() {
      return this.selectionElement;
   }

   public Optional<T> getDefaultValue() {
      return this.defaultValue == null ? Optional.empty() : Optional.ofNullable(this.defaultValue.get());
   }

   public void save() {
      if (this.saveConsumer != null) {
         this.saveConsumer.accept(this.getValue());
      }

   }

   public @NotNull List<? extends IGuiEventListener> getEventListeners() {
      return Lists.newArrayList(this.selectionElement, this.resetButton);
   }

   public Optional<ITextComponent> getError() {
      return this.selectionElement.topRenderer.getError();
   }

   public void lateRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
      this.selectionElement.lateRender(matrices, mouseX, mouseY, delta);
   }

   public int getMorePossibleHeight() {
      return this.selectionElement.getMorePossibleHeight();
   }

   public boolean mouseScrolled(double double_1, double double_2, double double_3) {
      return this.selectionElement.mouseScrolled(double_1, double_2, double_3);
   }

   public static class DefaultSelectionTopCellElement<R> extends DropdownBoxEntry.SelectionTopCellElement<R> {
      protected TextFieldWidget textFieldWidget;
      protected Function<String, R> toObjectFunction;
      protected Function<R, ITextComponent> toTextFunction;
      protected final R original;
      protected R value;

      public DefaultSelectionTopCellElement(R value, Function<String, R> toObjectFunction, Function<R, ITextComponent> toTextFunction) {
         this.original = Objects.requireNonNull(value);
         this.value = Objects.requireNonNull(value);
         this.toObjectFunction = Objects.requireNonNull(toObjectFunction);
         this.toTextFunction = Objects.requireNonNull(toTextFunction);
         this.textFieldWidget = new TextFieldWidget(Minecraft.getInstance().fontRenderer, 0, 0, 148, 18, NarratorChatListener.EMPTY) {
            public void render(@NotNull MatrixStack matrices, int mouseX, int mouseY, float delta) {
               this.setFocused(DefaultSelectionTopCellElement.this.isSuggestionMode() && DefaultSelectionTopCellElement.this.isSelected && DefaultSelectionTopCellElement.this.getParent().getListener() == DefaultSelectionTopCellElement.this.getParent().selectionElement && DefaultSelectionTopCellElement.this.getParent().selectionElement.getListener() == DefaultSelectionTopCellElement.this && DefaultSelectionTopCellElement.this.getListener() == this);
               super.render(matrices, mouseX, mouseY, delta);
            }

            public boolean keyPressed(int int_1, int int_2, int int_3) {
               if (int_1 != 257 && int_1 != 335) {
                  return DefaultSelectionTopCellElement.this.isSuggestionMode() && super.keyPressed(int_1, int_2, int_3);
               } else {
                  DefaultSelectionTopCellElement.this.selectFirstRecommendation();
                  return true;
               }
            }

            public boolean charTyped(char chr, int keyCode) {
               return DefaultSelectionTopCellElement.this.isSuggestionMode() && super.charTyped(chr, keyCode);
            }
         };
         this.textFieldWidget.setEnableBackgroundDrawing(false);
         this.textFieldWidget.setMaxStringLength(999999);
         this.textFieldWidget.setText(toTextFunction.apply(value).getString());
      }

      public boolean isEdited() {
         return super.isEdited() || !this.getValue().equals(this.original);
      }

      public void render(MatrixStack matrices, int mouseX, int mouseY, int x, int y, int width, int height, float delta) {
         this.textFieldWidget.x = x + 4;
         this.textFieldWidget.y = y + 6;
         this.textFieldWidget.setWidth(width - 8);
         this.textFieldWidget.setEnabled(this.getParent().isEditable());
         this.textFieldWidget.setTextColor(this.getPreferredTextColor());
         this.textFieldWidget.render(matrices, mouseX, mouseY, delta);
      }

      public R getValue() {
         return this.hasError() ? this.value : this.toObjectFunction.apply(this.textFieldWidget.getText());
      }

      public void setValue(R value) {
         this.textFieldWidget.setText(this.toTextFunction.apply(value).getString());
         this.textFieldWidget.setCursorPosition(0);
      }

      public ITextComponent getSearchTerm() {
         return new StringTextComponent(this.textFieldWidget.getText());
      }

      public Optional<ITextComponent> getError() {
         return this.toObjectFunction.apply(this.textFieldWidget.getText()) != null ? Optional.empty() : Optional.of(new StringTextComponent("Invalid Value!"));
      }

      public @NotNull List<? extends IGuiEventListener> getEventListeners() {
         return Collections.singletonList(this.textFieldWidget);
      }
   }

   public abstract static class SelectionTopCellElement<R> extends FocusableGui {
      /** @deprecated */
      @Deprecated
      private DropdownBoxEntry<R> entry;
      protected boolean isSelected = false;

      public abstract R getValue();

      public abstract void setValue(R var1);

      public abstract ITextComponent getSearchTerm();

      public boolean isEdited() {
         return this.getConfigError().isPresent();
      }

      public abstract Optional<ITextComponent> getError();

      public final Optional<ITextComponent> getConfigError() {
         return this.entry.getConfigError();
      }

      public DropdownBoxEntry<R> getParent() {
         return this.entry;
      }

      public final boolean hasConfigError() {
         return this.getConfigError().isPresent();
      }

      public final boolean hasError() {
         return this.getError().isPresent();
      }

      public final int getPreferredTextColor() {
         return this.getConfigError().isPresent() ? 16733525 : 16777215;
      }

      public final boolean isSuggestionMode() {
         return this.getParent().isSuggestionMode();
      }

      public void selectFirstRecommendation() {
         List<DropdownBoxEntry.SelectionCellElement<R>> children = this.getParent().selectionElement.menu.getEventListeners();
         Iterator var2 = children.iterator();

         while(var2.hasNext()) {
            DropdownBoxEntry.SelectionCellElement<R> child = (DropdownBoxEntry.SelectionCellElement)var2.next();
            if (child.getSelection() != null) {
               this.setValue(child.getSelection());
               this.getParent().selectionElement.setListener(null);
               break;
            }
         }

      }

      public abstract void render(MatrixStack var1, int var2, int var3, int var4, int var5, int var6, int var7, float var8);
   }

   public static class DefaultSelectionCellElement<R> extends DropdownBoxEntry.SelectionCellElement<R> {
      protected R r;
      protected int x;
      protected int y;
      protected int width;
      protected int height;
      protected boolean rendering;
      protected Function<R, ITextComponent> toTextFunction;

      public DefaultSelectionCellElement(R r, Function<R, ITextComponent> toTextFunction) {
         this.r = r;
         this.toTextFunction = toTextFunction;
      }

      public void render(MatrixStack matrices, int mouseX, int mouseY, int x, int y, int width, int height, float delta) {
         this.rendering = true;
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         boolean b = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
         if (b) {
            fill(matrices, x + 1, y + 1, x + width - 1, y + height - 1, -15132391);
         }

         Minecraft.getInstance().fontRenderer.func_238407_a_(matrices, this.toTextFunction.apply(this.r)
           .func_241878_f(), (float)(x + 6), (float)(y + 3), b ? 16777215 : 8947848);
      }

      public void dontRender(MatrixStack matrices, float delta) {
         this.rendering = false;
      }

      @Nullable
      public ITextComponent getSearchKey() {
         return this.toTextFunction.apply(this.r);
      }

      @Nullable
      public R getSelection() {
         return this.r;
      }

      public @NotNull List<? extends IGuiEventListener> getEventListeners() {
         return Collections.emptyList();
      }

      public boolean mouseClicked(double mouseX, double mouseY, int int_1) {
         boolean b = this.rendering && mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
         if (b) {
            this.getEntry().selectionElement.topRenderer.setValue(this.r);
            this.getEntry().selectionElement.setListener(null);
            this.getEntry().selectionElement.dontReFocus = true;
            return true;
         } else {
            return false;
         }
      }
   }

   public abstract static class SelectionCellElement<R> extends FocusableGui {
      /** @deprecated */
      @Deprecated
      @NotNull
      private DropdownBoxEntry<R> entry;

      @NotNull
      public final DropdownBoxEntry<R> getEntry() {
         return this.entry;
      }

      public abstract void render(MatrixStack var1, int var2, int var3, int var4, int var5, int var6, int var7, float var8);

      public abstract void dontRender(MatrixStack var1, float var2);

      @Nullable
      public abstract ITextComponent getSearchKey();

      @Nullable
      public abstract R getSelection();
   }

   public static class DefaultSelectionCellCreator<R> extends DropdownBoxEntry.SelectionCellCreator<R> {
      protected Function<R, ITextComponent> toTextFunction;

      public DefaultSelectionCellCreator(Function<R, ITextComponent> toTextFunction) {
         this.toTextFunction = toTextFunction;
      }

      public DefaultSelectionCellCreator() {
         this((r) -> {
            return new StringTextComponent(r.toString());
         });
      }

      public DropdownBoxEntry.SelectionCellElement<R> create(R selection) {
         return new DropdownBoxEntry.DefaultSelectionCellElement(selection, this.toTextFunction);
      }

      public int getCellHeight() {
         return 14;
      }

      public int getDropBoxMaxHeight() {
         return this.getCellHeight() * 7;
      }
   }

   public abstract static class SelectionCellCreator<R> {
      public abstract DropdownBoxEntry.SelectionCellElement<R> create(R var1);

      public abstract int getCellHeight();

      public abstract int getDropBoxMaxHeight();

      public int getCellWidth() {
         return 132;
      }
   }

   public static class DefaultDropdownMenuElement<R> extends DropdownBoxEntry.DropdownMenuElement<R> {
      @NotNull
      protected ImmutableList<R> selections;
      @NotNull
      protected List<DropdownBoxEntry.SelectionCellElement<R>> cells;
      @NotNull
      protected List<DropdownBoxEntry.SelectionCellElement<R>> currentElements;
      protected ITextComponent lastSearchKeyword;
      protected Rectangle lastRectangle;
      protected boolean scrolling;
      protected double scroll;
      protected double target;
      protected long start;
      protected long duration;

      public DefaultDropdownMenuElement(@NotNull ImmutableList<R> selections) {
         this.lastSearchKeyword = NarratorChatListener.EMPTY;
         this.selections = selections;
         this.cells = Lists.newArrayList();
         this.currentElements = Lists.newArrayList();
      }

      public double getMaxScroll() {
         return this.getCellCreator().getCellHeight() * this.currentElements.size();
      }

      protected double getMaxScrollPosition() {
         return Math.max(0.0D, this.getMaxScroll() - (double)this.getHeight());
      }

      @NotNull
      public ImmutableList<R> getSelections() {
         return this.selections;
      }

      public void initCells() {
         for (R selection : this.getSelections())
            this.cells.add(this.getCellCreator().create(selection));
         for (SelectionCellElement<R> cell : this.cells)
            cell.entry = this.getEntry();
         this.search();
      }

      public void search() {
         if (this.isSuggestionMode()) {
            this.currentElements.clear();
            String keyword = this.lastSearchKeyword.getString().toLowerCase();
            Iterator<DropdownBoxEntry.SelectionCellElement<R>> var2 = this.cells.iterator();

            while(true) {
               DropdownBoxEntry.SelectionCellElement<R> cell;
               ITextComponent key;
               do {
                  if (!var2.hasNext()) {
                     if (!keyword.isEmpty()) {
                        Comparator<DropdownBoxEntry.SelectionCellElement<?>> c = Comparator.comparingDouble((i) -> {
                           return i.getSearchKey() == null ? Double.MAX_VALUE : this.similarity(i.getSearchKey().getString(), keyword);
                        });
                        this.currentElements.sort(c.reversed());
                     }

                     this.scrollTo(0.0D, false);
                     return;
                  }
                  
                  cell = var2.next();
                  key = cell.getSearchKey();
               } while(key != null && !key.getString().toLowerCase().contains(keyword));

               this.currentElements.add(cell);
            }
         } else {
            this.currentElements.clear();
            this.currentElements.addAll(this.cells);
         }
      }

      protected int editDistance(String s1, String s2) {
         s1 = s1.toLowerCase();
         s2 = s2.toLowerCase();
         int[] costs = new int[s2.length() + 1];

         for(int i = 0; i <= s1.length(); ++i) {
            int lastValue = i;

            for(int j = 0; j <= s2.length(); ++j) {
               if (i == 0) {
                  costs[j] = j;
               } else if (j > 0) {
                  int newValue = costs[j - 1];
                  if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                     newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                  }

                  costs[j - 1] = lastValue;
                  lastValue = newValue;
               }
            }

            if (i > 0) {
               costs[s2.length()] = lastValue;
            }
         }

         return costs[s2.length()];
      }

      protected double similarity(String s1, String s2) {
         String longer = s1;
         String shorter = s2;
         if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
         }

         int longerLength = longer.length();
         return longerLength == 0 ? 1.0D : (double)(longerLength - this.editDistance(longer, shorter)) / (double)longerLength;
      }

      public void render(MatrixStack matrices, int mouseX, int mouseY, Rectangle rectangle, float delta) {
         if (!this.getEntry().selectionElement.topRenderer.getSearchTerm().equals(this.lastSearchKeyword)) {
            this.lastSearchKeyword = this.getEntry().selectionElement.topRenderer.getSearchTerm();
            this.search();
         }

         this.updatePosition(delta);
         this.lastRectangle = rectangle.clone();
         this.lastRectangle.translate(0, -1);
      }

      private void updatePosition(float delta) {
         double[] target = new double[]{this.target};
         this.scroll = ScrollingContainer.handleScrollingPosition(target, this.scroll, this.getMaxScrollPosition(), delta, (double)this.start, (double)this.duration);
         this.target = target[0];
      }

      public void lateRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
         int last10Height = this.getHeight();
         int cWidth = this.getCellCreator().getCellWidth();
         fill(matrices, this.lastRectangle.x, this.lastRectangle.y + this.lastRectangle.height, this.lastRectangle.x + cWidth, this.lastRectangle.y + this.lastRectangle.height + last10Height + 1, this.isExpanded() ? -1 : -6250336);
         fill(matrices, this.lastRectangle.x + 1, this.lastRectangle.y + this.lastRectangle.height + 1, this.lastRectangle.x + cWidth - 1, this.lastRectangle.y + this.lastRectangle.height + last10Height, -16777216);
         RenderSystem.pushMatrix();
         RenderSystem.translatef(0.0F, 0.0F, 300.0F);
         ScissorsHandler.INSTANCE.scissor(new Rectangle(this.lastRectangle.x, this.lastRectangle.y + this.lastRectangle.height + 1, cWidth - 6, last10Height - 1));
         double yy = (double)(this.lastRectangle.y + this.lastRectangle.height) - this.scroll;

         for(Iterator var9 = this.currentElements.iterator(); var9.hasNext(); yy +=
           this.getCellCreator().getCellHeight()) {
            DropdownBoxEntry.SelectionCellElement<R> cell = (DropdownBoxEntry.SelectionCellElement)var9.next();
            if (yy + (double)this.getCellCreator().getCellHeight() >= (double)(this.lastRectangle.y + this.lastRectangle.height) && yy <= (double)(this.lastRectangle.y + this.lastRectangle.height + last10Height + 1)) {
               cell.render(matrices, mouseX, mouseY, this.lastRectangle.x, (int)yy, this.getMaxScrollPosition() > 6.0D ? this.getCellCreator().getCellWidth() - 6 : this.getCellCreator().getCellWidth(), this.getCellCreator().getCellHeight(), delta);
            } else {
               cell.dontRender(matrices, delta);
            }
         }

         ScissorsHandler.INSTANCE.removeLastScissor();
         if (this.currentElements.isEmpty()) {
            FontRenderer textRenderer = Minecraft.getInstance().fontRenderer;
            ITextComponent text = new TranslationTextComponent("text.cloth-config.dropdown.value.unknown");
            textRenderer.func_238407_a_(matrices, text.func_241878_f(), (float)this.lastRectangle.x + (float)this.getCellCreator().getCellWidth() / 2.0F - (float)textRenderer.getStringPropertyWidth(text) / 2.0F, (float)(this.lastRectangle.y + this.lastRectangle.height + 3), -1);
         }

         if (this.getMaxScrollPosition() > 6.0D) {
            RenderSystem.disableTexture();
            int scrollbarPositionMinX = this.lastRectangle.x + this.getCellCreator().getCellWidth() - 6;
            int scrollbarPositionMaxX = scrollbarPositionMinX + 6;
            int height = (int)((double)(last10Height * last10Height) / this.getMaxScrollPosition());
            height = MathHelper.clamp(height, 32, last10Height - 8);
            height = (int)((double)height - Math.min(this.scroll < 0.0D ? (double)((int)(-this.scroll)) : (this.scroll > this.getMaxScrollPosition() ? (double)((int)this.scroll) - this.getMaxScrollPosition() : 0.0D), (double)height * 0.95D));
            height = Math.max(10, height);
            int minY = (int)Math.min(Math.max((double)((int)this.scroll * (last10Height - height)) / this.getMaxScrollPosition() + (double)(this.lastRectangle.y + this.lastRectangle.height + 1),
                                              this.lastRectangle.y + this.lastRectangle.height + 1),
                                     this.lastRectangle.y + this.lastRectangle.height + 1 + last10Height - height);
            int bottomc = (new Rectangle(scrollbarPositionMinX, minY, scrollbarPositionMaxX - scrollbarPositionMinX, height)).contains(PointHelper.ofMouse()) ? 168 : 128;
            int topc = (new Rectangle(scrollbarPositionMinX, minY, scrollbarPositionMaxX - scrollbarPositionMinX, height)).contains(PointHelper.ofMouse()) ? 222 : 172;
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            buffer.pos(scrollbarPositionMinX, minY + height, 0.0D).tex(0.0F, 1.0F).color(bottomc, bottomc, bottomc, 255).endVertex();
            buffer.pos(scrollbarPositionMaxX, minY + height, 0.0D).tex(1.0F, 1.0F).color(bottomc, bottomc, bottomc, 255).endVertex();
            buffer.pos(scrollbarPositionMaxX, minY, 0.0D).tex(1.0F, 0.0F).color(bottomc, bottomc, bottomc, 255).endVertex();
            buffer.pos(scrollbarPositionMinX, minY, 0.0D).tex(0.0F, 0.0F).color(bottomc, bottomc, bottomc, 255).endVertex();
            tessellator.draw();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            buffer.pos(scrollbarPositionMinX, minY + height - 1, 0.0D).tex(0.0F, 1.0F).color(topc, topc, topc, 255).endVertex();
            buffer.pos(scrollbarPositionMaxX - 1, minY + height - 1, 0.0D).tex(1.0F, 1.0F).color(topc, topc, topc, 255).endVertex();
            buffer.pos(scrollbarPositionMaxX - 1, minY, 0.0D).tex(1.0F, 0.0F).color(topc, topc, topc, 255).endVertex();
            buffer.pos(scrollbarPositionMinX, minY, 0.0D).tex(0.0F, 0.0F).color(topc, topc, topc, 255).endVertex();
            tessellator.draw();
            RenderSystem.enableTexture();
         }

         RenderSystem.translatef(0.0F, 0.0F, -300.0F);
         RenderSystem.popMatrix();
      }

      public int getHeight() {
         return Math.max(Math.min(this.getCellCreator().getDropBoxMaxHeight(), (int)this.getMaxScroll()), 14);
      }

      public boolean isMouseOver(double mouseX, double mouseY) {
         return this.isExpanded() && mouseX >= (double)this.lastRectangle.x && mouseX <= (double)(this.lastRectangle.x + this.getCellCreator().getCellWidth()) && mouseY >= (double)(this.lastRectangle.y + this.lastRectangle.height) && mouseY <= (double)(this.lastRectangle.y + this.lastRectangle.height + this.getHeight() + 1);
      }

      public boolean mouseDragged(double double_1, double double_2, int int_1, double double_3, double double_4) {
         if (!this.isExpanded()) {
            return false;
         } else if (int_1 == 0 && this.scrolling) {
            if (double_2 < (double)this.lastRectangle.y + (double)this.lastRectangle.height) {
               this.scrollTo(0.0D, false);
            } else if (double_2 > (double)this.lastRectangle.y + (double)this.lastRectangle.height + (double)this.getHeight()) {
               this.scrollTo(this.getMaxScrollPosition(), false);
            } else {
               double double_5 = Math.max(1.0D, this.getMaxScrollPosition());
               int int_2 = this.getHeight();
               int int_3 = MathHelper.clamp((int)((float)(int_2 * int_2) / (float)this.getMaxScrollPosition()), 32, int_2 - 8);
               double double_6 = Math.max(1.0D, double_5 / (double)(int_2 - int_3));
               this.offset(double_4 * double_6, false);
            }

            this.target = MathHelper.clamp(this.target, 0.0D, this.getMaxScrollPosition());
            return true;
         } else {
            return false;
         }
      }

      public boolean mouseScrolled(double mouseX, double mouseY, double double_3) {
         if (this.isMouseOver(mouseX, mouseY)) {
            this.offset(ClothConfigInitializer.getScrollStep() * -double_3, true);
            return true;
         } else {
            return false;
         }
      }

      protected void updateScrollingState(double double_1, double double_2, int int_1) {
         this.scrolling = this.isExpanded() && this.lastRectangle != null && int_1 == 0 && double_1 >= (double)this.lastRectangle.x + (double)this.getCellCreator().getCellWidth() - 6.0D && double_1 < (double)(this.lastRectangle.x + this.getCellCreator().getCellWidth());
      }

      public boolean mouseClicked(double double_1, double double_2, int int_1) {
         if (!this.isExpanded()) {
            return false;
         } else {
            this.updateScrollingState(double_1, double_2, int_1);
            return super.mouseClicked(double_1, double_2, int_1) || this.scrolling;
         }
      }

      public void offset(double value, boolean animated) {
         this.scrollTo(this.target + value, animated);
      }

      public void scrollTo(double value, boolean animated) {
         this.scrollTo(value, animated, ClothConfigInitializer.getScrollDuration());
      }

      public void scrollTo(double value, boolean animated, long duration) {
         this.target = ScrollingContainer.clampExtension(value, this.getMaxScrollPosition());
         if (animated) {
            this.start = System.currentTimeMillis();
            this.duration = duration;
         } else {
            this.scroll = this.target;
         }

      }

      public List<DropdownBoxEntry.SelectionCellElement<R>> getEventListeners() {
         return this.currentElements;
      }
   }

   public abstract static class DropdownMenuElement<R> extends FocusableGui {
      /** @deprecated */
      @Deprecated
      @NotNull
      private DropdownBoxEntry.SelectionCellCreator<R> cellCreator;
      /** @deprecated */
      @Deprecated
      @NotNull
      private DropdownBoxEntry<R> entry;
      private boolean isSelected;

      @NotNull
      public DropdownBoxEntry.SelectionCellCreator<R> getCellCreator() {
         return this.cellCreator;
      }

      @NotNull
      public final DropdownBoxEntry<R> getEntry() {
         return this.entry;
      }

      @NotNull
      public abstract ImmutableList<R> getSelections();

      public abstract void initCells();

      public abstract void render(MatrixStack var1, int var2, int var3, Rectangle var4, float var5);

      public abstract void lateRender(MatrixStack var1, int var2, int var3, float var4);

      public abstract int getHeight();

      public final boolean isExpanded() {
         return this.isSelected && this.getEntry().getListener() == this.getEntry().selectionElement;
      }

      public final boolean isSuggestionMode() {
         return this.entry.isSuggestionMode();
      }

      public abstract @NotNull List<DropdownBoxEntry.SelectionCellElement<R>> getEventListeners();
   }

   public static class SelectionElement<R> extends FocusableGui implements IRenderable {
      protected Rectangle bounds;
      protected boolean active;
      protected DropdownBoxEntry.SelectionTopCellElement<R> topRenderer;
      protected DropdownBoxEntry<R> entry;
      protected DropdownBoxEntry.DropdownMenuElement<R> menu;
      protected boolean dontReFocus = false;

      public SelectionElement(DropdownBoxEntry<R> entry, Rectangle bounds, DropdownBoxEntry.DropdownMenuElement<R> menu, DropdownBoxEntry.SelectionTopCellElement<R> topRenderer, DropdownBoxEntry.SelectionCellCreator<R> cellCreator) {
         this.bounds = bounds;
         this.entry = entry;
         this.menu = Objects.requireNonNull(menu);
         this.menu.entry = entry;
         this.menu.cellCreator = Objects.requireNonNull(cellCreator);
         this.menu.initCells();
         this.topRenderer = Objects.requireNonNull(topRenderer);
         this.topRenderer.entry = entry;
      }

      public void render(@NotNull MatrixStack matrices, int mouseX, int mouseY, float delta) {
         fill(matrices, this.bounds.x, this.bounds.y, this.bounds.x + this.bounds.width, this.bounds.y + this.bounds.height, this.topRenderer.isSelected ? -1 : -6250336);
         fill(matrices, this.bounds.x + 1, this.bounds.y + 1, this.bounds.x + this.bounds.width - 1, this.bounds.y + this.bounds.height - 1, -16777216);
         this.topRenderer.render(matrices, mouseX, mouseY, this.bounds.x, this.bounds.y, this.bounds.width, this.bounds.height, delta);
         if (this.menu.isExpanded()) {
            this.menu.render(matrices, mouseX, mouseY, this.bounds, delta);
         }

      }

      /** @deprecated */
      @Deprecated
      public DropdownBoxEntry.SelectionTopCellElement<R> getTopRenderer() {
         return this.topRenderer;
      }

      public boolean mouseScrolled(double double_1, double double_2, double double_3) {
         return this.menu.isExpanded() ? this.menu.mouseScrolled(double_1, double_2, double_3) : false;
      }

      public void lateRender(MatrixStack matrices, int mouseX, int mouseY, float delta) {
         if (this.menu.isExpanded()) {
            this.menu.lateRender(matrices, mouseX, mouseY, delta);
         }

      }

      public int getMorePossibleHeight() {
         return this.menu.isExpanded() ? this.menu.getHeight() : -1;
      }

      public R getValue() {
         return this.topRenderer.getValue();
      }

      public @NotNull List<? extends IGuiEventListener> getEventListeners() {
         return Lists.newArrayList(this.topRenderer, this.menu);
      }

      public boolean mouseClicked(double double_1, double double_2, int int_1) {
         this.dontReFocus = false;
         boolean b = super.mouseClicked(double_1, double_2, int_1);
         if (this.dontReFocus) {
            this.setListener(null);
            this.dontReFocus = false;
         }

         return b;
      }
   }
}
