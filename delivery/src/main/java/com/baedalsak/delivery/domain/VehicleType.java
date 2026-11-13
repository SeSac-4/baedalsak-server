package com.baedalsak.delivery.domain;

/**
 * 라이더 운송수단. 배차 수락 때 member 에서 받아 배달에 스냅샷으로 저장한다 (가상 GPS 속도 · 도착 예정 시각에 쓴다).
 * 이름은 member 의 VehicleType 과 같아야 한다.
 */
public enum VehicleType {
    WALK, BICYCLE, MOTORCYCLE;

    /** 모르는 값이거나 비어 있으면 쓰는 기본값 */
    public static final VehicleType DEFAULT = MOTORCYCLE;

    /**
     * @param value member 가 보낸 값
     * @return 운송수단. null · 모르는 값이면 null
     */
    public static VehicleType parse(String value) {
        if (value == null) {
            return null;
        }
        for (VehicleType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * @param type 배달에 저장된 운송수단 (null 가능)
     * @return null 이면 기본값(오토바이)
     */
    public static VehicleType orDefault(VehicleType type) {
        return type == null ? DEFAULT : type;
    }
}
