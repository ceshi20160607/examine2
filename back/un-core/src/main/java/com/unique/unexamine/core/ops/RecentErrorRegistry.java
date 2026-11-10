package com.unique.unexamine.core.ops;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** 进程内保留最近异常摘要，便于运维定位；具体堆栈仍在服务端日志。 */
@Component
public class RecentErrorRegistry {
    public record ErrorView(LocalDateTime occurTime,String type,String requestId) {}
    private final ArrayDeque<ErrorView> recent=new ArrayDeque<>();

    public synchronized void record(Throwable error) {
        recent.addFirst(new ErrorView(LocalDateTime.now(),error.getClass().getSimpleName(),MDC.get("requestId")));
        while (recent.size()>100) recent.removeLast();
    }

    public synchronized List<ErrorView> latest() {
        return recent.stream().limit(20).toList();
    }
}
