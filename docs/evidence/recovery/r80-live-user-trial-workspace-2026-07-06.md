# R80 Live User Trial Workspace

Status: PASS

This is engineering evidence only. It seeds real trial data and keeps `gates.user_script_passed=false`.

## Trial Entries

- Admin: `admin / 123123aa`, entry `http://127.0.0.1:18131/#/platform`
- Runtime normal member: `r63_member_0706162707_4a00b6 / Aa123456!`, system `1118`, module `453`
- Runtime readonly member: `r63_readonly_0706162707_4a00b6 / Aa123456!`
- Workflow requester: `r3_requester_0706162803307_ca6efa / Aa123456!`, system `1121`, module `454`
- Workflow approver: `r3_approver_0706162803307_ca6efa / Aa123456!`, todo entry `http://127.0.0.1:18131/#/systems/1121/todos`

## Evidence

- Release verification passed before seeding.
- R63 configured runtime trial data was kept.
- R64 workflow/todo/message trial data was kept.
- R79 signoff boundary stayed false.

Result JSON: `docs/evidence/recovery/r80-live-user-trial-workspace-result.json`
