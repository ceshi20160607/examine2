package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.work.service.WorkTaskReminderClaimWorker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FeatureApiJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "feature_api_root";
    private static final String ROOT_PASSWORD = "Feature-Api-Root-Password-84!";
    private static final Path FILE_STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "examine-feature-api-" + UUID.randomUUID());

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_feature_api_test")
            .withUsername("examine_feature_api_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.locations", () -> "filesystem:"
                + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.bootstrap.root.username", () -> ROOT_USERNAME);
        registry.add("examine.bootstrap.root.password", () -> ROOT_PASSWORD);
        registry.add("examine.bootstrap.root.display-name", () -> "Feature API Test Root");
        registry.add("examine.file.storage-root", FILE_STORAGE_ROOT::toString);
        registry.add("examine.work.task-reminder.scheduler.enabled", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdService idService;

    @Autowired
    private WorkTaskReminderClaimWorker workTaskReminderClaimWorker;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterAll
    static void cleanFileStorage() throws IOException {
        if (!Files.exists(FILE_STORAGE_ROOT)) {
            return;
        }
        try (var paths = Files.walk(FILE_STORAGE_ROOT)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void rootExercisesWorkEventAndFileJourneysAcrossTenantBoundary() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));

        var createdSystem = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "feature_api_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Feature API Journey",
                "description", "HTTP journeys for work, event and file",
                "tenantMode", "MULTI"
        )), Map.of("Idempotency-Key", key()));
        assertOk(createdSystem);
        var systemId = text(createdSystem.body(), "/data/id");
        var workRoot = "/api/v1/systems/" + systemId + "/work/tasks";
        var projectsRoot = "/api/v1/systems/" + systemId + "/work/projects";
        var reportsRoot = "/api/v1/systems/" + systemId + "/work/reports";
        var eventRoot = "/api/v1/systems/" + systemId + "/event/messages";
        var fileRoot = "/api/v1/systems/" + systemId + "/files";

        assertError(new TestClient().get(workRoot), 401, "AUTH_REQUIRED");
        assertError(client.get(workRoot), 403, "CONTEXT_SYSTEM_MISMATCH");

        var switched = client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        var firstTenantId = text(switched.body(), "/data/context/tenantId");
        var memberId = text(switched.body(), "/data/context/memberId");
        var permissions = StreamSupport.stream(
                        switched.body().at("/data/context/permissions").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(permissions).contains(
                "work.task.access", "work.task.create", "work.task.manage",
                "work.project.access", "work.project.create", "work.project.manage",
                "work.report.access", "work.report.create", "work.report.manage",
                "event.message.access",
                "file.create", "file.read", "file.reference", "file.manage");

        var targetMemberId = createTargetMember(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(memberId));
        var createdProject = client.postWithCsrf(projectsRoot, json(Map.of(
                "title", "Feature delivery",
                "description", "Project-backed HTTP journey"
        )), Map.of());
        assertCreated(createdProject);
        var projectId = text(createdProject.body(), "/data/id");
        assertThat(text(createdProject.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(createdProject.body().at("/data/version").asLong()).isOne();

        var initialProjectMembers = client.get(projectsRoot + "/" + projectId + "/members");
        assertOk(initialProjectMembers);
        assertThat(initialProjectMembers.body().at("/data")).hasSize(1);
        assertThat(text(initialProjectMembers.body(), "/data/0/memberId")).isEqualTo(memberId);
        assertThat(text(initialProjectMembers.body(), "/data/0/role")).isEqualTo("OWNER");

        var addedProjectMember = client.postWithCsrf(
                projectsRoot + "/" + projectId + "/members",
                json(Map.of(
                        "memberId", Long.parseLong(targetMemberId),
                        "role", "MEMBER"
                )), Map.of());
        assertCreated(addedProjectMember);
        assertThat(text(addedProjectMember.body(), "/data/memberId"))
                .isEqualTo(targetMemberId);
        assertThat(text(addedProjectMember.body(), "/data/role")).isEqualTo("MEMBER");

        var projects = client.get(projectsRoot
                + "?keyword=Feature&status=ACTIVE&page=1&size=20");
        var projectItems = pageItems(projects, 1, 20, 1);
        assertThat(projectItems.get(0).path("id").asText()).isEqualTo(projectId);

        var createdTask = client.postWithCsrf(workRoot, json(Map.of(
                "title", "Verify feature HTTP journey",
                "assigneeMemberId", Long.parseLong(targetMemberId),
                "projectId", Long.parseLong(projectId),
                "description", "One fact shared by all task views",
                "dueAt", "2031-03-04T05:06:07Z"
        )), Map.of());
        assertCreated(createdTask);
        var taskId = text(createdTask.body(), "/data/id");
        assertThat(text(createdTask.body(), "/data/tenantId")).isEqualTo(firstTenantId);
        assertThat(text(createdTask.body(), "/data/status")).isEqualTo("OPEN");
        assertThat(text(createdTask.body(), "/data/projectId")).isEqualTo(projectId);
        assertThat(text(createdTask.body(), "/data/assigneeMemberId"))
                .isEqualTo(targetMemberId);
        assertThat(text(createdTask.body(), "/data/dueAt"))
                .isEqualTo("2031-03-04T05:06:07Z");

        var revisedTask = client.putWithCsrf(workRoot + "/" + taskId, json(Map.of(
                "title", "Verify shared Work views",
                "projectId", Long.parseLong(projectId),
                "description", "Updated without replacing the task fact",
                "dueAt", "2031-03-05T05:06:07Z",
                "version", 1
        )), Map.of());
        assertOk(revisedTask);
        assertThat(text(revisedTask.body(), "/data/id")).isEqualTo(taskId);
        assertThat(revisedTask.body().at("/data/version").asLong()).isEqualTo(2);

        var tasks = client.get(workRoot);
        var taskItems = pageItems(tasks, 1, 20, 1);
        assertThat(taskItems).hasSize(1);
        assertThat(taskItems.get(0).path("id").asText()).isEqualTo(taskId);

        var projectTasks = pageItems(client.get(workRoot
                + "?projectId=" + projectId
                + "&status=ALL&role=ALL&page=1&size=20"), 1, 20, 1);
        var openProjectTasks = pageItems(client.get(workRoot
                + "?projectId=" + projectId
                + "&status=OPEN&role=ALL&page=1&size=20"), 1, 20, 1);
        var dueProjectTasks = pageItems(client.get(workRoot
                + "?projectId=" + projectId
                + "&dueFrom=2031-03-05T00:00:00Z"
                + "&dueTo=2031-03-06T00:00:00Z"
                + "&status=ALL&role=ALL&page=1&size=20"), 1, 20, 1);
        assertThat(projectTasks.get(0).path("id").asText()).isEqualTo(taskId);
        assertThat(openProjectTasks.get(0).path("id").asText()).isEqualTo(taskId);
        assertThat(dueProjectTasks.get(0).path("id").asText()).isEqualTo(taskId);

        var reportDate = LocalDate.now().minusDays(1);
        var createdReport = client.postWithCsrf(reportsRoot, json(Map.of(
                "workDate", reportDate.toString(),
                "completedWork", "Created the project-backed task journey",
                "plannedWork", "Verify the durable daily-report lifecycle",
                "blockers", "None"
        )), Map.of());
        assertCreated(createdReport);
        var reportId = text(createdReport.body(), "/data/id");
        assertThat(text(createdReport.body(), "/data/authorMemberId")).isEqualTo(memberId);
        assertThat(text(createdReport.body(), "/data/workDate")).isEqualTo(reportDate.toString());
        assertThat(text(createdReport.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(createdReport.body().at("/data/version").asLong()).isOne();

        var revisedReport = client.putWithCsrf(reportsRoot + "/" + reportId, json(Map.of(
                "completedWork", "Created and verified one shared Work task fact",
                "plannedWork", "Submit and review the daily report",
                "blockers", "No blockers",
                "version", 1
        )), Map.of());
        assertOk(revisedReport);
        assertThat(revisedReport.body().at("/data/version").asLong()).isEqualTo(2);

        var submittedReport = client.postWithCsrf(
                reportsRoot + "/" + reportId + ":submit",
                json(Map.of("version", 2)), Map.of());
        assertOk(submittedReport);
        assertThat(text(submittedReport.body(), "/data/status")).isEqualTo("SUBMITTED");
        assertThat(submittedReport.body().at("/data/version").asLong()).isEqualTo(3);
        var replayedSubmission = client.postWithCsrf(
                reportsRoot + "/" + reportId + ":submit",
                json(Map.of("version", 3)), Map.of());
        assertOk(replayedSubmission);
        assertThat(replayedSubmission.body().at("/data/version").asLong()).isEqualTo(3);
        assertError(client.putWithCsrf(reportsRoot + "/" + reportId, json(Map.of(
                        "completedWork", "Rejected submitted edit",
                        "plannedWork", "Rejected submitted edit",
                        "blockers", "Rejected submitted edit",
                        "version", 3
                )), Map.of()), 409, "WORK_REPORT_STATE_INVALID");

        var reportItems = pageItems(client.get(reportsRoot
                + "?scope=ALL&memberId=" + memberId
                + "&dateFrom=" + reportDate + "&dateTo=" + reportDate
                + "&status=SUBMITTED&page=1&size=20"), 1, 20, 1);
        assertThat(reportItems.get(0).path("id").asText()).isEqualTo(reportId);
        var reportSummary = client.get(reportsRoot + "/summary?endDate="
                + reportDate + "&memberId=" + memberId);
        assertOk(reportSummary);
        assertThat(reportSummary.body().at("/data/submittedCount").asLong()).isOne();
        assertThat(StreamSupport.stream(
                        reportSummary.body().at("/data/days").spliterator(), false)
                .anyMatch(day -> reportDate.toString().equals(day.path("workDate").asText())
                        && "SUBMITTED".equals(day.path("state").asText())
                        && reportId.equals(day.path("reportId").asText())))
                .isTrue();

        var stableTaskTime = Instant.parse("2030-01-02T03:04:05Z");
        var currentMemberId = Long.parseLong(memberId);
        var otherMemberId = currentMemberId + 1;
        var stableTaskFirst = insertWorkTask(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                currentMemberId,
                otherMemberId,
                "StableJourney task one",
                "OPEN",
                stableTaskTime);
        var stableTaskSecond = insertWorkTask(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                currentMemberId,
                otherMemberId,
                "StableJourney task two",
                "OPEN",
                stableTaskTime);
        var assignedCompletedTask = insertWorkTask(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                otherMemberId,
                currentMemberId,
                "AssignedJourney completed",
                "COMPLETED",
                Instant.parse("2029-01-02T03:04:05Z"));
        var unrelatedTask = insertWorkTask(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                otherMemberId,
                otherMemberId + 1,
                "UnrelatedJourney open",
                "OPEN",
                Instant.parse("2028-01-02T03:04:05Z"));

        var firstTaskPage = client.get(workRoot
                + "?keyword=StableJourney&status=OPEN&role=CREATED_BY_ME&page=1&size=1");
        var secondTaskPage = client.get(workRoot
                + "?keyword=StableJourney&status=OPEN&role=CREATED_BY_ME&page=2&size=1");
        var firstStableTaskItems = pageItems(firstTaskPage, 1, 1, 2);
        var secondStableTaskItems = pageItems(secondTaskPage, 2, 1, 2);
        assertThat(firstStableTaskItems.get(0).path("id").asText())
                .isEqualTo(Long.toString(Math.max(stableTaskFirst, stableTaskSecond)));
        assertThat(secondStableTaskItems.get(0).path("id").asText())
                .isEqualTo(Long.toString(Math.min(stableTaskFirst, stableTaskSecond)));

        var assignedCompleted = client.get(workRoot
                + "?keyword=AssignedJourney&status=COMPLETED&role=ASSIGNED_TO_ME&page=1&size=10");
        var assignedCompletedItems = pageItems(assignedCompleted, 1, 10, 1);
        assertThat(assignedCompletedItems.get(0).path("id").asText())
                .isEqualTo(Long.toString(assignedCompletedTask));

        var allTenantTasks = client.get(workRoot
                + "?keyword=UnrelatedJourney&status=OPEN&role=ALL&page=1&size=10");
        var allTenantTaskItems = pageItems(allTenantTasks, 1, 10, 1);
        assertThat(allTenantTaskItems.get(0).path("id").asText())
                .isEqualTo(Long.toString(unrelatedTask));

        var messageId = insertInboxMessage(
                Long.parseLong(systemId), Long.parseLong(firstTenantId), Long.parseLong(memberId), taskId);
        var inbox = client.get(eventRoot);
        var inboxItems = pageItems(inbox, 1, 20, 1);
        assertThat(inboxItems).hasSize(1);
        assertThat(inboxItems.get(0).path("id").asText()).isEqualTo(Long.toString(messageId));
        assertThat(inboxItems.get(0).path("status").asText()).isEqualTo("UNREAD");
        assertThat(inboxItems.get(0).at("/target/type").asText()).isEqualTo("WORK_TASK");
        assertThat(inboxItems.get(0).at("/target/id").asText()).isEqualTo(taskId);

        var unreadCount = client.get(eventRoot + "/unread-count");
        assertOk(unreadCount);
        assertThat(unreadCount.body().at("/data/unreadCount").asLong()).isOne();

        var stableMessageTime = Instant.parse("2030-02-03T04:05:06Z");
        var stableMessageFirst = insertInboxMessage(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                currentMemberId,
                taskId,
                "Stable message one",
                "UNREAD",
                stableMessageTime);
        var stableMessageSecond = insertInboxMessage(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                currentMemberId,
                taskId,
                "Stable message two",
                "UNREAD",
                stableMessageTime);
        var seededReadMessage = insertInboxMessage(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                currentMemberId,
                taskId,
                "Read message",
                "READ",
                Instant.parse("2029-02-03T04:05:06Z"));
        var seededArchivedMessage = insertInboxMessage(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                currentMemberId,
                taskId,
                "Archived message",
                "ARCHIVED",
                Instant.parse("2028-02-03T04:05:06Z"));

        var allMessages = pageItems(
                client.get(eventRoot + "?status=ALL&page=1&size=10"),
                1, 10, 4);
        assertThat(allMessages).allMatch(item -> !"ARCHIVED".equals(item.path("status").asText()));

        var unreadMessages = pageItems(
                client.get(eventRoot + "?status=UNREAD&page=1&size=10"),
                1, 10, 3);
        assertThat(unreadMessages).allMatch(item -> "UNREAD".equals(item.path("status").asText()));

        var readMessages = pageItems(
                client.get(eventRoot + "?status=READ&page=1&size=10"),
                1, 10, 1);
        assertThat(readMessages.get(0).path("id").asText())
                .isEqualTo(Long.toString(seededReadMessage));

        var archivedMessages = pageItems(
                client.get(eventRoot + "?status=ARCHIVED&page=1&size=10"),
                1, 10, 1);
        assertThat(archivedMessages.get(0).path("id").asText())
                .isEqualTo(Long.toString(seededArchivedMessage));

        var firstMessagePage = pageItems(
                client.get(eventRoot + "?status=UNREAD&page=1&size=1"),
                1, 1, 3);
        var secondMessagePage = pageItems(
                client.get(eventRoot + "?status=UNREAD&page=2&size=1"),
                2, 1, 3);
        assertThat(firstMessagePage.get(0).path("id").asText())
                .isEqualTo(Long.toString(Math.max(stableMessageFirst, stableMessageSecond)));
        assertThat(secondMessagePage.get(0).path("id").asText())
                .isEqualTo(Long.toString(Math.min(stableMessageFirst, stableMessageSecond)));

        var fileContent = "feature-api-multipart-content".getBytes(StandardCharsets.UTF_8);
        var uploaded = client.multipartWithCsrf(
                fileRoot, "journey.txt", "text/plain", fileContent, Map.of());
        assertCreated(uploaded);
        var fileId = text(uploaded.body(), "/data/id");
        assertThat(text(uploaded.body(), "/data/originalName")).isEqualTo("journey.txt");
        assertThat(uploaded.body().at("/data/size").asLong()).isEqualTo(fileContent.length);
        assertThat(text(uploaded.body(), "/data/sha256")).isEqualTo(sha256(fileContent));

        var metadata = client.get(fileRoot + "/" + fileId);
        assertOk(metadata);
        assertThat(text(metadata.body(), "/data/id")).isEqualTo(fileId);
        var downloaded = client.download(fileRoot + "/" + fileId + "/content");
        assertThat(downloaded.status()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(fileContent);
        assertThat(downloaded.contentType()).startsWith("text/plain");

        var archivedProject = client.postWithCsrf(
                projectsRoot + "/" + projectId + ":archive",
                json(Map.of("version", 1)), Map.of());
        assertOk(archivedProject);
        assertThat(text(archivedProject.body(), "/data/status")).isEqualTo("ARCHIVED");
        assertError(client.postWithCsrf(workRoot, json(Map.of(
                        "title", "Rejected archived-project task",
                        "assigneeMemberId", Long.parseLong(memberId),
                        "projectId", Long.parseLong(projectId)
                )), Map.of()), 409, "WORK_PROJECT_STATE_INVALID");

        var secondTenant = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/tenants",
                json(Map.of("code", "isolated", "name", "Isolated Tenant")),
                Map.of("Idempotency-Key", key()));
        assertOk(secondTenant);
        var secondTenantId = text(secondTenant.body(), "/data/id");
        assertThat(secondTenantId).isNotEqualTo(firstTenantId);

        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var switchedTenant = client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch", "{}", Map.of());
        assertOk(switchedTenant);
        assertThat(text(switchedTenant.body(), "/data/context/tenantId")).isEqualTo(secondTenantId);

        var isolatedTasks = client.get(workRoot
                + "?keyword=StableJourney&status=ALL&role=ALL&page=1&size=1");
        assertThat(pageItems(isolatedTasks, 1, 1, 0)).isEmpty();
        assertError(client.postWithCsrf(workRoot + "/" + taskId + ":complete", "{}", Map.of()),
                404, "WORK_TASK_NOT_FOUND");
        assertThat(pageItems(client.get(projectsRoot
                + "?status=ALL&page=1&size=20"), 1, 20, 0)).isEmpty();
        assertError(client.get(projectsRoot + "/" + projectId),
                404, "WORK_PROJECT_NOT_FOUND");
        assertThat(pageItems(client.get(reportsRoot
                + "?scope=ALL&status=ALL&page=1&size=20"), 1, 20, 0)).isEmpty();
        assertError(client.get(reportsRoot + "/" + reportId),
                404, "WORK_REPORT_NOT_FOUND");

        var isolatedInbox = client.get(eventRoot + "?status=ALL&page=1&size=1");
        assertThat(pageItems(isolatedInbox, 1, 1, 0)).isEmpty();
        var isolatedArchivedInbox = client.get(eventRoot + "?status=ARCHIVED&page=1&size=1");
        assertThat(pageItems(isolatedArchivedInbox, 1, 1, 0)).isEmpty();
        var isolatedUnread = client.get(eventRoot + "/unread-count");
        assertOk(isolatedUnread);
        assertThat(isolatedUnread.body().at("/data/unreadCount").asLong()).isZero();
        assertError(client.postWithCsrf(
                eventRoot + "/" + messageId + ":read", "{}", Map.of()),
                404, "EVENT_MESSAGE_NOT_FOUND");

        assertError(client.get(fileRoot + "/" + fileId), 404, "FILE_NOT_FOUND");

        var switchedBack = client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch", "{}", Map.of());
        assertOk(switchedBack);
        assertThat(text(switchedBack.body(), "/data/context/tenantId")).isEqualTo(firstTenantId);

        var reopenedProject = client.postWithCsrf(
                projectsRoot + "/" + projectId + ":reopen",
                json(Map.of("version", 2)), Map.of());
        assertOk(reopenedProject);
        assertThat(text(reopenedProject.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(reopenedProject.body().at("/data/version").asLong()).isEqualTo(3);

        var persistedSubmittedReport = client.get(reportsRoot + "/" + reportId);
        assertOk(persistedSubmittedReport);
        assertThat(text(persistedSubmittedReport.body(), "/data/completedWork"))
                .isEqualTo("Created and verified one shared Work task fact");
        assertThat(text(persistedSubmittedReport.body(), "/data/status")).isEqualTo("SUBMITTED");
        var reopenedReport = client.postWithCsrf(
                reportsRoot + "/" + reportId + ":reopen",
                json(Map.of("version", 3)), Map.of());
        assertOk(reopenedReport);
        assertThat(text(reopenedReport.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(reopenedReport.body().at("/data/version").asLong()).isEqualTo(4);

        var assigned = client.postWithCsrf(workRoot + "/" + taskId + ":assign",
                json(Map.of("assigneeMemberId", Long.parseLong(memberId))), Map.of());
        assertOk(assigned);
        assertThat(text(assigned.body(), "/data/assigneeMemberId")).isEqualTo(memberId);
        var completed = client.postWithCsrf(workRoot + "/" + taskId + ":complete", "{}", Map.of());
        assertOk(completed);
        assertThat(text(completed.body(), "/data/status")).isEqualTo("COMPLETED");
        var reopened = client.postWithCsrf(workRoot + "/" + taskId + ":reopen", "{}", Map.of());
        assertOk(reopened);
        assertThat(text(reopened.body(), "/data/status")).isEqualTo("OPEN");

        var reminderAt = Instant.now().plusMillis(1_200)
                .truncatedTo(ChronoUnit.MILLIS);
        var scheduledReminder = client.putWithCsrf(
                workRoot + "/" + taskId,
                json(Map.of(
                        "title", "Verify shared Work views",
                        "projectId", Long.parseLong(projectId),
                        "description", "Updated without replacing the task fact",
                        "dueAt", "2031-03-05T05:06:07Z",
                        "reminderAt", reminderAt.toString(),
                        "version", 5
                )), Map.of());
        assertOk(scheduledReminder);
        assertThat(scheduledReminder.body().at("/data/version").asLong())
                .isEqualTo(6);
        assertThat(text(scheduledReminder.body(), "/data/reminderAt"))
                .isEqualTo(reminderAt.toString());
        assertThat(text(scheduledReminder.body(), "/data/reminder/status"))
                .isEqualTo("PENDING");
        assertThat(scheduledReminder.body().at("/data/reminder/generation").asInt())
                .isOne();

        var waitMillis = Duration.between(
                Instant.now(), reminderAt.plusMillis(100)).toMillis();
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
        assertThat(workTaskReminderClaimWorker.runBatch(20)).isOne();
        assertThat(workTaskReminderClaimWorker.runBatch(20)).isZero();

        var deliveredTask = client.get(workRoot + "/" + taskId);
        assertOk(deliveredTask);
        assertThat(text(deliveredTask.body(), "/data/reminder/status"))
                .isEqualTo("SENT");
        assertThat(deliveredTask.body().at("/data/reminder/attemptCount").asInt())
                .isOne();
        assertThat(deliveredTask.body().at("/data/reminder/sentAt").isTextual())
                .isTrue();

        var inboxAfterReminder = pageItems(
                client.get(eventRoot + "?status=ALL&page=1&size=20"),
                1, 20, 5);
        var reminderMessages = StreamSupport.stream(
                        inboxAfterReminder.spliterator(), false)
                .filter(item -> "WORK_TASK_REMINDER".equals(
                        item.path("templateCode").asText()))
                .toList();
        assertThat(reminderMessages).hasSize(1);
        assertThat(reminderMessages.get(0).at("/target/type").asText())
                .isEqualTo("WORK_TASK");
        assertThat(reminderMessages.get(0).at("/target/id").asText())
                .isEqualTo(taskId);
        assertThat(reminderMessages.get(0).path("targetPath").asText())
                .isEqualTo("/systems/" + systemId + "/work/tasks/" + taskId);

        var nextReminderAt = Instant.now().plus(10, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.MILLIS);
        var rescheduledReminder = client.putWithCsrf(
                workRoot + "/" + taskId,
                json(Map.of(
                        "title", "Verify shared Work views",
                        "projectId", Long.parseLong(projectId),
                        "description", "Updated without replacing the task fact",
                        "dueAt", "2031-03-05T05:06:07Z",
                        "reminderAt", nextReminderAt.toString(),
                        "version", 6
                )), Map.of());
        assertOk(rescheduledReminder);
        assertThat(rescheduledReminder.body().at("/data/version").asLong())
                .isEqualTo(7);
        assertThat(rescheduledReminder.body().at("/data/reminder/generation").asInt())
                .isEqualTo(2);
        assertThat(text(rescheduledReminder.body(), "/data/reminder/status"))
                .isEqualTo("PENDING");

        var completedWithReminder = client.postWithCsrf(
                workRoot + "/" + taskId + ":complete", "{}", Map.of());
        assertOk(completedWithReminder);
        assertThat(completedWithReminder.body().at("/data/version").asLong())
                .isEqualTo(8);
        var completedReminderAt = completedWithReminder.body()
                .at("/data/reminderAt");
        assertThat(completedReminderAt.isMissingNode()
                || completedReminderAt.isNull())
                .isTrue();
        assertThat(text(completedWithReminder.body(), "/data/reminder/status"))
                .isEqualTo("CANCELLED");
        var reopenedWithoutReminder = client.postWithCsrf(
                workRoot + "/" + taskId + ":reopen", "{}", Map.of());
        assertOk(reopenedWithoutReminder);
        assertThat(reopenedWithoutReminder.body().at("/data/version").asLong())
                .isEqualTo(9);
        var reopenedReminderAt = reopenedWithoutReminder.body()
                .at("/data/reminderAt");
        assertThat(reopenedReminderAt.isMissingNode()
                || reopenedReminderAt.isNull())
                .isTrue();
        assertThat(text(reopenedWithoutReminder.body(), "/data/reminder/status"))
                .isEqualTo("CANCELLED");

        var read = client.postWithCsrf(eventRoot + "/" + messageId + ":read", "{}", Map.of());
        assertOk(read);
        assertThat(text(read.body(), "/data/status")).isEqualTo("READ");
        assertThat(read.body().at("/data/readAt").isTextual()).isTrue();
        var readAll = client.postWithCsrf(eventRoot + "/read-all", "{}", Map.of());
        assertOk(readAll);
        assertThat(readAll.body().at("/data/changedCount").asInt()).isEqualTo(3);
        var archived = client.postWithCsrf(eventRoot + "/" + messageId + ":archive", "{}", Map.of());
        assertOk(archived);
        assertThat(text(archived.body(), "/data/status")).isEqualTo("ARCHIVED");
        var currentInbox = pageItems(
                client.get(eventRoot + "?status=ALL&page=1&size=10"),
                1, 10, 4);
        assertThat(currentInbox).allMatch(item -> "READ".equals(item.path("status").asText()));
        var archivedInbox = pageItems(
                client.get(eventRoot + "?status=ARCHIVED&page=1&size=10"),
                1, 10, 2);
        assertContainsId(archivedInbox, messageId);
        assertContainsId(archivedInbox, seededArchivedMessage);

        var referenceBody = json(Map.of("targetType", "WORK_TASK", "targetId", taskId));
        var referenced = client.postWithCsrf(
                fileRoot + "/" + fileId + "/references", referenceBody, Map.of());
        assertOk(referenced);
        assertThat(referenced.body().at("/data/references")).hasSize(1);
        assertThat(text(referenced.body(), "/data/references/0/targetId")).isEqualTo(taskId);
        assertError(client.deleteWithCsrf(fileRoot + "/" + fileId, "{}", Map.of()),
                409, "FILE_STILL_REFERENCED");

        var unreferenced = client.deleteWithCsrf(
                fileRoot + "/" + fileId + "/references", referenceBody, Map.of());
        assertOk(unreferenced);
        assertThat(unreferenced.body().at("/data/references")).isEmpty();
        assertOk(client.deleteWithCsrf(fileRoot + "/" + fileId, "{}", Map.of()));
        assertError(client.get(fileRoot + "/" + fileId), 404, "FILE_NOT_FOUND");
    }

    private TestClient login() throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of(
                "account", ROOT_USERNAME,
                "password", ROOT_PASSWORD
        )), Map.of()));
        return client;
    }

    private long insertWorkTask(
            long systemId,
            long tenantId,
            long creatorMemberId,
            long assigneeMemberId,
            String title,
            String status,
            Instant timestamp
    ) {
        var taskId = idService.nextId();
        var inserted = jdbcTemplate.update("""
                INSERT INTO un_work_task (
                    id, system_id, tenant_id, creator_member_id, assignee_member_id,
                    title, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, systemId, tenantId, creatorMemberId, assigneeMemberId,
                title, status, Timestamp.from(timestamp), Timestamp.from(timestamp), 1L);
        assertThat(inserted).isOne();
        return taskId;
    }

    private String createTargetMember(
            long systemId,
            long tenantId,
            long ownerMemberId
    ) {
        var ownerAccountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM un_plat_member WHERE system_id=? AND id=?",
                Long.class, systemId, ownerMemberId);
        assertThat(ownerAccountId).isNotNull();
        var accountId = idService.nextId();
        var memberId = idService.nextId();
        var memberTenantId = idService.nextId();
        var suffix = Long.toUnsignedString(memberId, 36);
        var now = LocalDateTime.now();

        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_account "
                        + "(id,account_code,username,username_normalized,email,email_normalized,phone,"
                        + "display_name,locale,time_zone,status,last_login_at,created_at,created_by,"
                        + "updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,NULL,NULL,NULL,?,'zh-CN','Asia/Shanghai','ACTIVE',NULL,?,?,"
                        + "?,?,NULL,NULL,0)",
                accountId, "work_target_" + suffix, "work_target_" + suffix,
                "work_target_" + suffix, "Work Target", now, ownerAccountId,
                now, ownerAccountId)).isOne();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_member "
                        + "(id,system_id,account_id,member_code,display_name,default_tenant_id,status,"
                        + "joined_at,created_at,created_by,updated_at,updated_by,deleted_at,version) "
                        + "VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,NULL,0)",
                memberId, systemId, accountId, "work_target_" + suffix,
                "Work Target", tenantId, now, now, ownerAccountId, now,
                ownerAccountId)).isOne();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_member_tenant "
                        + "(id,system_id,member_id,tenant_id,status,granted_at,granted_by,expires_at,"
                        + "created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,'ACTIVE',?,?,NULL,?,?,?, ?,NULL,NULL,0)",
                memberTenantId, systemId, memberId, tenantId, now,
                ownerAccountId, now, ownerAccountId, now, ownerAccountId))
                .isOne();
        return Long.toString(memberId);
    }

    private long insertInboxMessage(long systemId, long tenantId, long memberId, String taskId) {
        return insertInboxMessage(
                systemId,
                tenantId,
                memberId,
                taskId,
                "Task assigned",
                "UNREAD",
                Instant.now());
    }

    private long insertInboxMessage(
            long systemId,
            long tenantId,
            long memberId,
            String taskId,
            String title,
            String status,
            Instant createdAt
    ) {
        var messageId = idService.nextId();
        var readAt = "UNREAD".equals(status) ? null : Timestamp.from(createdAt.plusSeconds(1));
        var archivedAt = "ARCHIVED".equals(status)
                ? Timestamp.from(createdAt.plusSeconds(2))
                : null;
        var inserted = jdbcTemplate.update("""
                INSERT INTO un_event_message (
                    id, system_id, tenant_id, sender_member_id, recipient_member_id,
                    template_code, title, body, target_type, target_id, status,
                    created_at, read_at, archived_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                messageId, systemId, tenantId, memberId, memberId,
                "WORK_TASK_ASSIGNED", title, "A work task is ready for review",
                "WORK_TASK", taskId, status, Timestamp.from(createdAt), readAt, archivedAt, 1L);
        assertThat(inserted).isOne();
        return messageId;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 200, got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 201, got %s: %s", response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static JsonNode pageItems(
            TestResponse response,
            int expectedPage,
            int expectedSize,
            long expectedTotal
    ) {
        assertOk(response);
        var data = response.body().path("data");
        assertThat(data.path("page").asInt()).isEqualTo(expectedPage);
        assertThat(data.path("size").asInt()).isEqualTo(expectedSize);
        assertThat(data.path("total").asLong()).isEqualTo(expectedTotal);
        assertThat(data.path("items").isArray()).isTrue();
        return data.path("items");
    }

    private static void assertContainsId(JsonNode items, long expectedId) {
        assertThat(StreamSupport.stream(items.spliterator(), false)
                .map(item -> item.path("id").asText()))
                .contains(Long.toString(expectedId));
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected %s, got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private record TestResponse(int status, JsonNode body) {
    }

    private record BinaryResponse(int status, byte[] body, String contentType) {
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        TestResponse get(String path) throws Exception {
            return sendJson(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }

        TestResponse deleteWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("DELETE", path, body, headers, true);
        }

        TestResponse multipartWithCsrf(
                String path,
                String filename,
                String mediaType,
                byte[] content,
                Map<String, String> headers
        ) throws Exception {
            var boundary = "----ExamineFeatureApi" + UUID.randomUUID().toString().replace("-", "");
            var output = new ByteArrayOutputStream();
            output.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + mediaType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(content);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-CSRF-Token", csrf())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()));
            headers.forEach(builder::header);
            return sendJson(builder);
        }

        BinaryResponse download(String path) throws IOException, InterruptedException {
            var response = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + path))
                            .header("X-Request-ID", "feature-api-test-" + key())
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(
                    response.statusCode(),
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse(""));
        }

        private TestResponse request(
                String method,
                String path,
                String body,
                Map<String, String> headers,
                boolean csrf
        ) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) {
                builder.header("X-CSRF-Token", csrf());
            }
            return sendJson(builder);
        }

        private TestResponse sendJson(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "feature-api-test-" + key()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("CSRF cookie is missing"));
        }
    }
}
