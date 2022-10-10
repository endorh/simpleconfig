package endorh.simpleconfig.api.entry;

import endorh.simpleconfig.api.ConfigEntryBuilder;
import endorh.simpleconfig.api.KeyEntryBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

public interface ResourceEntryBuilder<Self extends ResourceEntryBuilder<Self>>
  extends ConfigEntryBuilder<ResourceLocation, String, ResourceLocation, Self>, KeyEntryBuilder<ResourceLocation> {
	@Contract(pure=true) @NotNull Self suggest(Supplier<List<ResourceLocation>> suggestionSupplier);
	
	@Contract(pure=true) @NotNull Self suggest(List<ResourceLocation> suggestions);
}
