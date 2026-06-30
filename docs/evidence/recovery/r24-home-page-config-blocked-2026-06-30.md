# R24 Home Page Config Closure - Blocked Evidence

- Time: 2026-06-30 12:15 Asia/Shanghai
- Scope: FRC-1 missing product surfaces, focusing on system home page/page design configuration.
- Status: SUPERSEDED

This blocker was cleared by switching the current build toolchain to Temurin JDK 21.0.11 and rerunning R24. Current passing evidence is `docs/evidence/recovery/r24-home-page-config-2026-06-30.md` and `docs/evidence/recovery/r24-home-page-config-result.json`.

## Completed implementation work

- Added backend home page configuration endpoints in `WorkManagementController`.
- Added `HomePageConfigService` and `HomePageConfigModels` to persist system home page configuration through `un_module_work_config` using `config_type=SYSTEM_HOME_PAGE`.
- Added system-admin frontend binding for "首页与页面设计": save home page title/subtitle/visual tone/widgets and run publish check.
- Added runtime system dashboard binding to read `/work/home-page` and render the persisted title/subtitle/widgets.
- Added frontend API types and clients for home page config read/update/publish-check.
- Added admin permission protection for `/work/home-page-config`; runtime `/work/home-page` remains readable to system members.

## Verification

- Frontend build: PASS
  - Command: `D:\dev\nodejs24\npm.cmd run build`
  - Output: `tsc --noEmit && vite build` passed, generated `dist/assets/index-C1wA-Rqx.js`.
- Backend build: BLOCKED
  - Command: `JAVA_HOME=D:\dev\jdk21; D:\dev\maven\bin\mvn.cmd -pl examine-web -am package -DskipTests`
  - Failure module: `examine-ai-work`
  - Failure type: JDK compiler internal exception, not a normal Java diagnostic.
  - JDK: `21.0.10`
  - Key error: `java.lang.NoSuchMethodError: com.sun.tools.javac.jvm.ClassWriter$StackMapTableFrame.getInstance(...)`

## Acceptance impact

R24 cannot be accepted and FRC-1 cannot be marked closed until backend packaging passes and the deployed browser proves:

- system admin saves home page config,
- GET reads the same persisted config,
- runtime system dashboard renders the saved config,
- publish-check returns a visible pass/fail result,
- normal member can read runtime home page but cannot write admin config.
