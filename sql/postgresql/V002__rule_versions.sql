ALTER TABLE effect_rules ADD COLUMN rule_version BIGINT NOT NULL DEFAULT 1 CHECK(rule_version>0);
