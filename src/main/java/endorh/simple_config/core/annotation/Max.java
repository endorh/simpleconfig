package endorh.simple_config.core.annotation;


import endorh.simple_config.core.entry.RangedEntry;
import endorh.simple_config.core.entry.RangedListEntry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If this field generates a {@link RangedEntry}
 * or {@link RangedListEntry}, this annotation
 * sets its maximum value (inclusive)<br>
 * The value is converted from double to the correspondent type
 * @see Entry
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Max {
	double value() default Double.POSITIVE_INFINITY;
}