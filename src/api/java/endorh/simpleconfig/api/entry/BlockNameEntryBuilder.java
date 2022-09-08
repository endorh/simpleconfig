package endorh.simpleconfig.api.entry;

import net.minecraft.tags.Tag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Contract;

public interface BlockNameEntryBuilder
  extends ResourceEntryBuilder<BlockNameEntryBuilder> {
	/**
	 * Restrict the selectable items to those of a tag<br>
	 * This can only be done on server configs, since tags
	 * are server-dependant
	 */
	@Contract(pure=true) BlockNameEntryBuilder suggest(Tag<Block> tag);
}