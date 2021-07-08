package endorh.simple_config.core.entry;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import endorh.simple_config.core.ISimpleConfigEntryHolder;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class INBTEntry extends AbstractSerializableEntry<INBT, INBTEntry> {
	@Internal public INBTEntry(ISimpleConfigEntryHolder parent, String name, INBT value) {
		super(parent, name, value, INBT.class);
	}
	
	public static class Builder extends AbstractSerializableEntry.Builder<INBT, INBTEntry, Builder> {
		public Builder(INBT value) {
			super(value, INBT.class);
		}
		
		@Override
		protected INBTEntry buildEntry(ISimpleConfigEntryHolder parent, String name) {
			return new INBTEntry(parent, name, value);
		}
	}
	
	@Override
	protected String serialize(INBT value) {
		return value.toFormattedComponent().getString();
	}
	
	@Override
	protected @Nullable INBT deserialize(String value) {
		try {
			return new JsonToNBT(new StringReader(value)).readValue();
		} catch (CommandSyntaxException ignored) {
			return null;
		}
	}
	
	@Override
	protected Optional<ITextComponent> getErrorMessage(String value) {
		return Optional.of(new TranslationTextComponent(
		  "simple-config.config.error.invalid_nbt"));
	}
}
