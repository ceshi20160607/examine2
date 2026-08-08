package com.unique.examine.module.runtime.notification;

import com.unique.examine.core.api.ResultNotificationFacade;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JobResultNotificationWiringContractTest {

    @Test
    void notifierDependsOnlyOnTheCorePortAndDispatchesAfterCommit() throws Exception {
        assertThat(JobResultNotifier.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(ResultNotificationFacade.class));

        var source = compact(read("notification/JobResultNotifier.java"));
        assertThat(source)
                .contains("transactionsynchronizationmanager.registersynchronization(")
                .contains("public void aftercommit() { dispatchsafely(value);")
                .contains("notifications.dispatch(command)")
                .contains("job-result:import:")
                .contains("job-result:export:")
                .contains("job-result:print:")
                .doesNotContain("com.unique.examine.event");
    }

    @Test
    void everyTerminalExecutorPathSchedulesExactlyOneNotification() throws Exception {
        assertThat(compact(read("importing/ImportJobExecutor.java")))
                .contains("notifier.importsucceeded(batch)")
                .contains("notifier.importfailed(batch, failure.getmessage())");
        assertThat(compact(read("exporting/ExportJobExecutor.java")))
                .contains("notifier.exportsucceeded(task, outputrows.size())")
                .contains("notifier.exportfailed(task, message)");
        assertThat(compact(read("printing/PrintJobExecutor.java")))
                .contains("notifier.printsucceeded(task)")
                .contains("notifier.printfailed(task, message)");
    }

    private static String read(String relativePath) throws Exception {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("backend/examine-module/src/main/java/com/unique/examine/module/runtime/")
                    .resolve(relativePath);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            candidate = current.resolve("src/main/java/com/unique/examine/module/runtime/").resolve(relativePath);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relativePath);
    }

    private static String compact(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ");
    }
}
