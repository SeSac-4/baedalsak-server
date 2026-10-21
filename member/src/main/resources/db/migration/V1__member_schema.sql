CREATE TABLE member (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('CUSTOMER', 'OWNER', 'RIDER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE owner_profile (
    member_id            BIGINT PRIMARY KEY REFERENCES member (id),
    store_name_at_signup VARCHAR(100) NOT NULL
);

CREATE TABLE rider_profile (
    member_id    BIGINT PRIMARY KEY REFERENCES member (id),
    vehicle_type VARCHAR(10) NOT NULL CHECK (vehicle_type IN ('MOTORCYCLE', 'BICYCLE', 'WALK'))
);