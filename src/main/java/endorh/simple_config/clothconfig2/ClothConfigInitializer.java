package endorh.simple_config.clothconfig2;

import endorh.simple_config.clothconfig2.api.ScrollingContainer;
import endorh.simple_config.clothconfig2.impl.EasingMethod;
import endorh.simple_config.clothconfig2.impl.EasingMethod.EasingMethodImpl;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

@OnlyIn(value = Dist.CLIENT)
public class ClothConfigInitializer {
	public static final Logger LOGGER = LogManager.getFormatterLogger("ClothConfig");
	public static final String MOD_ID = "cloth-config";
	
	@Deprecated
	@ApiStatus.ScheduledForRemoval
	public static double handleScrollingPosition(
	  double[] target, double scroll, double maxScroll, float delta, double start, double duration
	) {
		return ScrollingContainer.handleScrollingPosition(
		  target, scroll, maxScroll, delta, start, duration);
	}
	
	@Deprecated
	@ApiStatus.ScheduledForRemoval
	public static double expoEase(double start, double end, double amount) {
		return ScrollingContainer.ease(start, end, amount, ClothConfigInitializer.getEasingMethod());
	}
	
	@Deprecated
	@ApiStatus.ScheduledForRemoval
	public static double clamp(double v, double maxScroll) {
		return ScrollingContainer.clampExtension(v, maxScroll);
	}
	
	public static EasingMethod getEasingMethod() {
		return EasingMethodImpl.CIRC;
	}
	
	public static long getScrollDuration() {
		return 150L;
	}
	
	public static double getScrollStep() {
		return 16.0;
	}
	
	public static double getBounceBackMultiplier() {
		return -10.0;
	}
}
