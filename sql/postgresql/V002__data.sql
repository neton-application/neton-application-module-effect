-- effect: 1.0.0-beta initial data, for an empty baseline database only.
SET search_path = public;
SET standard_conforming_strings = on;
SELECT pg_catalog.setval('public.effect_resources_id_seq', 1, false);
SELECT pg_catalog.setval('public.effect_versions_id_seq', 1, false);
SELECT pg_catalog.setval('public.effect_rules_id_seq', 1, false);
SELECT pg_catalog.setval('public.effect_catalogs_id_seq', 1, false);
