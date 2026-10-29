CREATE TABLE effect_resources (
 id BIGSERIAL PRIMARY KEY, object_key TEXT NOT NULL UNIQUE, sha256 VARCHAR(64) NOT NULL,
 size BIGINT NOT NULL CHECK(size BETWEEN 1 AND 2097152), metadata_json TEXT NOT NULL,
 ready INT NOT NULL DEFAULT 0 CHECK(ready IN (0,1)),
 ever_published INT NOT NULL DEFAULT 0 CHECK(ever_published IN (0,1)),
 operator_id BIGINT NOT NULL, created_at BIGINT NOT NULL
);
CREATE TABLE effect_versions (
 id BIGSERIAL PRIMARY KEY, effect_id VARCHAR(128) NOT NULL, version INT NOT NULL CHECK(version>0),
 name VARCHAR(128) NOT NULL, rights TEXT NOT NULL,
 publication_state INT NOT NULL DEFAULT 1 CHECK(publication_state IN (1,2,3)),
 resource_id BIGINT UNIQUE REFERENCES effect_resources(id),
 operator_id BIGINT NOT NULL, created_at BIGINT NOT NULL, UNIQUE(effect_id,version)
);
CREATE TABLE effect_rules (
 id BIGSERIAL PRIMARY KEY, effect_version_id BIGINT NOT NULL REFERENCES effect_versions(id),
 configuration_json TEXT NOT NULL, enabled INT NOT NULL CHECK(enabled IN (0,1))
);
CREATE TABLE effect_catalogs (
 id BIGSERIAL PRIMARY KEY, expires_at_ms BIGINT NOT NULL,
 rules_json TEXT NOT NULL, operator_id BIGINT NOT NULL, created_at BIGINT NOT NULL
);
COMMENT ON TABLE effect_catalogs IS 'Immutable monotonically versioned platform rules snapshots; not sticker packs or IM messages';
