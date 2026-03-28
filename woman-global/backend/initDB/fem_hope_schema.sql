--
-- PostgreSQL database dump
--

-- Dumped from database version 16.3
-- Dumped by pg_dump version 16.3

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

--
-- Name: postgis; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;


--
-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION postgis IS 'PostGIS geometry and geography spatial types and functions';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: administrators; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.administrators (
    id integer NOT NULL,
    first_name character varying,
    last_name character varying,
    email character varying,
    phone character varying NOT NULL,
    last_login timestamp with time zone,
    password character varying NOT NULL,
    user_id character varying NOT NULL
);


ALTER TABLE public.administrators OWNER TO app_user1;

--
-- Name: administrators_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.administrators_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.administrators_id_seq OWNER TO app_user1;

--
-- Name: administrators_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.administrators_id_seq OWNED BY public.administrators.id;


--
-- Name: alembic_version; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.alembic_version (
    version_num character varying(32) NOT NULL
);


ALTER TABLE public.alembic_version OWNER TO app_user1;

--
-- Name: chats; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.chats (
    id integer NOT NULL,
    quote_id integer,
    chat_code character varying
);


ALTER TABLE public.chats OWNER TO app_user1;

--
-- Name: chats_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.chats_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.chats_id_seq OWNER TO app_user1;

--
-- Name: chats_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.chats_id_seq OWNED BY public.chats.id;


--
-- Name: document_type; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.document_type (
    id integer NOT NULL,
    name character varying NOT NULL
);


ALTER TABLE public.document_type OWNER TO app_user1;

--
-- Name: document_type_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.document_type_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.document_type_id_seq OWNER TO app_user1;

--
-- Name: document_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.document_type_id_seq OWNED BY public.document_type.id;


--
-- Name: documents; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.documents (
    id integer NOT NULL,
    name character varying NOT NULL,
    verified boolean,
    verif_date timestamp with time zone,
    doc_type_id integer,
    user_id integer
);


ALTER TABLE public.documents OWNER TO app_user1;

--
-- Name: documents_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.documents_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.documents_id_seq OWNER TO app_user1;

--
-- Name: documents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.documents_id_seq OWNED BY public.documents.id;


--
-- Name: job_score_card; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.job_score_card (
    id integer NOT NULL,
    rate integer,
    job_id integer
);


ALTER TABLE public.job_score_card OWNER TO app_user1;

--
-- Name: job_score_card_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.job_score_card_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.job_score_card_id_seq OWNER TO app_user1;

--
-- Name: job_score_card_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.job_score_card_id_seq OWNED BY public.job_score_card.id;


--
-- Name: jobs; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.jobs (
    id integer NOT NULL,
    location character varying,
    price double precision,
    date timestamp with time zone,
    quote_id integer,
    payment_mode character varying,
    paid boolean,
    complete boolean
);


ALTER TABLE public.jobs OWNER TO app_user1;

--
-- Name: jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.jobs_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.jobs_id_seq OWNER TO app_user1;

--
-- Name: jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.jobs_id_seq OWNED BY public.jobs.id;


--
-- Name: live_location; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.live_location (
    id integer NOT NULL,
    latitude double precision,
    longitude double precision,
    geo public.geometry(Point),
    user_id integer
);


ALTER TABLE public.live_location OWNER TO app_user1;

--
-- Name: live_location_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.live_location_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.live_location_id_seq OWNER TO app_user1;

--
-- Name: live_location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.live_location_id_seq OWNED BY public.live_location.id;


--
-- Name: messages; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.messages (
    id integer NOT NULL,
    sender_id character varying,
    content character varying,
    "time" timestamp with time zone,
    chat_id integer
);


ALTER TABLE public.messages OWNER TO app_user1;

--
-- Name: messages_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.messages_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.messages_id_seq OWNER TO app_user1;

--
-- Name: messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.messages_id_seq OWNED BY public.messages.id;


--
-- Name: quotes; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.quotes (
    id integer NOT NULL,
    date timestamp with time zone,
    conversion_date timestamp with time zone,
    final_price double precision,
    client_id integer,
    provider_id integer,
    service_id integer,
    quote_code character varying,
    converted boolean
);


ALTER TABLE public.quotes OWNER TO app_user1;

--
-- Name: quotes_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.quotes_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.quotes_id_seq OWNER TO app_user1;

--
-- Name: quotes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.quotes_id_seq OWNED BY public.quotes.id;


--
-- Name: services; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.services (
    id integer NOT NULL,
    name character varying NOT NULL,
    description character varying,
    min_price double precision NOT NULL,
    service_pic character varying
);


ALTER TABLE public.services OWNER TO app_user1;

--
-- Name: services_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.services_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.services_id_seq OWNER TO app_user1;

--
-- Name: services_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.services_id_seq OWNED BY public.services.id;


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.subscriptions (
    id integer NOT NULL,
    user_id integer,
    service_id integer
);


ALTER TABLE public.subscriptions OWNER TO app_user1;

