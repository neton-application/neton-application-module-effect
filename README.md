# PrivChat Effect Backend

Independent PLATFORM-owned PAG effects and exact-text rule catalog. No dependency
on sticker packs, installation or message storage. No frontend or mobile renderer
is included. The source includes production Neton routes, PostgreSQL persistence,
private StorageOperator uploads and immutable catalog snapshots, not seeded demo
keywords or a replacement identity provider.

See [API and deployment contract](docs/API.md).

## Assembly Status

The parent task has now added this sibling module to the product distribution's
settings, application dependency and KSP `neton.modules` registry, plus
`effect.conf`, the private storage source and sticker inspector configuration.
Those host edits belong to the parent task. Internal Neton module ID is `effect`
(hyphens are not valid in generated Kotlin packages); repository/project name is
`privchat-application-module-effect` / `module-effect`.

The generated `EffectModuleManifest` automatically invokes
`init.EffectRuntimeBootstrap.initialize(ctx)` before routes. Its embedded
migration source ID is also `effect`; the configuration file name is
`effect.conf` because bootstrap explicitly loads `moduleName="effect"`.
Apply embedded V001/V002 migrations through normal migration tooling before
starting the assembled host. Compilation/registration is not proof that the
normal product host or its deployment database has been started/migrated.
There is no startup DDL or fake/default admin identity.
No admin menu or role assignments are automatically seeded: grants must use the
existing system RBAC administration. Missing grants deny writes.

Config `effect.conf` requires `public_base_url` and optionally
`pag_inspector_executable`, a fixed absolute native libpag 4.5.98 binary path.
Without a real configured inspector, new PAG uploads fail closed. The initial
inspection adapter is independently packaged here to avoid a business dependency
on sticker; it uses the same native-owner CLI protocol and conservative budgets.
Extracting it into a neutral shared capability module remains a future refactor.

## Verification

`EffectRulesTest` tests rights/identity validation, bounded fullscreen rules,
expiry and camelCase wire shape. `EffectPostgresTest` is opt-in and requires
pre-migrated empty disposable tables at `127.0.0.1:55476/pag_backend_test`, an actual
official inspector and an actual PAG file. Only its
`EFFECT_POSTGRES_EXECUTED` marker proves the real lifecycle ran. A skipped
optional test is not acceptance.

Executed backend acceptance: 34 policy tests, 2 real PostgreSQL/official-libpag
lifecycle tests and a separate 54-check actual-route HTTP client passed. The
default 37-test opt-in-skipping run is not the evidence for those integrations.
The temporary HTTP host subsequently timed out at its 15-minute lifetime limit;
normal assembled-product startup remains unverified by this module task.
See the sibling sticker module's
`docs/PAG_BACKEND_ACCEPTANCE.md` and retained
`docs/evidence/pag-20261023/` logs for exact commands, markers and limitations.

Latest PG recheck uses original MIT Weey assets, not upstream LOGO/guide files:
sticker 256x256/2400ms and effect 720x1280/4000ms. Exactly two real PG tests passed
(250ms sticker, 338ms effect) in a newly created disposable database, with both
execution markers. See the sibling evidence directory's `original-mit/` logs.
