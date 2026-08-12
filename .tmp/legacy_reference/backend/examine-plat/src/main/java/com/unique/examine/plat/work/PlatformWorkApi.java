package com.unique.examine.plat.work;

import com.unique.examine.core.ai.PlatformTaskFacade;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PlatformWorkApi {
    private PlatformWorkApi() {
    }

    public enum TaskKind { PROJECT, GENERAL }
    public enum TaskStatus { ALL, OPEN, COMPLETED, CANCELLED }
    public enum ReportStatus { DRAFT, SUBMITTED }

    public record Overview(long activeProjects, long openTasks, long overdueTasks,
                           long todayTasks, boolean todayReportSubmitted) {
    }

    public record ProjectView(String id, String code, String name, String status,
                              LocalDate startDate, LocalDate dueDate,
                              long taskCount, long completedTaskCount,
                              Instant updatedAt, long version) {
    }

    public record ProjectInput(String code, String name, LocalDate startDate,
                               LocalDate dueDate) {
    }

    public record TaskView(String taskId, TaskKind kind, String projectId,
                           String projectName, String title, String description,
                           Instant dueAt, PlatformTaskFacade.Priority priority,
                           PlatformTaskFacade.Status status, List<String> labels,
                           Instant createdAt, Instant updatedAt, long version) {
        public TaskView {
            labels = List.copyOf(labels);
        }
    }

    public record TaskPage(List<TaskView> items, int page, int size, long total) {
        public TaskPage {
            items = List.copyOf(items);
        }
    }

    public record TaskInput(TaskKind kind, String projectId, String title,
                            String description, Instant dueAt,
                            PlatformTaskFacade.Priority priority,
                            List<String> labels) {
        public TaskInput {
            labels = labels == null ? List.of() : List.copyOf(labels);
        }
    }

    public record ReportView(String id, LocalDate reportDate, ReportStatus status,
                             String completed, String plan, String risks,
                             String projectId, String projectName,
                             Instant updatedAt, long version) {
    }

    public record ReportInput(LocalDate reportDate, String completed, String plan,
                              String risks, String projectId, Long expectedVersion) {
    }
}
