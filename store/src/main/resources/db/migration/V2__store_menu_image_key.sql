-- 가게·메뉴 사진 (#110). S3 키만 저장하고 (예: stores/1/3f2a….jpg) 전체 URL 은 응답을 만들 때 붙인다.
-- NULL 이면 사진 없음
ALTER TABLE store ADD COLUMN image_key VARCHAR(255);
ALTER TABLE menu ADD COLUMN image_key VARCHAR(255);
