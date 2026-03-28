BEGIN;

CREATE TABLE alembic_version (
    version_num VARCHAR(32) NOT NULL, 
    CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num)
);

-- Running upgrade  -> 3ad1aa05eb91

INSERT INTO alembic_version (version_num) VALUES ('3ad1aa05eb91') RETURNING alembic_version.version_num;

-- Running upgrade 3ad1aa05eb91 -> 31f41935c818

CREATE TABLE creator (
    id SERIAL NOT NULL, 
    first_name VARCHAR NOT NULL, 
    last_name VARCHAR NOT NULL, 
    title VARCHAR, 
    phone VARCHAR NOT NULL, 
    email VARCHAR NOT NULL, 
    password VARCHAR NOT NULL, 
    PRIMARY KEY (id)
);

CREATE TABLE seeker (
    id SERIAL NOT NULL, 
    first_name VARCHAR NOT NULL, 
    last_name VARCHAR NOT NULL, 
    phone VARCHAR NOT NULL, 
    email VARCHAR, 
    gender VARCHAR NOT NULL, 
    birth_date DATE, 
    password VARCHAR NOT NULL, 
    country VARCHAR NOT NULL, 
    county VARCHAR NOT NULL, 
    area_name VARCHAR NOT NULL, 
    prof_pic VARCHAR NOT NULL, 
    "WH_badge" BOOLEAN, 
    PRIMARY KEY (id)
);

UPDATE alembic_version SET version_num='31f41935c818' WHERE alembic_version.version_num = '3ad1aa05eb91';

-- Running upgrade 31f41935c818 -> e5e4cddf19b1

CREATE TABLE document_type (
    id SERIAL NOT NULL, 
    name VARCHAR NOT NULL, 
    PRIMARY KEY (id)
);

CREATE TABLE services (
    id SERIAL NOT NULL, 
    name VARCHAR NOT NULL, 
    description VARCHAR, 
    min_price FLOAT NOT NULL, 
    PRIMARY KEY (id)
);

CREATE TABLE users (
    id SERIAL NOT NULL, 
    title VARCHAR NOT NULL, 
    first_name VARCHAR NOT NULL, 
    last_name VARCHAR NOT NULL, 
    phone VARCHAR NOT NULL, 
    email VARCHAR NOT NULL, 
    gender VARCHAR, 
    birth_date DATE, 
    password VARCHAR NOT NULL, 
    country VARCHAR, 
    county VARCHAR, 
    area_name VARCHAR, 
    prof_pic VARCHAR, 
    nat_id VARCHAR, 
    "WH_badge" BOOLEAN, 
    online BOOLEAN, 
    service_provider BOOLEAN, 
    reg_date TIMESTAMP WITH TIME ZONE, 
    PRIMARY KEY (id)
);

CREATE TABLE documents (
    id SERIAL NOT NULL, 
    name VARCHAR NOT NULL, 
    verified BOOLEAN, 
    verif_date TIMESTAMP WITH TIME ZONE, 
    doc_type_id INTEGER, 
    user_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(doc_type_id) REFERENCES document_type (id), 
    FOREIGN KEY(user_id) REFERENCES users (id)
);

CREATE TABLE jobs (
    id SERIAL NOT NULL, 
    location VARCHAR, 
    price FLOAT, 
    date TIMESTAMP WITH TIME ZONE, 
    client_id INTEGER, 
    provider_id INTEGER, 
    service_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(client_id) REFERENCES users (id), 
    FOREIGN KEY(provider_id) REFERENCES users (id), 
    FOREIGN KEY(service_id) REFERENCES services (id)
);

CREATE TABLE subscriptions (
    id SERIAL NOT NULL, 
    user_id INTEGER, 
    service_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(service_id) REFERENCES services (id), 
    FOREIGN KEY(user_id) REFERENCES users (id)
);

CREATE TABLE job_score_card (
    id SERIAL NOT NULL, 
    rate INTEGER, 
    job_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(job_id) REFERENCES jobs (id)
);

DROP TABLE creator;

DROP TABLE seeker;

UPDATE alembic_version SET version_num='e5e4cddf19b1' WHERE alembic_version.version_num = '31f41935c818';

-- Running upgrade e5e4cddf19b1 -> a4c068867e7e

UPDATE alembic_version SET version_num='a4c068867e7e' WHERE alembic_version.version_num = 'e5e4cddf19b1';

-- Running upgrade a4c068867e7e -> 507dedd0aa37

UPDATE alembic_version SET version_num='507dedd0aa37' WHERE alembic_version.version_num = 'a4c068867e7e';

-- Running upgrade 507dedd0aa37 -> b39042c904b7

ALTER TABLE users ADD COLUMN user_id VARCHAR;

