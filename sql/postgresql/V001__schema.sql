-- effect: 1.0.0-beta fresh-install schema. Not an upgrade path.
-- Apply through the migration runner; never edit after this baseline is released.
SET search_path = public;
--
--


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: effect_catalogs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.effect_catalogs (
    id bigint NOT NULL,
    expires_at_ms bigint NOT NULL,
    rules_json text NOT NULL,
    operator_id bigint NOT NULL,
    created_at bigint NOT NULL
);


--
-- Name: TABLE effect_catalogs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.effect_catalogs IS 'Immutable monotonically versioned platform rules snapshots; not sticker packs or IM messages';


--
-- Name: effect_catalogs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.effect_catalogs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: effect_catalogs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.effect_catalogs_id_seq OWNED BY public.effect_catalogs.id;


--
-- Name: effect_resources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.effect_resources (
    id bigint NOT NULL,
    object_key text NOT NULL,
    sha256 character varying(64) NOT NULL,
    size bigint NOT NULL,
    metadata_json text NOT NULL,
    ready integer DEFAULT 0 NOT NULL,
    ever_published integer DEFAULT 0 NOT NULL,
    operator_id bigint NOT NULL,
    created_at bigint NOT NULL,
    CONSTRAINT effect_resources_ever_published_check CHECK ((ever_published = ANY (ARRAY[0, 1]))),
    CONSTRAINT effect_resources_ready_check CHECK ((ready = ANY (ARRAY[0, 1]))),
    CONSTRAINT effect_resources_size_check CHECK (((size >= 1) AND (size <= 2097152)))
);


--
-- Name: effect_resources_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.effect_resources_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: effect_resources_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.effect_resources_id_seq OWNED BY public.effect_resources.id;


--
-- Name: effect_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.effect_rules (
    id bigint NOT NULL,
    effect_version_id bigint NOT NULL,
    configuration_json text NOT NULL,
    enabled integer NOT NULL,
    rule_version bigint DEFAULT 1 NOT NULL,
    CONSTRAINT effect_rules_enabled_check CHECK ((enabled = ANY (ARRAY[0, 1]))),
    CONSTRAINT effect_rules_rule_version_check CHECK ((rule_version > 0))
);


--
-- Name: effect_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.effect_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: effect_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.effect_rules_id_seq OWNED BY public.effect_rules.id;


--
-- Name: effect_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.effect_versions (
    id bigint NOT NULL,
    effect_id character varying(128) NOT NULL,
    version integer NOT NULL,
    name character varying(128) NOT NULL,
    rights text NOT NULL,
    publication_state integer DEFAULT 1 NOT NULL,
    resource_id bigint,
    operator_id bigint NOT NULL,
    created_at bigint NOT NULL,
    CONSTRAINT effect_versions_publication_state_check CHECK ((publication_state = ANY (ARRAY[1, 2, 3]))),
    CONSTRAINT effect_versions_version_check CHECK ((version > 0))
);


--
-- Name: effect_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.effect_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: effect_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.effect_versions_id_seq OWNED BY public.effect_versions.id;


--
-- Name: effect_catalogs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_catalogs ALTER COLUMN id SET DEFAULT nextval('public.effect_catalogs_id_seq'::regclass);


--
-- Name: effect_resources id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_resources ALTER COLUMN id SET DEFAULT nextval('public.effect_resources_id_seq'::regclass);


--
-- Name: effect_rules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_rules ALTER COLUMN id SET DEFAULT nextval('public.effect_rules_id_seq'::regclass);


--
-- Name: effect_versions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_versions ALTER COLUMN id SET DEFAULT nextval('public.effect_versions_id_seq'::regclass);


--
-- Name: effect_catalogs effect_catalogs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_catalogs
    ADD CONSTRAINT effect_catalogs_pkey PRIMARY KEY (id);


--
-- Name: effect_resources effect_resources_object_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_resources
    ADD CONSTRAINT effect_resources_object_key_key UNIQUE (object_key);


--
-- Name: effect_resources effect_resources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_resources
    ADD CONSTRAINT effect_resources_pkey PRIMARY KEY (id);


--
-- Name: effect_rules effect_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_rules
    ADD CONSTRAINT effect_rules_pkey PRIMARY KEY (id);


--
-- Name: effect_versions effect_versions_effect_id_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_versions
    ADD CONSTRAINT effect_versions_effect_id_version_key UNIQUE (effect_id, version);


--
-- Name: effect_versions effect_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_versions
    ADD CONSTRAINT effect_versions_pkey PRIMARY KEY (id);


--
-- Name: effect_versions effect_versions_resource_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_versions
    ADD CONSTRAINT effect_versions_resource_id_key UNIQUE (resource_id);


--
-- Name: effect_rules effect_rules_effect_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_rules
    ADD CONSTRAINT effect_rules_effect_version_id_fkey FOREIGN KEY (effect_version_id) REFERENCES public.effect_versions(id);


--
-- Name: effect_versions effect_versions_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.effect_versions
    ADD CONSTRAINT effect_versions_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES public.effect_resources(id);


--
--


SET search_path = public;
