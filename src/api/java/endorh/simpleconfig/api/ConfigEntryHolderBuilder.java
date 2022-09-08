package endorh.simpleconfig.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Contract;

import java.util.function.Supplier;

public interface ConfigEntryHolderBuilder<Builder extends ConfigEntryHolderBuilder<Builder>> {
	/**
	 * Flag this config section as requiring a restart to be effective
	 */
	@Contract("-> this") Builder restart();
	
	/**
	 * Add an entry to the config
	 */
	@Contract("_, _ -> this") Builder add(
	  String name, ConfigEntryBuilder<?, ?, ?, ?> entryBuilder
	);
	
	/**
	 * Create a text entry in the config<br>
	 *
	 * @param name Name of the entry
	 * @param args Args to be passed to the translation<br>
	 *   As a special case, {@code Supplier} args will be
	 *   called before being filled in
	 */
	@Contract("_, _ -> this") Builder text(String name, Object... args);
	
	/**
	 * Create a text entry in the config
	 */
	@Contract("_ -> this") Builder text(Component text);
	
	/**
	 * Create a text entry in the config
	 */
	@Contract("_ -> this") Builder text(Supplier<Component> textSupplier);
	
	/**
	 * Add a config group
	 */
	@Contract("_ -> this") Builder n(ConfigGroupBuilder group);
}