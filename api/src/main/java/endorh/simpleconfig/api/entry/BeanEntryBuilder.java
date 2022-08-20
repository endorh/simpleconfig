package endorh.simpleconfig.api.entry;

import endorh.simpleconfig.api.ConfigEntryBuilder;
import endorh.simpleconfig.api.KeyEntryBuilder;
import endorh.simpleconfig.ui.icon.Icon;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public interface BeanEntryBuilder<B> extends ConfigEntryBuilder<
  B, Map<String, Object>, B, BeanEntryBuilder<B>
> {
	@Contract(pure=true)default BeanEntryBuilder<B> allowUneditableProperties() {
		return allowUneditableProperties(true);
	}
	@Contract(pure=true) BeanEntryBuilder<B> allowUneditableProperties(boolean allowUneditable);
	
	/**
	 * Add an editable entry for a bean property.
	 */
	@Contract(pure=true) BeanEntryBuilder<B> add(String name, ConfigEntryBuilder<?, ?, ?, ?> entryBuilder);
	
	/**
	 * Add an editable entry for a bean property as a caption for this bean entry.<br>
	 * Only one property can be the caption. The last set is used.
	 */
	@Contract(pure=true) <CB extends ConfigEntryBuilder<?, ?, ?, ?> & KeyEntryBuilder<?>>
	BeanEntryBuilder<B> caption(String name, CB entryBuilder);
	
	/**
	 * Remove a previously set caption property.<br>
	 * Transforms it into a regular property.
	 */
	@Contract(pure=true) BeanEntryBuilder<B> withoutCaption();
	
	/**
	 * Set an icon to be displayed at the header of this entry.<br>
	 * Max recommended icon size is 18×18, but you can try something larger and see
	 * how it fits in the GUI.
	 * Icons can depend on the GUI value of the bean.
	 */
	@Contract(pure=true) BeanEntryBuilder<B> withIcon(Function<B, Icon> icon);
	
	/**
	 * Set an icon to be displayed at the header of this entry.<br>
	 * Max recommended icon size is 18×18, but you can try something larger and see
	 * how it fits in the GUI.<br>
	 * Icons can depend on the GUI value of the bean.
	 */
	@Contract(pure=true) default BeanEntryBuilder<B> withIcon(@Nullable Icon icon) {
		return withIcon(icon == null? null : b -> icon);
	}
}
