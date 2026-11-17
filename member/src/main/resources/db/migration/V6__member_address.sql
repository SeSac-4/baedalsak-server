-- 고객 배달 주소록 (#215). 고객당 최대 3개, 기본 주소 1개 (개수 · 기본 주소 규칙은 서비스가 회원 행 잠금으로 지킴)
-- 주소 · 동 · 좌표는 프론트가 우편번호 검색 + 카카오맵 지오코딩으로 구해 보낸다 (member 는 카카오를 부르지 않음)
-- 좌표는 홈 헤더에서 고른 주소로 가게 목록 반경 조회(#210)를 하므로 필수
CREATE TABLE member_address (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id      BIGINT           NOT NULL REFERENCES member (id),
    label          VARCHAR(20),
    address        VARCHAR(200)     NOT NULL,
    address_detail VARCHAR(50),
    dong           VARCHAR(50)      NOT NULL,
    lat            DOUBLE PRECISION NOT NULL,
    lng            DOUBLE PRECISION NOT NULL,
    is_default     BOOLEAN          NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_member_address_member_id ON member_address (member_id);
