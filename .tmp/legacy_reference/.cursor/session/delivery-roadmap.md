# Examine2 delivery roadmap

The authoritative machine contract is [`project-delivery.json`](project-delivery.json). This document is only a stable human-readable map and does not duplicate mutable execution state.

```text
Product phase 1: P1 Foundation, default admin, login, registration and system entry
  -> Product phase 2: P2 Platform/config/dashboard + P3 record runtime/management UI/permission
  -> Product phase 3: P4 Flow and external applications/OpenAPI
  -> Product phase 4: P5 Work/messages/files/import-export + P6 retained rich visual configuration/permission-safe AI
  -> Product phase 5: P7 hardening, operations and full project acceptance
```

P2 and P3 are two bounded delivery phases inside one product phase. Their combined exit is the first directly usable business system; configuration is not considered a useful product result until an authorized member can create, search and read back a record through the frozen list/right-detail UI.

Only P1 is expanded into tasks and cycles. Later phases retain source-backed atomic candidates but no implementation tasks until their predecessor result is demonstrated and their own UI/domain contracts are frozen.

P1 contains three maximum-four-hour demonstrations:

1. `/login`: the idempotently bootstrapped `admin` account signs in and reaches My Systems with authenticated readback; its password is stored only as a secure hash and is never emitted in evidence.
2. `/register`: new user creates an account and first system atomically and enters administration as super administrator.
3. `/platform/systems`: the owner signs in again, sees the authorized system and enters its context-correct system shell.

Performance and capacity work is not part of this roadmap. It can exist only as a separately authorized plan after complete functional delivery.
