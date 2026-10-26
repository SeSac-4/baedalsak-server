package com.baedalsak.store.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.store.domain.Store;
import com.baedalsak.store.error.StoreErrorCode;
import com.baedalsak.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사장님의 가게 관리·메뉴 관리 권한 검증. 소유권은 서비스가 검증한다 (N4.2).
 */
@Component
@RequiredArgsConstructor
public class StoreOwnership {

    private final StoreRepository storeRepository;

    // 역할은 gateway 가 검증하지만, 가게·메뉴 관리는 사장님만 가능하도록 한 번 더 막는다
    public void requireOwner(LoginMember member) {
        if (member.role() != MemberRole.OWNER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    // 가게가 없으면 404 STORE_NOT_FOUND, 내 가게가 아니면 403
    public Store getOwnedStore(LoginMember member, Long storeId) {
        requireOwner(member);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
        if (!store.isOwnedBy(member.id())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return store;
    }
}
