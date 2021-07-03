package endorh.simple_config.core;

import endorh.simple_config.SimpleConfigMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static endorh.simple_config.core.entry.Builders.*;
import static endorh.simple_config.core.SimpleConfig.category;
import static endorh.simple_config.core.SimpleConfig.group;

@EventBusSubscriber(bus = Bus.MOD)
public class SimpleConfigTest {
	private static final Logger LOGGER = LogManager.getLogger();
	
	@SubscribeEvent
	public static void onTestEvent() {
		registerConfig();
	}
	
	public static void registerConfig() {
		List<Long> some_fibonacci = new ArrayList<>();
		for (long l : new long[]{1, 1, 2, 3, 5, 8})
			some_fibonacci.add(l);
		new SimpleConfigBuilder(SimpleConfigMod.MOD_ID, Type.SERVER)
		  .text("test_config")
		  .n(category("test_category")
		     .text("test_category")
		     .n(group("some_group", true)
		        .add("test", number(0.5, 0, 1))
		     ).add("other_test", bool(true))
		  ).n(group("test_group", true)
		      .add("test", number(10, 0, 10).slider())
		      .n(group("nested_group", true)
		         .add("enum", enum_(TestEnum.ENUM))
		         .add("color", color(Color.BLUE, true))
		      ).add("list", list(some_fibonacci, 0L, Long.MAX_VALUE))
		      .n(group("collapsed_group")
		         .add("string", string("Hello There!"))
		         .n(group("super_nested_group")
		              .n(group("and_even_more")
		                   .add("ok_that_was_enough", number(0, -100, 100).slider())))
		      ).n(group("empty_group", true)))
		  .setBaker(SimpleConfigTest::onBakeTestConfig)
		  .buildAndRegister();
	}
	
	public static void onBakeTestConfig(SimpleConfig config) {
		LOGGER.debug("Baking test config");
		final SimpleConfigGroup test_group = config.getGroup("test_group");
		final SimpleConfigGroup nested_group = test_group.getGroup("nested_group");
		final SimpleConfigCategory test_category = config.getCategory("test_category");
		final SimpleConfigGroup some_group = test_category.getGroup("some_group");
		final float test = some_group.getFloat("test");
	}
	
	public enum TestEnum {
		ENUM, OTHER_ENUM
	}
}