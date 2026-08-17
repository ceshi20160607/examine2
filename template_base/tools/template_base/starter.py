from __future__ import annotations

import json
import re
import tempfile
from dataclasses import dataclass
from pathlib import Path
from textwrap import dedent
from typing import Any

from .common import canonical_json, sha256_bytes
from .generator import GenerationError, tree_hashes, write_generated_tree
from .naming import java_path, pascal
from .rules import validate_rule


KEBAB = re.compile(r"^[a-z][a-z0-9-]*$")
PACKAGE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
SEMVER = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")


@dataclass(frozen=True)
class StarterIssue:
    path: str
    message: str

    def __str__(self) -> str:
        return f"{self.path}: {self.message}"


class StarterError(ValueError):
    def __init__(self, issues: list[StarterIssue]):
        super().__init__("\n".join(str(issue) for issue in issues))
        self.issues = issues


def load_starter(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise StarterError([StarterIssue("$", f"cannot read project starter: {error}")]) from error
    if not isinstance(data, dict):
        raise StarterError([StarterIssue("$", "must be an object")])
    return data


def _object(
    value: Any,
    path: str,
    required: set[str],
    issues: list[StarterIssue],
) -> dict[str, Any]:
    if not isinstance(value, dict):
        issues.append(StarterIssue(path, "must be an object"))
        return {}
    for key in sorted(required - value.keys()):
        issues.append(StarterIssue(f"{path}.{key}", "is required"))
    for key in sorted(value.keys() - required):
        issues.append(StarterIssue(f"{path}.{key}", "is not allowed"))
    return value


def _text(value: Any, path: str, issues: list[StarterIssue], pattern: re.Pattern[str] | None = None) -> str:
    if not isinstance(value, str) or not value.strip():
        issues.append(StarterIssue(path, "must be a non-blank string"))
        return ""
    if pattern is not None and not pattern.fullmatch(value):
        issues.append(StarterIssue(path, f"has invalid format: {value}"))
    return value


def _port(value: Any, path: str, issues: list[StarterIssue]) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or not 1024 <= value <= 65535:
        issues.append(StarterIssue(path, "must be an integer from 1024 to 65535"))
        return 0
    return value


def _relative(value: Any, path: str, source_path: Path, issues: list[StarterIssue]) -> Path | None:
    text = _text(value, path, issues)
    if not text:
        return None
    candidate = Path(text)
    if candidate.is_absolute():
        issues.append(StarterIssue(path, "must be relative to the project starter"))
        return None
    return (source_path.parent / candidate).resolve()


def validate_starter(data: dict[str, Any], starter_path: Path) -> list[StarterIssue]:
    issues = [StarterIssue(issue.path, issue.message) for issue in validate_rule(data, "project-starter.schema.json")]
    root = _object(
        data,
        "$",
        {"schemaVersion", "source", "project", "backend", "frontend", "local"},
        issues,
    )
    if root.get("schemaVersion") != 1:
        issues.append(StarterIssue("$.schemaVersion", "must equal 1"))

    source = _object(
        root.get("source"),
        "$.source",
        {"requirementsPath", "requirementsSha256"},
        issues,
    )
    requirements_path = _relative(source.get("requirementsPath"), "$.source.requirementsPath", starter_path, issues)
    requirements_hash = _text(source.get("requirementsSha256"), "$.source.requirementsSha256", issues)
    if requirements_path is not None:
        if not requirements_path.is_file():
            issues.append(StarterIssue("$.source.requirementsPath", f"file does not exist: {requirements_path}"))
        else:
            actual = sha256_bytes(requirements_path.read_bytes())
            if actual != requirements_hash:
                issues.append(StarterIssue("$.source.requirementsSha256", f"requirements changed; actual is {actual}"))
    project = _object(root.get("project"), "$.project", {"id", "name", "groupId", "artifactId", "basePackage"}, issues)
    _text(project.get("id"), "$.project.id", issues, KEBAB)
    _text(project.get("name"), "$.project.name", issues)
    _text(project.get("groupId"), "$.project.groupId", issues, PACKAGE)
    _text(project.get("artifactId"), "$.project.artifactId", issues, KEBAB)
    _text(project.get("basePackage"), "$.project.basePackage", issues, PACKAGE)

    backend = _object(root.get("backend"), "$.backend", {"java", "springBoot", "mybatisPlus", "flyway", "serverPort"}, issues)
    if backend.get("java") != 21:
        issues.append(StarterIssue("$.backend.java", "must equal 21"))
    _text(backend.get("springBoot"), "$.backend.springBoot", issues, SEMVER)
    _text(backend.get("mybatisPlus"), "$.backend.mybatisPlus", issues, SEMVER)
    _text(backend.get("flyway"), "$.backend.flyway", issues, SEMVER)
    backend_port = _port(backend.get("serverPort"), "$.backend.serverPort", issues)

    frontend = _object(
        root.get("frontend"),
        "$.frontend",
        {"node", "vue", "vite", "typescript", "antDesignVue", "vueRouter", "pinia", "vitest", "vitePluginVue"},
        issues,
    )
    node_version = _text(frontend.get("node"), "$.frontend.node", issues)
    if node_version and not node_version.startswith(">="):
        issues.append(StarterIssue("$.frontend.node", "must declare a minimum version with >="))
    for name in ("vue", "vite", "typescript", "antDesignVue", "vueRouter", "pinia", "vitest", "vitePluginVue"):
        _text(frontend.get(name), f"$.frontend.{name}", issues, SEMVER)

    local = _object(root.get("local"), "$.local", {"databaseName", "databaseUser", "mysqlPort", "redisPort"}, issues)
    _text(local.get("databaseName"), "$.local.databaseName", issues, re.compile(r"^[a-z][a-z0-9_]*$"))
    _text(local.get("databaseUser"), "$.local.databaseUser", issues, re.compile(r"^[a-z][a-z0-9_]*$"))
    mysql_port = _port(local.get("mysqlPort"), "$.local.mysqlPort", issues)
    redis_port = _port(local.get("redisPort"), "$.local.redisPort", issues)
    ports = [port for port in (backend_port, mysql_port, redis_port) if port]
    if len(ports) != len(set(ports)):
        issues.append(StarterIssue("$.local", "backend, MySQL and Redis ports must be different"))
    return issues


def require_valid_starter(data: dict[str, Any], starter_path: Path) -> None:
    issues = validate_starter(data, starter_path)
    if issues:
        raise StarterError(issues)


def _generated_header(comment: str = "//") -> str:
    return f"{comment} @generated by Template Base. Do not edit by hand.\n"


def _render_backend(data: dict[str, Any]) -> dict[str, str]:
    project = data["project"]
    backend = data["backend"]
    local = data["local"]
    package = project["basePackage"]
    package_path = java_path(package)
    application_class = f"{pascal(project['id'])}Application"
    database_password = f"{project['id'].replace('-', '_')}_local"
    pom = dedent(f"""\
        <?xml version="1.0" encoding="UTF-8"?>
        <!-- @generated by Template Base. Change dependency versions in tools/project-starter.json. -->
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <parent>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-parent</artifactId>
            <version>{backend['springBoot']}</version>
            <relativePath/>
          </parent>
          <groupId>{project['groupId']}</groupId>
          <artifactId>{project['artifactId']}</artifactId>
          <version>0.1.0-SNAPSHOT</version>
          <name>{project['name']}</name>
          <properties>
            <java.version>{backend['java']}</java.version>
            <mybatis-plus.version>{backend['mybatisPlus']}</mybatis-plus.version>
            <flyway.version>{backend['flyway']}</flyway.version>
          </properties>
          <dependencies>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-web</artifactId>
              <exclusions>
                <exclusion><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-tomcat</artifactId></exclusion>
              </exclusions>
            </dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-undertow</artifactId></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>
            <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot3-starter</artifactId><version>${{mybatis-plus.version}}</version></dependency>
            <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-jsqlparser</artifactId><version>${{mybatis-plus.version}}</version></dependency>
            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
            <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
            <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
            <dependency><groupId>org.testcontainers</groupId><artifactId>mysql</artifactId><scope>test</scope></dependency>
          </dependencies>
          <build>
            <resources>
              <resource><directory>src/main/resources</directory></resource>
              <resource>
                <directory>../db/migration</directory>
                <targetPath>db/migration</targetPath>
                <filtering>false</filtering>
              </resource>
            </resources>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>3.6.2</version>
                <executions>
                  <execution>
                    <id>require-java-21</id>
                    <goals><goal>enforce</goal></goals>
                    <configuration><rules><requireJavaVersion><version>[21,22)</version></requireJavaVersion></rules></configuration>
                  </execution>
                </executions>
              </plugin>
              <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
            </plugins>
          </build>
        </project>
        """)
    application = _generated_header() + dedent(f"""\
        package {package};

        import org.springframework.boot.SpringApplication;
        import org.springframework.boot.autoconfigure.SpringBootApplication;

        @SpringBootApplication
        public class {application_class} {{
            public static void main(String[] args) {{
                SpringApplication.run({application_class}.class, args);
            }}
        }}
        """)
    application_yml = _generated_header("#") + dedent(f"""\
        server:
          port: ${{APP_SERVER_PORT:{backend['serverPort']}}}
          shutdown: graceful
        spring:
          application:
            name: {project['artifactId']}
          datasource:
            url: ${{APP_DB_URL:jdbc:mysql://127.0.0.1:{local['mysqlPort']}/{local['databaseName']}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}}
            username: ${{APP_DB_USERNAME:{local['databaseUser']}}}
            password: ${{APP_DB_PASSWORD:{database_password}}}
          data:
            redis:
              host: ${{APP_REDIS_HOST:127.0.0.1}}
              port: ${{APP_REDIS_PORT:{local['redisPort']}}}
          flyway:
            enabled: true
            clean-disabled: true
            locations: classpath:db/migration
          lifecycle:
            timeout-per-shutdown-phase: 20s
        management:
          endpoints:
            web:
              exposure:
                include: health,info
          endpoint:
            health:
              probes:
                enabled: true
        """)
    return {
        "backend/pom.xml": pom,
        f"backend/src/main/java/{package_path}/{application_class}.java": application,
        "backend/src/main/resources/application.yml": application_yml,
    }


def _render_frontend(data: dict[str, Any]) -> dict[str, str]:
    project = data["project"]
    backend = data["backend"]
    versions = data["frontend"]
    package_json = canonical_json({
        "name": f"{project['id']}-web",
        "private": True,
        "version": "0.1.0",
        "type": "module",
        "engines": {"node": versions["node"]},
        "scripts": {
            "dev": "vite --host 0.0.0.0",
            "build": "vue-tsc --noEmit -p tsconfig.app.json && vite build",
            "test": "vitest run",
        },
        "dependencies": {
            "ant-design-vue": versions["antDesignVue"],
            "pinia": versions["pinia"],
            "vue": versions["vue"],
            "vue-router": versions["vueRouter"],
        },
        "devDependencies": {
            "@vitejs/plugin-vue": versions["vitePluginVue"],
            "@vue/tsconfig": "0.8.1",
            "@types/node": "26.2.0",
            "typescript": versions["typescript"],
            "vite": versions["vite"],
            "vitest": versions["vitest"],
            "vue-tsc": "3.3.9",
        },
    })
    index = dedent(f"""\
        <!doctype html>
        <html lang="zh-CN">
          <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>{project['name']}</title>
          </head>
          <body><div id="app"></div><script type="module" src="/src/main.ts"></script></body>
        </html>
        """)
    tsconfig = canonical_json({
        "files": [],
        "references": [{"path": "./tsconfig.app.json"}, {"path": "./tsconfig.node.json"}],
    })
    tsconfig_app = canonical_json({
        "extends": "@vue/tsconfig/tsconfig.dom.json",
        "include": ["env.d.ts", "src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"],
        "compilerOptions": {"tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo", "strict": True},
    })
    tsconfig_node = canonical_json({
        "include": ["vite.config.*", "vitest.config.*"],
        "compilerOptions": {
            "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
            "types": ["node"],
            "target": "ES2023",
            "module": "ESNext",
            "moduleResolution": "Bundler",
            "strict": True,
            "noEmit": True,
        },
    })
    vite = _generated_header() + dedent(f"""\
        import {{ fileURLToPath, URL }} from 'node:url'
        import vue from '@vitejs/plugin-vue'
        import {{ defineConfig }} from 'vite'

        export default defineConfig({{
          plugins: [vue()],
          resolve: {{ alias: {{ '@': fileURLToPath(new URL('./src', import.meta.url)) }} }},
          server: {{
            port: 15173,
            proxy: {{ '/api': {{ target: process.env.VITE_API_TARGET ?? 'http://127.0.0.1:{backend['serverPort']}' }} }},
          }},
        }})
        """)
    main = _generated_header() + dedent("""\
        import 'ant-design-vue/dist/reset.css'
        import './styles.css'
        import { createPinia } from 'pinia'
        import { createApp } from 'vue'
        import Antd from 'ant-design-vue'
        import App from './App.vue'
        import router from './router'

        createApp(App).use(createPinia()).use(router).use(Antd).mount('#app')
        """)
    router = _generated_header() + dedent("""\
        import { createRouter, createWebHistory } from 'vue-router'
        import { productRoutes } from './product/routes'
        import ProjectReadyView from './views/ProjectReadyView.vue'

        export default createRouter({
          history: createWebHistory(),
          routes: productRoutes.length > 0
            ? productRoutes
            : [{ path: '/', name: 'project-ready', component: ProjectReadyView }],
        })
        """)
    app = _generated_header("<!--").rstrip("\n") + " -->\n" + dedent("""\
        <script setup lang="ts"></script>

        <template><router-view /></template>
        """)
    ready_view = _generated_header("<!--").rstrip("\n") + " -->\n" + dedent(f"""\
        <template>
          <main class="ready-page">
            <section class="ready-card">
              <p class="eyebrow">TEMPLATE BASE / PROJECT READY</p>
              <h1>{project['name']} 新工程已初始化</h1>
              <p>当前只证明后端、前端和本地基础设施可以重复生成并构建，业务功能尚未声明完成。</p>
            </section>
          </main>
        </template>
        """)
    project_info = _generated_header() + dedent(f"""\
        export const generatedProjectInfo = {{
          projectId: '{project['id']}',
        }} as const
        """)
    project_test = _generated_header() + dedent("""\
        import { describe, expect, it } from 'vitest'
        import { generatedProjectInfo } from './projectInfo'

        describe('generated project identity', () => {
          it('uses the configured project id', () => {
            expect(generatedProjectInfo.projectId).toBe('__PROJECT_ID__')
          })
        })
        """).replace("__PROJECT_ID__", project["id"])
    styles = "/* @generated by Template Base. Do not edit by hand. */\n" + dedent("""\
        :root { color: #172033; background: #f2f5f9; font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif; }
        * { box-sizing: border-box; }
        body { margin: 0; min-width: 320px; min-height: 100vh; }
        .ready-page { min-height: 100vh; display: grid; place-items: center; padding: 32px; }
        .ready-card { width: min(680px, 100%); padding: 48px; border: 1px solid #dce3ed; border-radius: 20px; background: #fff; box-shadow: 0 24px 70px rgba(31, 52, 78, .1); }
        .ready-card h1 { margin: 8px 0 16px; font-size: 34px; }
        .ready-card p { color: #5d6879; line-height: 1.8; }
        .eyebrow { margin: 0; color: #1677ff !important; font-size: 12px; font-weight: 700; letter-spacing: .12em; }
        """)
    return {
        "frontend/package.json": package_json,
        "frontend/index.html": index,
        "frontend/tsconfig.json": tsconfig,
        "frontend/tsconfig.app.json": tsconfig_app,
        "frontend/tsconfig.node.json": tsconfig_node,
        "frontend/vite.config.ts": vite,
        "frontend/env.d.ts": '/// <reference types="vite/client" />\n',
        "frontend/src/main.ts": main,
        "frontend/src/router.ts": router,
        "frontend/src/App.vue": app,
        "frontend/src/styles.css": styles,
        "frontend/src/views/ProjectReadyView.vue": ready_view,
        "frontend/src/views/generated/projectInfo.ts": project_info,
        "frontend/src/views/generated/projectInfo.test.ts": project_test,
    }


def _render_local(data: dict[str, Any]) -> dict[str, str]:
    project = data["project"]
    backend = data["backend"]
    local = data["local"]
    database_password = f"{project['id'].replace('-', '_')}_local"
    root_password = f"{project['id'].replace('-', '_')}_root_local"
    compose = dedent(f"""\
        # @generated by Template Base. Local development dependencies only.
        name: {project['id']}
        services:
          mysql:
            image: mysql:8.4
            command: ["--log-bin-trust-function-creators=1"]
            environment:
              MYSQL_DATABASE: {local['databaseName']}
              MYSQL_USER: {local['databaseUser']}
              MYSQL_PASSWORD: {database_password}
              MYSQL_ROOT_PASSWORD: {root_password}
            ports: ["{local['mysqlPort']}:3306"]
            healthcheck:
              test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p{root_password}"]
              interval: 2s
              timeout: 3s
              retries: 30
            volumes: ["mysql-data:/var/lib/mysql"]
          redis:
            image: redis:7.4-alpine
            ports: ["{local['redisPort']}:6379"]
            healthcheck:
              test: ["CMD", "redis-cli", "ping"]
              interval: 2s
              timeout: 3s
              retries: 30
        volumes:
          mysql-data:
        """)
    env = dedent(f"""\
        APP_SERVER_PORT={backend['serverPort']}
        APP_DB_URL=jdbc:mysql://127.0.0.1:{local['mysqlPort']}/{local['databaseName']}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
        APP_DB_USERNAME={local['databaseUser']}
        APP_DB_PASSWORD={database_password}
        APP_REDIS_HOST=127.0.0.1
        APP_REDIS_PORT={local['redisPort']}
        VITE_API_TARGET=http://127.0.0.1:{backend['serverPort']}
        """)
    readme = dedent("""\
        # Generated application skeleton

        The engineering shell is generated from `tools/project-starter.json`. Product source stays in `backend/` and `frontend/`; project-specific Agent control files stay in `ai/`.

        Design SQL under `db/` first, prepare the Flyway inputs, apply them to MySQL, then use Template Base's MyBatis-Plus Generator to replace each module's `base` package. Handwritten product logic belongs under each module's `manage` package. Business storage and module boundaries come from the current requirement analysis and design, never from the starter.
        """)
    return {"deploy/compose.yaml": compose, "deploy/local.env.example": env, "README.md": readme}


def _render_control_plane(data: dict[str, Any]) -> dict[str, str]:
    project_name = data["project"]["name"]
    return {
        "ai/README.md": dedent(f"""\
            # {project_name} AI control plane

            This directory holds project-specific Agent inputs and outputs. Product source code belongs in `backend/` and `frontend/`; reusable deterministic capabilities belong in `template_base/`.

            The lead Agent owns overall status, dependency coordination and decision closure. Role Agents only work from assignments recorded under `agents/`. A test Agent joins every development cycle, and each cycle is limited to four hours.
            """),
        "ai/requirements/README.md": "# Requirements\n\nKeep the canonical-source intake, complete analysis plan, traceability and atomic use cases here.\n",
        "ai/architecture/README.md": "# Architecture\n\nKeep source-bound architecture decisions and capability ownership here.\n",
        "ai/planning/README.md": "# Planning\n\nCreate stages and <=4-hour cycles only after full requirements, tasks and table designs are ready.\n",
        "ai/agents/README.md": "# Agent assignments\n\nRecord each role's bounded assignment, inputs, outputs, dependencies and current state here.\n",
        "ai/decisions/README.md": "# Decisions\n\nRecord consequential project decisions, evidence, owner and supersession relationships here.\n",
        "ai/status/README.md": "# Status\n\nThe lead Agent keeps the single authoritative project status here. Unknown totals remain unknown; they are never guessed.\n",
        "ai/evidence/README.md": "# Evidence\n\nStore cycle-scoped build, test and acceptance evidence here.\n",
        "db/README.md": "# Database\n\nDesign versioned SQL under `base/` and `update/`. Use Template Base `db-prepare` to produce `base.sql`, `update.sql`, `all.sql` and Flyway inputs under `migration/`.\n",
        "db/base/README.md": "# Baseline SQL\n\nKeep the versioned baseline table design here.\n",
        "db/update/README.md": "# Update SQL\n\nKeep post-baseline versioned changes here.\n",
        "db/migration/README.md": "# Flyway inputs\n\nThis directory is derived by Template Base `db-prepare`; do not edit generated migrations directly.\n",
        "tools/README.md": "# Project inputs\n\nKeep project-specific generator configuration and thin command wrappers here. Reusable executable logic belongs in `template_base/`.\n",
    }


def render_starter(data: dict[str, Any]) -> dict[str, str]:
    rendered: dict[str, str] = {}
    for group in (_render_backend(data), _render_frontend(data), _render_local(data), _render_control_plane(data)):
        overlap = rendered.keys() & group.keys()
        if overlap:
            raise GenerationError(f"duplicate starter paths: {sorted(overlap)}")
        rendered.update(group)
    return dict(sorted(rendered.items()))


def generate_starter(data: dict[str, Any], starter_path: Path, output_root: Path) -> dict[str, Any]:
    require_valid_starter(data, starter_path)
    rendered = render_starter(data)
    return write_generated_tree(
        rendered,
        output_root,
        {
            "generatorVersion": "0.4.0-dev",
            "generatorKind": "project-starter",
            "projectId": data["project"]["id"],
            "starterSha256": sha256_bytes(starter_path.read_bytes()),
            "requirementsSha256": data["source"]["requirementsSha256"],
        },
    )


def check_starter_repeatable(data: dict[str, Any], starter_path: Path) -> dict[str, Any]:
    require_valid_starter(data, starter_path)
    with tempfile.TemporaryDirectory(prefix="template-base-starter-a-") as first_dir, tempfile.TemporaryDirectory(prefix="template-base-starter-b-") as second_dir:
        first = Path(first_dir)
        second = Path(second_dir)
        generate_starter(data, starter_path, first)
        generate_starter(data, starter_path, second)
        first_hashes = tree_hashes(first)
        second_hashes = tree_hashes(second)
        if first_hashes != second_hashes:
            differences = [
                name for name in sorted(first_hashes.keys() | second_hashes.keys())
                if first_hashes.get(name) != second_hashes.get(name)
            ]
            raise GenerationError(f"starter generation is not repeatable: {differences}")
        second_run = generate_starter(data, starter_path, first)
        if second_run["changed"]:
            raise GenerationError(f"second starter generation changed files: {second_run['changed']}")
        return {
            "repeatable": True,
            "fileCount": len(first_hashes),
            "treeSha256": sha256_bytes(canonical_json(first_hashes).encode("utf-8")),
        }

