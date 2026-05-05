package com.citytrip.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackBehavior {

    String eventType();

    String eventSource() default "backend";

    double weight() default 1.0D;

    String itineraryIdExpression() default "";

    String poiIdExpression() default "";

    String optionKeyExpression() default "";

    String extraExpression() default "";
}
