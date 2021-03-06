package org.ironrhino.core.spring.converter;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Set;

import org.ironrhino.core.model.Persistable;
import org.ironrhino.core.util.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;

public class SerializableToSerializableConverter implements ConditionalGenericConverter {

	@Override
	public Set<ConvertiblePair> getConvertibleTypes() {
		return Collections.singleton(new ConvertiblePair(Serializable.class, Serializable.class));
	}

	@Override
	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
		Class<?> sourceClass = sourceType.getType();
		Class<?> targetClass = targetType.getType();
		if (sourceClass == targetClass || targetClass.isAssignableFrom(sourceClass))
			return false;
		if (Persistable.class.isAssignableFrom(targetClass) && sourceClass == String.class)
			return true;
		try {
			targetClass.getConstructor(sourceClass);
			return true;
		} catch (NoSuchMethodException | SecurityException e) {
		}
		try {
			sourceClass.getConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
			return false;
		}
		if (targetClass == String.class || targetClass == Long.class || targetClass == Long.TYPE
				|| targetClass == Integer.class || targetClass == Integer.TYPE) {
			BeanWrapperImpl bw = new BeanWrapperImpl(sourceClass);
			try {
				PropertyDescriptor pd = bw.getPropertyDescriptor("id");
				if (pd != null && pd.getPropertyType() == targetClass)
					return true;
			} catch (InvalidPropertyException e) {
				return false;
			}
		}
		try {
			targetClass.getConstructor();
			return true;
		} catch (NoSuchMethodException | SecurityException e) {
			return false;
		}
	}

	@Override
	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (source == null)
			return null;
		Class<?> sourceClass = sourceType.getType();
		Class<?> targetClass = targetType.getType();
		if (targetClass.isInstance(source))
			return source;
		if (Persistable.class.isAssignableFrom(targetClass) && sourceClass == String.class) {
			try {
				Object target = targetClass.getConstructor().newInstance();
				BeanUtils.setPropertyValue(target, "id", source);
				return target;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		try {
			Constructor<?> ctor = targetClass.getConstructor(sourceClass);
			return ctor.newInstance(source);
		} catch (Exception e) {
		}
		if (targetClass == String.class || targetClass == Long.class || targetClass == Long.TYPE
				|| targetClass == Integer.class || targetClass == Integer.TYPE) {
			BeanWrapperImpl bw = new BeanWrapperImpl(source);
			return bw.getPropertyValue("id");
		}
		try {
			Object target = targetClass.getConstructor().newInstance();
			BeanUtils.copyProperties(source, target);
			return target;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
