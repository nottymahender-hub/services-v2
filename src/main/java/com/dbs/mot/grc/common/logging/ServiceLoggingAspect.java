package com.dbs.mot.grc.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cross-cutting DEBUG-level entry/exit logging for service-layer methods and CSV import
 * entry points, plus WARN-level logging of any exception before it is rethrown.
 *
 * <p>{@link MultipartFile} arguments are summarised (filename/size only) rather than
 * logged in full — the raw file content must never end up in application logs.
 */
@Slf4j
@Aspect
@Component
public class ServiceLoggingAspect {

    @Around("execution(* com.dbs.mot.grc..service..*(..)) "
            + "|| execution(* com.dbs.mot.grc..csv.handler.*.processAndImport(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String argsSummary = summariseArgs(joinPoint.getArgs());

        log.debug("Entering {}({})", methodName, argsSummary);
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long durationMs = System.currentTimeMillis() - start;
            log.debug("Exiting {} -> {} (durationMs={})", methodName, summarise(result), durationMs);
            return result;
        } catch (Throwable ex) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("Exception in {} after {}ms: {}", methodName, durationMs, ex.toString());
            throw ex;
        }
    }

    private String summariseArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        return Arrays.stream(args).map(this::summarise).collect(Collectors.joining(", "));
    }

    private String summarise(Object arg) {
        if (arg == null) return "null";
        if (arg instanceof MultipartFile file) {
            return "MultipartFile[name=" + file.getOriginalFilename() + ", size=" + file.getSize() + "]";
        }
        String text = arg.toString();
        return text.length() > 200 ? text.substring(0, 200) + "...(truncated)" : text;
    }
}
