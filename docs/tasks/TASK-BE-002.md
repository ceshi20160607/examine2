# TASK-BE-002

## meta

- task_id: TASK-BE-002
- type: implementation
- owner: backend
- phase: build
- parallel_group: G1
- depends_on: [TASK-BE-001]

## goal

Create backend business module POMs and generator scaffold used by base generation tasks.

## inputs

- `backend/pom.xml`
- `docs/tasks/plan.md`
- `.cursor/architecture/backend-structure.md`

## outputs

- `backend/examine-generator/**`
- `backend/examine-plat/pom.xml`
- `backend/examine-module/pom.xml`
- `backend/examine-flow/pom.xml`
- `backend/examine-message-log/pom.xml`
- `backend/examine-upload/pom.xml`
- `backend/examine-app/pom.xml`
- `backend/examine-ai-work/pom.xml`

## scope

### Do

- Add Maven modules only inside the declared output paths.
- Prepare generator conventions and package roots for base and manage layers.

### Do Not

- Do not generate entity/service code in this task.
- Do not implement business APIs.

## self_check_commands

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' -f backend/pom.xml -DskipTests compile
```

## acceptance

- [ ] Maven modules compile.
- [ ] Generator scaffold records table-prefix to module mapping.
- [ ] `task-accept` verdict is pass.

