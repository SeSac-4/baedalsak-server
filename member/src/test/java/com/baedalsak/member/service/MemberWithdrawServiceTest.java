package com.baedalsak.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.auth.CognitoAuthClient;
import com.baedalsak.member.auth.TokenBlacklistStore;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberRepository;

/**
 * 회원 탈퇴 (#188): 고객만 · DB 먼저 커밋 → Cognito 정리 → 지금 AT 블랙리스트.
 * DB 없이 돌리려고 저장소 · Cognito · Redis 는 가짜(mock), 트랜잭션은 커밋 · 롤백만 기록하는 가짜 관리자(SignupServiceTest 것)
 */
class MemberWithdrawServiceTest {

  private static final long CUSTOMER_ID = 1015L;
  private static final long EXPIRES_AT = 2_000_000_000L;

  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final CognitoAuthClient cognitoAuthClient = mock(CognitoAuthClient.class);
  private final TokenBlacklistStore tokenBlacklistStore = mock(TokenBlacklistStore.class);
  private final SignupServiceTest.FakeTransactionManager tx = new SignupServiceTest.FakeTransactionManager();
  private final MemberWithdrawService service = new MemberWithdrawService(
      memberRepository, cognitoAuthClient, tokenBlacklistStore, new TransactionTemplate(tx));

  /** DB 에서 읽어 온 회원처럼 (id 는 DB 가 채우는 값이라 직접 넣음) */
  private Member saved(long id, Role role) {
    Member member = new Member("hj.withdraw1@test.baedalsak.click", "탈퇴테스트", "010-1234-5678", role);
    ReflectionTestUtils.setField(member, "id", id);
    member.markPhoneVerified();
    when(memberRepository.findById(id)).thenReturn(Optional.of(member));
    return member;
  }

  @Test
  void 고객이면_개인정보를_지우고_커밋한_뒤_Cognito_정리와_AT_블랙리스트() {
    Member member = saved(CUSTOMER_ID, Role.CUSTOMER);
    // Cognito 를 부를 때 DB 는 이미 커밋돼 있어야 함 (Cognito 가 실패해도 탈퇴 상태가 남게)
    doAnswer(inv -> {
      assertThat(tx.committed).isTrue();
      return null;
    }).when(cognitoAuthClient).globalSignOut(CUSTOMER_ID);

    service.withdraw(CUSTOMER_ID, "jti-1", EXPIRES_AT);

    assertThat(member.getDeletedAt()).isNotNull();
    assertThat(member.getEmail()).isEqualTo("deleted-1015@withdrawn.invalid");   // 같은 이메일로 다시 가입 가능
    assertThat(member.getName()).isEqualTo("탈퇴한 회원");
    assertThat(member.getPhone()).isEqualTo("000-0000-0000");
    assertThat(member.isPhoneVerified()).isFalse();

    InOrder order = inOrder(cognitoAuthClient, tokenBlacklistStore);
    order.verify(cognitoAuthClient).globalSignOut(CUSTOMER_ID);   // 지우기 전에 전체 로그아웃
    order.verify(cognitoAuthClient).deleteUser(CUSTOMER_ID);
    order.verify(tokenBlacklistStore).add("jti-1", EXPIRES_AT);
  }

  @Test
  void 사장님_라이더는_403이고_아무것도_안_바뀜() {
    Member owner = saved(1101L, Role.OWNER);
    Member rider = saved(1201L, Role.RIDER);

    for (long id : new long[] {1101L, 1201L}) {
      assertThatThrownBy(() -> service.withdraw(id, "jti-1", EXPIRES_AT))
          .isInstanceOfSatisfying(BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.WITHDRAWAL_NOT_ALLOWED));
    }
    assertThat(owner.getDeletedAt()).isNull();
    assertThat(rider.getDeletedAt()).isNull();
    assertThat(tx.rolledBack).isTrue();
    verifyNoInteractions(cognitoAuthClient, tokenBlacklistStore);
  }

  @Test
  void seed_고객은_팀_공용_계정이라_403() {
    Member seed = saved(2L, Role.CUSTOMER);

    assertThatThrownBy(() -> service.withdraw(2L, "jti-1", EXPIRES_AT))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.WITHDRAWAL_NOT_ALLOWED));
    assertThat(seed.getDeletedAt()).isNull();
    verifyNoInteractions(cognitoAuthClient);
  }

  @Test
  void 없거나_이미_탈퇴한_회원은_404() {   // 탈퇴 회원은 @SQLRestriction 으로 조회에서 빠짐 → findById 가 비어 있음
    when(memberRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.withdraw(CUSTOMER_ID, "jti-1", EXPIRES_AT))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    verifyNoInteractions(cognitoAuthClient);
  }

  @Test
  void AT_정보가_없으면_블랙리스트만_건너뛰고_탈퇴는_됨() {
    Member member = saved(CUSTOMER_ID, Role.CUSTOMER);

    service.withdraw(CUSTOMER_ID, null, null);

    assertThat(member.getDeletedAt()).isNotNull();
    verify(cognitoAuthClient).deleteUser(CUSTOMER_ID);
    verify(tokenBlacklistStore, never()).add(anyString(), anyLong());
  }
}
