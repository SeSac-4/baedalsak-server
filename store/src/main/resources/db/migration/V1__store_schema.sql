-- store 서비스 schema (설계서 '테이블 정의서' 탭 store_db)
-- 다른 서비스 ID(owner_id)는 값만 보관하고 FK 를 걸지 않는다.
-- 이미 적용(머지)된 뒤에는 이 파일을 고치지 말고 V2__... 를 추가한다 (docs 4-1).

-- 가게. 사장님 1명 = 가게 1개 (owner_id UNIQUE)
CREATE TABLE store (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id         BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    category         VARCHAR(20)  NOT NULL,
    address          VARCHAR(255) NOT NULL,
    min_order_amount INTEGER      NOT NULL,
    delivery_fee     INTEGER      NOT NULL,
    operation_status VARCHAR(10)  NOT NULL DEFAULT 'PAUSED',   -- 등록 직후 임시 중지 (1.2.1)
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT store_owner_uq UNIQUE (owner_id),
    CONSTRAINT store_category_ck CHECK (category IN ('KOREAN', 'SNACK', 'CHICKEN', 'CHINESE', 'PIZZA', 'CAFE_DESSERT')),
    CONSTRAINT store_min_order_amount_ck CHECK (min_order_amount >= 0),
    CONSTRAINT store_delivery_fee_ck CHECK (delivery_fee >= 0),
    CONSTRAINT store_operation_status_ck CHECK (operation_status IN ('OPEN', 'PAUSED'))
);

CREATE INDEX store_category_idx ON store (category);

-- 요일별 영업시간. 가게당 7행 (0=일 … 6=토), 등록 시 09:00~22:00 으로 생성 (1.2.1)
CREATE TABLE business_hour (
    store_id    BIGINT   NOT NULL REFERENCES store (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    open_time   TIME,                                         -- 휴무면 NULL
    close_time  TIME,
    is_closed   BOOLEAN  NOT NULL DEFAULT false,

    PRIMARY KEY (store_id, day_of_week),
    CONSTRAINT business_hour_day_ck CHECK (day_of_week BETWEEN 0 AND 6),
    CONSTRAINT business_hour_time_ck CHECK (is_closed OR (open_time IS NOT NULL AND close_time IS NOT NULL))
);

-- 메뉴. 삭제는 soft delete (주문 이력이 menu_id 를 참조)
CREATE TABLE menu (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    BIGINT       NOT NULL REFERENCES store (id),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price       INTEGER      NOT NULL,
    sale_status VARCHAR(10)  NOT NULL DEFAULT 'ON_SALE',
    deleted_at  TIMESTAMPTZ,                                  -- NULL 이면 활성
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT menu_price_ck CHECK (price > 0),
    CONSTRAINT menu_sale_status_ck CHECK (sale_status IN ('ON_SALE', 'SOLD_OUT'))
);

CREATE INDEX menu_store_active_idx ON menu (store_id) WHERE deleted_at IS NULL;
