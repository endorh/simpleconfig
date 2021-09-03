package endorh.simple_config.core.entry;

import com.google.common.collect.Lists;
import endorh.simple_config.clothconfig2.api.AbstractConfigListEntry;
import endorh.simple_config.clothconfig2.api.ConfigEntryBuilder;
import endorh.simple_config.clothconfig2.impl.builders.ComboBoxFieldBuilder;
import endorh.simple_config.core.AbstractConfigEntry;
import endorh.simple_config.core.AbstractConfigEntryBuilder;
import endorh.simple_config.core.ISimpleConfigEntryHolder;
import endorh.simple_config.core.entry.StringEntry.SupplierSuggestionProvider;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ResourceLocationException;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.jetbrains.annotations.ApiStatus.Internal;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static endorh.simple_config.clothconfig2.impl.builders.ComboBoxFieldBuilder.ofItemName;

public class ItemNameEntry extends AbstractResourceEntry<ItemNameEntry> {
	
	@Internal public ItemNameEntry(
	  ISimpleConfigEntryHolder parent, String name,
	  @Nullable ResourceLocation value
	) {
		super(parent, name, value != null ? value : new ResourceLocation(""));
	}
	
	public static class Builder extends AbstractResourceEntry.Builder<ItemNameEntry, Builder> {
		protected ITag<Item> tag = null;
		
		public Builder(ResourceLocation value) {
			super(value, ResourceLocation.class);
			suggestionSupplier = () -> Registry.ITEM.getEntries().stream().map(Entry::getValue)
			  .filter(i -> i.getGroup() != null).map(ForgeRegistryEntry::getRegistryName)
			  .collect(Collectors.toList());
		}
		
		public Builder suggest(Ingredient ingredient) {
			Builder copy = copy();
			copy.suggestionSupplier =
			  () -> Arrays.stream(ingredient.getMatchingStacks()).map(
			    s -> s.getItem().getRegistryName()
			  ).filter(Objects::nonNull).collect(Collectors.toList());
			return copy;
		}
		
		/**
		 * Restrict the selectable items to those of a tag<br>
		 * This can only be done on server configs, since tags
		 * are server-dependant
		 */
		public Builder suggest(ITag<Item> tag) {
			Builder copy = copy();
			copy.tag = tag;
			return copy;
		}
		
		@Override
		protected ItemNameEntry buildEntry(ISimpleConfigEntryHolder parent, String name) {
			if (parent.getRoot().type != Type.SERVER && tag != null)
				throw new IllegalArgumentException("Cannot use tag item filters in non-server config entry");
			if (tag != null) {
				final Supplier<List<ResourceLocation>> supplier = suggestionSupplier;
				suggestionSupplier = () -> Stream.concat(
				  supplier != null? supplier.get().stream() : Stream.empty(),
				  tag.getAllElements().stream().map(ForgeRegistryEntry::getRegistryName)
				).collect(Collectors.toList());
			}
			return new ItemNameEntry(parent, name, value);
		}
		
		@Override protected Builder createCopy() {
			final Builder copy = new Builder(value);
			copy.tag = tag;
			return copy;
		}
	}
	
	@OnlyIn(Dist.CLIENT) @Override
	public Optional<AbstractConfigListEntry<ResourceLocation>> buildGUIEntry(
	  ConfigEntryBuilder builder
	) {
		final ComboBoxFieldBuilder<ResourceLocation> entryBuilder =
		  builder.startComboBox(getDisplayName(), ofItemName(), forGui(get()));
		return Optional.of(decorate(entryBuilder).build());
	}
}
