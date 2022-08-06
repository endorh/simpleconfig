package endorh.simpleconfig.core.commands;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import endorh.simpleconfig.core.AbstractConfigEntry;
import endorh.simpleconfig.core.SimpleConfig;
import endorh.simpleconfig.yaml.SimpleConfigCommentedYamlFormat;
import net.minecraft.command.arguments.IArgumentSerializer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.concurrent.CompletableFuture;

public class SimpleConfigValueArgumentType implements ArgumentType<String> {
	private static final DynamicCommandExceptionType UNKNOWN_CONFIG = new DynamicCommandExceptionType(
	  m -> new TranslationTextComponent("simpleconfig.command.error.no_such_config", m));
	private static final DynamicCommandExceptionType INVALID_YAML = new DynamicCommandExceptionType(
	  m -> new TranslationTextComponent("simpleconfig.command.error.invalid_yaml", m));
	
	public static SimpleConfigValueArgumentType entryValue(String modId, Type type) {
		return new SimpleConfigValueArgumentType(modId, type);
	}
	
	private final @Nullable String modId;
	private final ModConfig.Type type;
	
	private SimpleConfigValueArgumentType(@Nullable String modId, Type type) {
		this.modId = modId;
		this.type = type;
	}
	
	public @Nullable SimpleConfig getConfig(CommandContext<?> ctx) {
		String modId = this.modId;
		if (modId == null) modId = ctx.getArgument("modId", String.class);
		if (SimpleConfig.hasConfig(modId, type))
			return SimpleConfig.getConfig(modId, type);
		return null;
	}
	
	@Override public String parse(StringReader reader) throws CommandSyntaxException {
		// TODO: Read a single scalar handling quotes, brackets and braces
		//       instead of consuming all input
		StringBuilder b = new StringBuilder();
		while (reader.canRead())
			b.append(reader.read());
		String value = b.toString();
		Yaml yaml = SimpleConfigCommentedYamlFormat.getDefaultYaml();
		try {
			yaml.compose(new java.io.StringReader(value));
		} catch (YAMLException e) {
			throw INVALID_YAML.createWithContext(reader, e.getLocalizedMessage());
		}
		return value;
	}
	
	@Override public <S> CompletableFuture<Suggestions> listSuggestions(
	  CommandContext<S> context, SuggestionsBuilder builder
	) {
		String key = context.getArgument("key", String.class);
		SimpleConfig config = getConfig(context);
		if (config == null) return Suggestions.empty();
		AbstractConfigEntry<Object, Object, Object, ?> entry = config.getEntry(key);
		String serialized = entry.getForCommand();
		if (serialized != null)
			builder.suggest(serialized, new TranslationTextComponent(
			  "simpleconfig.command.suggest.current"));
		String defSerialized = entry.forCommand(entry.defValue);
		if (defSerialized != null && !defSerialized.equals(serialized))
			builder.suggest(defSerialized, new TranslationTextComponent(
			  "simpleconfig.command.suggest.default"));
		return builder.buildFuture();
	}
	
	public static class Serializer implements IArgumentSerializer<SimpleConfigValueArgumentType> {
		@Override public void write(@NotNull SimpleConfigValueArgumentType arg, @NotNull PacketBuffer buf) {
			buf.writeBoolean(arg.modId != null);
			if (arg.modId != null) buf.writeString(arg.modId);
			buf.writeEnumValue(arg.type);
		}
		
		@Override public @NotNull SimpleConfigValueArgumentType read(@NotNull PacketBuffer buf) {
			return new SimpleConfigValueArgumentType(
			  buf.readBoolean()? buf.readString(32767) : null,
			  buf.readEnumValue(Type.class));
		}
		
		@Override public void write(@NotNull SimpleConfigValueArgumentType arg, @NotNull JsonObject obj) {
			obj.addProperty("modId", arg.modId);
			obj.addProperty("type", arg.type.extension());
		}
	}
}
