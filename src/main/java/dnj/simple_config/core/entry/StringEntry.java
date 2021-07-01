package dnj.simple_config.core.entry;

import dnj.simple_config.core.AbstractConfigEntry;
import dnj.simple_config.core.ISimpleConfigEntryHolder;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.TextFieldBuilder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import java.util.Optional;

public class StringEntry
  extends AbstractConfigEntry<String, String, String, StringEntry> {
	public StringEntry(String value) {super(value);}
	
	@Override
	protected Optional<ConfigValue<?>> buildConfigEntry(Builder builder) {
		return Optional.of(decorate(builder).define(name, value));
	}
	
	@OnlyIn(Dist.CLIENT)
	@Override
	protected Optional<AbstractConfigListEntry<?>> buildGUIEntry(
	  ConfigEntryBuilder builder, ISimpleConfigEntryHolder c
	) {
		final TextFieldBuilder valBuilder = builder
		  .startTextField(getDisplayName(), c.get(name))
		  .setDefaultValue(value)
		  .setSaveConsumer(saveConsumer(c))
		  .setTooltipSupplier(this::supplyTooltip)
		  .setErrorSupplier(this::supplyError);
		return Optional.of(decorate(valBuilder).build());
	}
}
