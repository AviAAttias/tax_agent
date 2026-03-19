package com.abco.taxassessment.tenant;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import java.util.Map;
import java.util.UUID;

/**
 * Propagates TenantContext across @Async thread boundaries (§7).
 *
 * Without this decorator, @Async methods run in a thread-pool thread that has no TenantContext.
 * This decorator captures the tenant ID from the submitting thread and restores it in the worker thread.
 *
 * Registered with the ThreadPoolTaskExecutor in AsyncConfig.
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        UUID tenantId = TenantContext.get();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            try {
                if (tenantId != null) {
                    TenantContext.set(tenantId);
                }
                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                }
                runnable.run();
            } finally {
                TenantContext.clear();
                MDC.clear();
            }
        };
    }
}
