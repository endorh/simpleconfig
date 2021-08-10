package endorh.simple_config.clothconfig2.gui.entries;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class FloatListEntry extends TextFieldListEntry<Float> {
   private static final Function<String, String> stripCharacters = (s) -> {
      StringBuilder stringBuilder_1 = new StringBuilder();
      char[] var2 = s.toCharArray();
      int var3 = var2.length;
      char[] var4 = var2;
      int var5 = var2.length;

      for(int var6 = 0; var6 < var5; ++var6) {
         char c = var4[var6];
         if (Character.isDigit(c) || c == '-' || c == '.') {
            stringBuilder_1.append(c);
         }
      }

      return stringBuilder_1.toString();
   };
   private float minimum;
   private float maximum;
   private final Consumer<Float> saveConsumer;

   /** @deprecated */
   @Deprecated
   @Internal
   public FloatListEntry(ITextComponent fieldName, Float value, ITextComponent resetButtonKey, Supplier<Float> defaultValue, Consumer<Float> saveConsumer) {
      super(fieldName, value, resetButtonKey, defaultValue);
      this.minimum = -3.4028235E38F;
      this.maximum = Float.MAX_VALUE;
      this.saveConsumer = saveConsumer;
   }

   /** @deprecated */
   @Deprecated
   @Internal
   public FloatListEntry(ITextComponent fieldName, Float value, ITextComponent resetButtonKey, Supplier<Float> defaultValue, Consumer<Float> saveConsumer, Supplier<Optional<ITextComponent[]>> tooltipSupplier) {
      this(fieldName, value, resetButtonKey, defaultValue, saveConsumer, tooltipSupplier, false);
   }

   /** @deprecated */
   @Deprecated
   @Internal
   public FloatListEntry(ITextComponent fieldName, Float value, ITextComponent resetButtonKey, Supplier<Float> defaultValue, Consumer<Float> saveConsumer, Supplier<Optional<ITextComponent[]>> tooltipSupplier, boolean requiresRestart) {
      super(fieldName, value, resetButtonKey, defaultValue, tooltipSupplier, requiresRestart);
      this.minimum = -3.4028235E38F;
      this.maximum = Float.MAX_VALUE;
      this.saveConsumer = saveConsumer;
   }

   protected String stripAddText(String s) {
      return stripCharacters.apply(s);
   }

   protected void textFieldPreRender(TextFieldWidget widget) {
      try {
         double i = Float.parseFloat(this.textFieldWidget.getText());
         if (!(i < (double)this.minimum) && !(i > (double)this.maximum)) {
            widget.setTextColor(14737632);
         } else {
            widget.setTextColor(16733525);
         }
      } catch (NumberFormatException var4) {
         widget.setTextColor(16733525);
      }

   }

   protected boolean isMatchDefault(String text) {
      return this.getDefaultValue().isPresent() && text.equals(this.defaultValue.get().toString());
   }

   public FloatListEntry setMinimum(float minimum) {
      this.minimum = minimum;
      return this;
   }

   public FloatListEntry setMaximum(float maximum) {
      this.maximum = maximum;
      return this;
   }

   public void save() {
      if (this.saveConsumer != null) {
         this.saveConsumer.accept(this.getValue());
      }

   }

   public Float getValue() {
      try {
         return Float.valueOf(this.textFieldWidget.getText());
      } catch (Exception var2) {
         return 0.0F;
      }
   }

   public Optional<ITextComponent> getError() {
      try {
         float i = Float.parseFloat(this.textFieldWidget.getText());
         if (i > this.maximum) {
            return Optional.of(new TranslationTextComponent("text.cloth-config.error.too_large", new Object[]{this.maximum}));
         }

         if (i < this.minimum) {
            return Optional.of(new TranslationTextComponent("text.cloth-config.error.too_small", new Object[]{this.minimum}));
         }
      } catch (NumberFormatException var2) {
         return Optional.of(new TranslationTextComponent("text.cloth-config.error.not_valid_number_float"));
      }

      return super.getError();
   }
}