--
-- Name: subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.subscriptions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.subscriptions_id_seq OWNER TO app_user1;

--
-- Name: subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.subscriptions_id_seq OWNED BY public.subscriptions.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: app_user1
--

CREATE TABLE public.users (
    id integer NOT NULL,
    title character varying NOT NULL,
    first_name character varying NOT NULL,
    last_name character varying NOT NULL,
    phone character varying NOT NULL,
    email character varying NOT NULL,
    gender character varying,
    birth_date date,
    password character varying NOT NULL,
    country character varying,
    county character varying,
    area_name character varying,
    prof_pic character varying,
    nat_id character varying,
    "WH_badge" boolean,
    online boolean,
    service_provider boolean,
    reg_date timestamp with time zone,
    user_id character varying,
    emm_cont_1 character varying,
    emm_cont_2 character varying,
    reg_platform character varying
);


ALTER TABLE public.users OWNER TO app_user1;

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: app_user1
--

CREATE SEQUENCE public.users_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.users_id_seq OWNER TO app_user1;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: app_user1
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: administrators id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.administrators ALTER COLUMN id SET DEFAULT nextval('public.administrators_id_seq'::regclass);


--
-- Name: chats id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.chats ALTER COLUMN id SET DEFAULT nextval('public.chats_id_seq'::regclass);


--
-- Name: document_type id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.document_type ALTER COLUMN id SET DEFAULT nextval('public.document_type_id_seq'::regclass);


--
-- Name: documents id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.documents ALTER COLUMN id SET DEFAULT nextval('public.documents_id_seq'::regclass);


--
-- Name: job_score_card id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.job_score_card ALTER COLUMN id SET DEFAULT nextval('public.job_score_card_id_seq'::regclass);


--
-- Name: jobs id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.jobs ALTER COLUMN id SET DEFAULT nextval('public.jobs_id_seq'::regclass);


--
-- Name: live_location id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.live_location ALTER COLUMN id SET DEFAULT nextval('public.live_location_id_seq'::regclass);


--
-- Name: messages id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.messages ALTER COLUMN id SET DEFAULT nextval('public.messages_id_seq'::regclass);


--
-- Name: quotes id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.quotes ALTER COLUMN id SET DEFAULT nextval('public.quotes_id_seq'::regclass);


--
-- Name: services id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.services ALTER COLUMN id SET DEFAULT nextval('public.services_id_seq'::regclass);


--
-- Name: subscriptions id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.subscriptions ALTER COLUMN id SET DEFAULT nextval('public.subscriptions_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: administrators administrators_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.administrators
    ADD CONSTRAINT administrators_pkey PRIMARY KEY (id);


--
-- Name: alembic_version alembic_version_pkc; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.alembic_version
    ADD CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num);


--
-- Name: chats chats_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.chats
    ADD CONSTRAINT chats_pkey PRIMARY KEY (id);


--
-- Name: document_type document_type_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.document_type
    ADD CONSTRAINT document_type_pkey PRIMARY KEY (id);


--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);


--
-- Name: job_score_card job_score_card_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.job_score_card
    ADD CONSTRAINT job_score_card_pkey PRIMARY KEY (id);


--
-- Name: jobs jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);


--
-- Name: live_location live_location_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.live_location
    ADD CONSTRAINT live_location_pkey PRIMARY KEY (id);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: quotes quotes_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT quotes_pkey PRIMARY KEY (id);


--
-- Name: services services_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.services
    ADD CONSTRAINT services_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_live_location_geo; Type: INDEX; Schema: public; Owner: app_user1
--

CREATE INDEX idx_live_location_geo ON public.live_location USING gist (geo);


--
-- Name: chats chats_quote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.chats
    ADD CONSTRAINT chats_quote_id_fkey FOREIGN KEY (quote_id) REFERENCES public.quotes(id);


--
-- Name: documents documents_doc_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_doc_type_id_fkey FOREIGN KEY (doc_type_id) REFERENCES public.document_type(id);


--
-- Name: documents documents_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: job_score_card job_score_card_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.job_score_card
    ADD CONSTRAINT job_score_card_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id);


--
-- Name: jobs jobs_quote_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_quote_id_fkey FOREIGN KEY (quote_id) REFERENCES public.quotes(id);


--
-- Name: live_location live_location_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.live_location
    ADD CONSTRAINT live_location_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: messages messages_chat_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_chat_id_fkey FOREIGN KEY (chat_id) REFERENCES public.chats(id);


--
-- Name: quotes quotes_client_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT quotes_client_id_fkey FOREIGN KEY (client_id) REFERENCES public.users(id);


--
-- Name: quotes quotes_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT quotes_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES public.users(id);


--
-- Name: quotes quotes_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.quotes
    ADD CONSTRAINT quotes_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.services(id);


--
-- Name: subscriptions subscriptions_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.services(id);


--
-- Name: subscriptions subscriptions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: app_user1
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: pg_database_owner
--

GRANT ALL ON SCHEMA public TO app_user1;


--
-- PostgreSQL database dump complete
--

