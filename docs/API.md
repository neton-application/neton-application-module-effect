# Effect API v1

PLATFORM-only independent service. Existing system admin and account realms,
session checks, permission evaluator and CSRF policy apply through the host.
No new authentication system or default identities. IDs are decimal strings.
Standard host response envelope applies; fields below describe envelope data.

## App Catalog

`GET /app/effect/catalog` requires account authentication. Returns the latest
database-published complete snapshot, not hardcoded rules or a per-message match.
Response uses `Cache-Control: private, no-store`. Clients load once per account/
Platform and refresh through their existing configuration lifecycle, never once
per message. Switching account/Platform clears the cached rule authority.

```json
{
  "schemaVersion":1,"revision":1,"expiresAtMs":1793000000000,
  "rules":[{
    "ruleId":"1","ruleVersion":2,"expiresAtMs":1793000000000,
    "effectId":"hearts","effectVersion":1,"keywords":["hello"],
    "channelScope":"all","placement":"fullscreen","priority":0,
    "cooldownMs":30000,"maxEventAgeMs":5000,
    "resource":{
      "resourceId":"1","url":"https://platform.example/app/effect/resource/get/1",
      "sha256":"<64 lowercase hex>","size":11052,"width":800,"height":800,
      "durationMs":5000,"profile":"pag-effect-v1"
    }
  }]
}
```

`channelScope` is `all|dm|group`. Only `fullscreen` is accepted. There are no
editable replacements, audio, regex, scripts, remote expressions or arbitrary
asset URLs. Keywords are release data. Clients perform NFC + trim + case-sensitive
whole-text exact matching of both messages and keywords; do not use substring
matching. Backend never reads chat messages or performs matching.

`ruleVersion` is a persisted monotonically incrementing version on every update
to the same rule ID. Recreating a deleted rule allocates a different ID. A catalog
revision is a new immutable snapshot and does not retroactively modify any prior
snapshot. Each rule expiry is the minimum of its optional configured absolute
expiry and snapshot expiry. Unknown schema/profile, expired snapshot/rule or
resource mismatch disables playback. No published snapshot yields revision 0,
expiry 0 and an empty rule list; expiry returns no playable rules.

Snapshots are **complete replacements**, not deltas: on refresh cancel loading/
playback and remove dedup authority for rules removed, disabled or changed in
version. A changed rule must not replay history. Effect offline atomically issues
a new snapshot removing its rules, without accidentally publishing unrelated
draft edits. Admin rule changes/deletes take effect only on explicit catalog
publication. There is no push revocation channel; offline clients can use an
existing authorized snapshot only until its absolute expiry. This is not an
immediate-offline-revocation guarantee.

`GET /app/effect/resource/get/{id}` anonymously reads only `ready=1 AND
ever_published=1` immutable bytes. Draft bytes are private. Previously published
bytes survive ordinary offline; rules no longer reference offline versions in a
new snapshot. Default content type is `application/octet-stream`, never an image
fallback URL or a sticker ID. Clients verify hash/size before submitting to PAG.

## Admin Routes

All routes below start with `/admin/effect`. Each action requires the exact
existing-system RBAC permission indicated; account/refresh/anonymous tokens cannot
become administrators.

| Routes | Permission |
| --- | --- |
| GET `/effect/list`, `/effect/get/{id}` | `effect:effect:query` |
| POST `/effect/create` | `effect:effect:create` |
| PUT `/effect/update` | `effect:effect:update` |
| DELETE `/effect/delete/{id}` | `effect:effect:delete` |
| POST `/effect/import` multipart `id,file` | `effect:effect:import` |
| POST `/effect/publish`, `/effect/offline` `{id}` | `effect:effect:publish` / `effect:effect:offline` |
| GET `/rule/list`, `/rule/get/{id}` | `effect:rule:query` |
| POST `/rule/create`, PUT `/rule/update`, DELETE `/rule/delete/{id}` | `effect:rule:create/update/delete` respectively |
| POST `/catalog/publish` `{expiresAtMs}` | `effect:catalog:publish` |
| GET `/catalog/get` | `effect:rule:query` |
| GET `/resource/get/{id}` private/no-store preview | `effect:effect:query` |
| POST `/resource/cleanup` | `effect:effect:delete` |

