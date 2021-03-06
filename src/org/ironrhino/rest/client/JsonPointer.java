package org.ironrhino.rest.client;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(METHOD)
public @interface JsonPointer {

	String value();

	Class<? extends JsonValidator> validator() default JsonValidator.class;

}
