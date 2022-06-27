package endorh.simpleconfig.core.entry;

import endorh.simpleconfig.clothconfig2.api.AbstractConfigListEntry;
import endorh.simpleconfig.clothconfig2.api.ConfigEntryBuilder;
import endorh.simpleconfig.clothconfig2.api.Modifier;
import endorh.simpleconfig.clothconfig2.api.ModifierKeyCode;
import endorh.simpleconfig.clothconfig2.impl.builders.KeyCodeBuilder;
import endorh.simpleconfig.core.AbstractConfigEntry;
import endorh.simpleconfig.core.AbstractConfigEntryBuilder;
import endorh.simpleconfig.core.IKeyEntry;
import endorh.simpleconfig.core.ISimpleConfigEntryHolder;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;


/**
 * Key binding entry. By default, accepts modifiers but not mouse keys.<br>
 * This is because handling mouse keys requires extra code on your end,
 * if you only ever handle keyCode and scanCode in keyPress events, you won't
 * be able to detect mouse keys.<br><br>
 * <b>Prefer registering regular {@link KeyBinding}s through
 * {@link net.minecraftforge.fml.client.registry.ClientRegistry#registerKeyBinding(KeyBinding)}
 * </b><br>
 * <b>KeyBindings registered the proper way can be configured altogether with
 * other vanilla/modded keybindings, with highlighted conflicts</b><br><br>
 * The only encouraged use of KeyBind entries is when you need further
 * flexibility, such as <em>a map of KeyBinds to actions of some kind</em>.
 * Use wisely.
 */
@OnlyIn(Dist.CLIENT)
public class KeyBindEntry extends AbstractConfigEntry<
  ModifierKeyCode, String, ModifierKeyCode, KeyBindEntry>
  implements IKeyEntry<String, ModifierKeyCode> {
	protected boolean allowKey = true;
	protected boolean allowModifiers = true;
	protected boolean allowMouse = true;
	
	public KeyBindEntry(ISimpleConfigEntryHolder parent, String name, ModifierKeyCode value) {
		super(parent, name, value);
	}
	
	public static class Builder extends AbstractConfigEntryBuilder<
	  ModifierKeyCode, String, ModifierKeyCode, KeyBindEntry, Builder> {
		
		protected boolean allowKey = true;
		protected boolean allowModifiers = true;
		// False by default, since allowing requires implementing
		//   additional code to handle mouse keys.
		//   Allowing by default would be error-prone.
		protected boolean allowMouse = false;
		
		public Builder() {
			this(ModifierKeyCode.unknown());
		}
		
		public Builder(String key) {
			this(ModifierKeyCode.parse(key));
		}
		
		public Builder(InputMappings.Input key, Modifier modifier) {
			this(ModifierKeyCode.of(key, modifier));
		}
		
		public Builder(ModifierKeyCode value) {
			super(value, ModifierKeyCode.class);
		}
		
		public Builder allowModifiers(boolean allowModifiers) {
			final Builder copy = copy();
			copy.allowModifiers = allowModifiers;
			return copy;
		}
		
		public Builder allowModifiers() {
			return allowModifiers(true);
		}
		public Builder noModifiers() {
			return allowModifiers(false);
		}
		
		public Builder keyboard(boolean allowKeys) {
			final Builder copy = copy();
			copy.allowKey = allowKeys;
			return copy;
		}
		
		/**
		 * Allow/disallow mouse keys.<br>
		 * <b>Warning:</b> If you allow mouse KeyBinds, your code must
		 * expect them. If you only check for keyCode and scanCode, you
		 * won't be able to handle mouse KeyBinds.
		 */
		public Builder mouse(boolean allowMouse) {
			final Builder copy = copy();
			copy.allowMouse = allowMouse;
			return copy;
		}
		
		/**
		 * Allow mouse keys.<br>
		 * <b>Warning:</b> If you allow mouse KeyBinds, your code must
		 * expect them. If you only check for keyCode and scanCode, you
		 * won't be able to handle mouse KeyBinds.
		 */
		public Builder allowMouse() {
			return mouse(true);
		}
		
		/**
		 * Only allow mouse keys.<br>
		 * <b>Warning:</b> If you allow mouse KeyBinds, your code must
		 * expect them. If you only check for keyCode and scanCode, you
		 * won't be able to handle mouse KeyBinds.
		 */
		public Builder mouseOnly() {
			return mouse(true).keyboard(false);
		}
		
		@Override protected KeyBindEntry buildEntry(ISimpleConfigEntryHolder parent, String name) {
			final KeyBindEntry entry = new KeyBindEntry(parent, name, value);
			entry.allowModifiers = allowModifiers;
			entry.allowKey = allowKey;
			entry.allowMouse = allowMouse;
			return entry;
		}
		
		@Override protected Builder createCopy() {
			final Builder copy = new Builder(value.copy());
			copy.allowModifiers = allowModifiers;
			copy.allowKey = allowKey;
			copy.allowMouse = allowMouse;
			return copy;
		}
	}
	
	@Override public String forConfig(ModifierKeyCode value) {
		return value != null? value.toConfig() : "";
	}
	
	@Nullable @Override public ModifierKeyCode fromConfig(@Nullable String value) {
		return ModifierKeyCode.parse(value);
	}
	
	@Override protected Optional<ConfigValue<?>> buildConfigEntry(
	  ForgeConfigSpec.Builder builder
	) {
		return Optional.of(decorate(builder).define(name, value, createConfigValidator()));
	}
	
	@OnlyIn(Dist.CLIENT) @Override
	public Optional<AbstractConfigListEntry<ModifierKeyCode>> buildGUIEntry(
	  ConfigEntryBuilder builder
	) {
		final KeyCodeBuilder valBuilder = builder
		  .startModifierKeyCodeField(getDisplayName(), forGui(get()))
		  .setAllowKey(allowKey)
		  .setAllowModifiers(allowModifiers)
		  .setAllowMouse(allowMouse);
		return Optional.of(decorate(valBuilder).build());
	}
	
	@Override public Optional<String> deserializeStringKey(@NotNull String key) {
		return Optional.of(key);
	}
}