`EffectWrite`: `{id?,effectId,version,name,rights}`. Rights/license provenance and
nonblank name are mandatory (max 4000/128 characters). Logical IDs match
`[A-Za-z0-9_-]{1,128}`; positive versions strictly increase. Metadata is editable
only in draft. Draft may attach exactly one verified immutable PAG; same-byte
retry returns it, different bytes conflict. Published metadata/resources cannot
be overwritten; offline cannot republish. Create a new version. Rights text is
an audited operator assertion, not automated legal verification.

`RuleWrite`: `{id?,effectVersionId,keywords,channelScope?,placement?,priority?,
cooldownMs?,maxEventAgeMs?,enabled?,expiresAtMs?}`. `effectVersionId` is the server
row ID from effect create, not a sticker ID. Up to 256 rules, each 1..32 unique
nonblank already-trimmed keywords of max 128 characters. Priority -1000..1000;
cooldown 10000..86400000ms (default 30000), max event age 1..5000ms (default 5000),
enabled integer 0/1 (default 1). Optional expiry is a positive lossless JSON
integer Unix ms. Snapshot publication requires future expiry within seven days;
this is a conservative server validity ceiling, not a promise to play for seven
days. Client cooldown/concurrency/accessibility hard limits always take priority.

`EffectDto`: `{id,effectId,version,name,rights,publicationState,resource?}`;
states 1 draft / 2 published / 3 offline. `RuleDto`:
`{id,configuration:RuleWrite,ruleVersion}`. Lists are bounded to 1000 effects /
256 rules in this minimal mobile-first backend. Pagination/admin frontend are
not included. Rule publication selects only enabled rules referencing published,
ready, ever-published resources, sorts priority descending then numeric rule ID,
and inserts one snapshot under the same PostgreSQL lock as admin mutations.

## Inspector and Deployment

Only `pag-effect-v1`: <=2 MiB, <=8000ms, **each axis <=2048**, <=128 layers, native
depth <=8, frame rate 1..60. Nested compositions also obey the 2048-axis/8000ms
profile limits. Official codec and public runtime metadata must both be present,
untruncated, audio/video-free, and within the layer/composition budgets. The
official 4.5.98 CLI must actually parse and render
three sample frames; all required metadata must be present, supported tag level,
finite probes <=250ms (including cold setup). No audio, video, bitmap or text
compositions in the initial profile: the current CLI does not report texture/
font complexity, so they fail closed. These are coarse upload screening gates,
not device FPS/RSS acceptance or proof that every frame will decode.

`pag_inspector_executable` is a fixed absolute binary path invoked with generated
private temp input and fixed argument `effect`, no shell/PATH lookup. Environment
is restricted, wall timeout 10s, CPU 5s, output 16KiB, temp mode 0600, core dumps
disabled. Missing/unavailable/invalid inspector rejects before resource writes.
Deployment must add OS memory/network isolation; the POSIX child wrapper is not
an RSS/network sandbox. Use the actual prebuilt binary, not the build wrapper.

Provision `effect-private` without public bucket/static mapping/baseUrl;
`public_base_url` is a fixed externally reachable API origin with optional proxy
prefix. Apply new V001/V002 via embedded migrations, never startup DDL. Storage
writes are not rolled back by DB rollback: reservations commit first; cleanup
only deletes unattached never-published objects older than 24h under the same lock.
Rights, preview and official inspection precede explicit resource/catalog
publication. No seeded keywords, frontend, deployment or product-host registration
is implied by having these source routes.