UPDATE alembic_version SET version_num='b39042c904b7' WHERE alembic_version.version_num = '507dedd0aa37';

-- Running upgrade b39042c904b7 -> 40f19c711ed2

CREATE TABLE live_location (
    id SERIAL NOT NULL, 
    latitude FLOAT, 
    longitude FLOAT, 
    geo geometry(POINT,-1), 
    user_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(user_id) REFERENCES users (id)
);

CREATE INDEX idx_live_location_geo ON live_location USING gist (geo);

UPDATE alembic_version SET version_num='40f19c711ed2' WHERE alembic_version.version_num = 'b39042c904b7';

-- Running upgrade 40f19c711ed2 -> 3c9c048582ff

ALTER TABLE users ADD COLUMN emm_cont_1 VARCHAR;

ALTER TABLE users ADD COLUMN emm_cont_2 VARCHAR;

ALTER TABLE users ADD COLUMN reg_platform VARCHAR;

UPDATE alembic_version SET version_num='3c9c048582ff' WHERE alembic_version.version_num = '40f19c711ed2';

-- Running upgrade 3c9c048582ff -> e4e8d91f7cf2

CREATE TABLE quotes (
    id SERIAL NOT NULL, 
    date TIMESTAMP WITH TIME ZONE, 
    conversion_date TIMESTAMP WITH TIME ZONE, 
    final_price FLOAT, 
    client_id INTEGER, 
    provider_id INTEGER, 
    service_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(client_id) REFERENCES users (id), 
    FOREIGN KEY(provider_id) REFERENCES users (id), 
    FOREIGN KEY(service_id) REFERENCES services (id)
);

CREATE TABLE chats (
    id SERIAL NOT NULL, 
    quote_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(quote_id) REFERENCES quotes (id)
);

CREATE TABLE messages (
    id SERIAL NOT NULL, 
    sender_id VARCHAR, 
    content VARCHAR, 
    time TIMESTAMP WITH TIME ZONE, 
    chat_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(chat_id) REFERENCES chats (id)
);

ALTER TABLE jobs ADD COLUMN quote_id INTEGER;

ALTER TABLE jobs DROP CONSTRAINT jobs_client_id_fkey;

ALTER TABLE jobs DROP CONSTRAINT jobs_provider_id_fkey;

ALTER TABLE jobs DROP CONSTRAINT jobs_service_id_fkey;

ALTER TABLE jobs ADD FOREIGN KEY(quote_id) REFERENCES quotes (id);

ALTER TABLE jobs DROP COLUMN client_id;

ALTER TABLE jobs DROP COLUMN service_id;

ALTER TABLE jobs DROP COLUMN provider_id;

UPDATE alembic_version SET version_num='e4e8d91f7cf2' WHERE alembic_version.version_num = '3c9c048582ff';

-- Running upgrade e4e8d91f7cf2 -> dee67f3797e7

ALTER TABLE chats ADD COLUMN chat_code VARCHAR;

ALTER TABLE quotes ADD COLUMN quote_code VARCHAR;

UPDATE alembic_version SET version_num='dee67f3797e7' WHERE alembic_version.version_num = 'e4e8d91f7cf2';

-- Running upgrade dee67f3797e7 -> 86b131e59cbd

ALTER TABLE jobs ADD COLUMN payment_mode VARCHAR;

ALTER TABLE jobs ADD COLUMN paid BOOLEAN;

ALTER TABLE jobs ADD COLUMN complete BOOLEAN;

ALTER TABLE quotes ADD COLUMN converted BOOLEAN;

UPDATE alembic_version SET version_num='86b131e59cbd' WHERE alembic_version.version_num = 'dee67f3797e7';

-- Running upgrade 86b131e59cbd -> 8df373a4673d

CREATE TABLE administrators (
    id SERIAL NOT NULL, 
    first_name VARCHAR, 
    last_name VARCHAR, 
    email VARCHAR, 
    phone VARCHAR NOT NULL, 
    last_login TIMESTAMP WITH TIME ZONE, 
    password VARCHAR NOT NULL, 
    PRIMARY KEY (id)
);

UPDATE alembic_version SET version_num='8df373a4673d' WHERE alembic_version.version_num = '86b131e59cbd';

-- Running upgrade 8df373a4673d -> 11ebbf750fa4

CREATE TABLE devices (
    id SERIAL NOT NULL, 
    device VARCHAR NOT NULL, 
    reg_token VARCHAR NOT NULL, 
    reg_token_date TIMESTAMP WITH TIME ZONE, 
    user_id INTEGER, 
    PRIMARY KEY (id), 
    FOREIGN KEY(user_id) REFERENCES users (id)
);

UPDATE alembic_version SET version_num='11ebbf750fa4' WHERE alembic_version.version_num = '8df373a4673d';

COMMIT;

