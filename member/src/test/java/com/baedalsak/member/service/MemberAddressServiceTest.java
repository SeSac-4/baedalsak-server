package com.baedalsak.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.MemberAddress;
import com.baedalsak.member.dto.AddressRequest;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberAddressRepository;
import com.baedalsak.member.repository.MemberRepository;

/**
 * 배달 주소록 (#215): 고객만 · 3개 제한 · 기본 주소 1개 · 남의 주소 404.
 * DB 없이 돌리려고 저장소는 가짜(mock). 잠금은 "잠금 조회를 불렀는지"만 본다.
 */
class MemberAddressServiceTest {

  private static final long ME = 1015L;
  private static final LoginMember CUSTOMER = new LoginMember(ME, MemberRole.CUSTOMER);

  private final MemberAddressRepository addressRepository = mock(MemberAddressRepository.class);
  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final MemberAddressService service = new MemberAddressService(addressRepository, memberRepository);

  @BeforeEach
  void setUp() {
    when(memberRepository.findByIdForUpdate(ME)).thenReturn(Optional.of(mock(Member.class)));
    // DB 가 INSERT 하면서 id 를 채우는 것처럼
    when(addressRepository.save(any(MemberAddress.class))).thenAnswer(inv -> {
      MemberAddress address = inv.getArgument(0);
      ReflectionTestUtils.setField(address, "id", 10L);
      return address;
    });
  }

  private AddressRequest request(Boolean isDefault) {
    return new AddressRequest("집", "서울 성동구 성수이로 77", "302호", "성수동2가", 37.5446, 127.0559, isDefault);
  }

  /** DB 에 있는 내 주소처럼 (id 는 DB 가 채우는 값이라 직접 넣음) */
  private MemberAddress saved(long id, boolean isDefault) {
    MemberAddress address = new MemberAddress(ME, "집", "서울 성동구 성수이로 77", null, "성수동2가", 37.5, 127.0, isDefault);
    ReflectionTestUtils.setField(address, "id", id);
    return address;
  }

  private void myAddressesAre(MemberAddress... addresses) {
    when(addressRepository.findByMemberIdOrderByDefaultAddressDescIdDesc(ME)).thenReturn(List.of(addresses));
  }

  @Test
  void 첫_주소는_기본_주소로_저장() {
    myAddressesAre();

    service.addAddress(CUSTOMER, request(null));

    ArgumentCaptor<MemberAddress> captor = ArgumentCaptor.forClass(MemberAddress.class);
    verify(addressRepository).save(captor.capture());
    assertThat(captor.getValue().isDefaultAddress()).isTrue();
    verify(memberRepository).findByIdForUpdate(ME);   // 회원 행을 잠그고 셌는지
  }

  @Test
  void 두번째_주소는_기본이_아님() {
    myAddressesAre(saved(1, true));

    service.addAddress(CUSTOMER, request(null));

    ArgumentCaptor<MemberAddress> captor = ArgumentCaptor.forClass(MemberAddress.class);
    verify(addressRepository).save(captor.capture());
    assertThat(captor.getValue().isDefaultAddress()).isFalse();
  }

  @Test
  void 주소가_3개면_409_저장_안함() {
    myAddressesAre(saved(1, true), saved(2, false), saved(3, false));

    assertThatThrownBy(() -> service.addAddress(CUSTOMER, request(null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(MemberErrorCode.ADDRESS_LIMIT_EXCEEDED);
    verify(addressRepository, never()).save(any());
  }

  @Test
  void 고객이_아니면_403() {
    LoginMember owner = new LoginMember(101L, MemberRole.OWNER);

    assertThatThrownBy(() -> service.addAddress(owner, request(null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
    verify(addressRepository, never()).save(any());
  }

  @Test
  void 남의_주소면_404() {
    when(addressRepository.findByIdAndMemberId(9L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.removeAddress(CUSTOMER, 9L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.NOT_FOUND);
    verify(addressRepository, never()).delete(any());
  }

  @Test
  void 다른_주소를_기본으로_바꾸면_예전_기본은_해제() {
    MemberAddress oldDefault = saved(1, true);
    MemberAddress target = saved(2, false);
    myAddressesAre(oldDefault, target);
    when(addressRepository.findByIdAndMemberId(2L, ME)).thenReturn(Optional.of(target));

    service.updateAddress(CUSTOMER, 2L, request(true));

    assertThat(target.isDefaultAddress()).isTrue();
    assertThat(oldDefault.isDefaultAddress()).isFalse();
  }

  @Test
  void 기본_주소를_지우면_가장_최근_주소가_기본() {
    MemberAddress oldDefault = saved(1, true);
    MemberAddress newest = saved(3, false);
    MemberAddress older = saved(2, false);
    when(addressRepository.findByIdAndMemberId(1L, ME)).thenReturn(Optional.of(oldDefault));
    myAddressesAre(oldDefault, newest, older);   // 기본 먼저, 나머지 id 큰 순

    service.removeAddress(CUSTOMER, 1L);

    verify(addressRepository).delete(oldDefault);
    assertThat(newest.isDefaultAddress()).isTrue();
    assertThat(older.isDefaultAddress()).isFalse();
  }
}
