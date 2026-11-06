package com.baedalsak.delivery.location;

/**
 * 위치를 만든 쪽.
 */
public enum LocationSource {
    /** 서버 가상 GPS (VirtualGpsSimulator) */
    VIRTUAL,
    /** 라이더 앱 실제 GPS (PUT /v1/deliveries/{id}/location) */
    REAL
}
