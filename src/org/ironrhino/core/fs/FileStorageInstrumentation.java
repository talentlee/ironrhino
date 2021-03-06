package org.ironrhino.core.fs;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.ironrhino.core.aop.BaseAspect;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Metrics;

@Component
@Aspect
public class FileStorageInstrumentation extends BaseAspect {

	@Around("execution(* *.*(..)) and target(fileStorage)")
	public Object timing(ProceedingJoinPoint pjp, FileStorage fileStorage) throws Throwable {
		if (!org.ironrhino.core.metrics.Metrics.isEnabled())
			return pjp.proceed();
		Object[] args = pjp.getArgs();
		Method method = ((MethodSignature) pjp.getSignature()).getMethod();
		String methodName = method.getName();
		if (methodName.startsWith("get") || methodName.startsWith("is"))
			return pjp.proceed();
		boolean error = false;
		long start = System.nanoTime();
		try {
			Object result = pjp.proceed();
			if (methodName.equals("write") && args.length > 2 && args[2] instanceof Long) {
				Metrics.summary("fs.write.size", "name", fileStorage.getName()).record((Long) args[2]);
			}
			return result;
		} catch (Exception e) {
			error = true;
			throw e;
		} finally {
			Metrics.timer("fs.operations", "name", fileStorage.getName(), "operation", methodName, "error",
					String.valueOf(error)).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
		}
	}

}