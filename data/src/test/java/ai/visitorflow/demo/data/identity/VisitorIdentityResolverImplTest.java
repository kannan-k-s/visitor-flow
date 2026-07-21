package ai.visitorflow.demo.data.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.visitorflow.demo.data.identity.model.IdentityLinkEntity;
import ai.visitorflow.demo.data.identity.model.VisitorEntity;
import ai.visitorflow.demo.data.identity.repository.IdentityLinkRepository;
import ai.visitorflow.demo.data.identity.repository.VisitorRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisitorIdentityResolverImplTest {
  @Mock
  private VisitorRepository visitorRepository;
  @Mock
  private IdentityLinkRepository identityLinkRepository;

  private VisitorIdentityResolverImpl resolver;

  @BeforeEach
  void setUp() {
    resolver = new VisitorIdentityResolverImpl(visitorRepository, identityLinkRepository);
  }

  @Test
  void mintsAnonWhenNoIdentityIsSupplied() {
    when(visitorRepository.save(any())).thenReturn(VisitorEntity.builder().id(41L).tenantId(2L).build());

    assertThat(resolver.resolveForAssign(2L, null, null)).isEqualTo(41L);
    verify(identityLinkRepository, never()).insertIgnore(any(), any(), any(), any());
  }

  @Test
  void trustsSuppliedAnonAndAppendsVisitorLink() {
    assertThat(resolver.resolveForAssign(2L, "visitor-a", 51L)).isEqualTo(51L);
    verify(identityLinkRepository).insertIgnore(any(), any(), any(), any());
    verify(visitorRepository, never()).save(any());
  }

  @Test
  void reusesExistingVisitorLinkWithoutMinting() {
    when(identityLinkRepository.findByVisitorIdAndTenantId("visitor-a", 2L)).thenReturn(Optional.of(
      IdentityLinkEntity.builder().tenantId(2L).visitorId("visitor-a").anonVisitorId(61L).build()
    ));

    assertThat(resolver.resolveForAssign(2L, "visitor-a", null)).isEqualTo(61L);
    verify(visitorRepository, never()).save(any());
  }

  @Test
  void trackLookupNeverMintsAnIdentity() {
    when(identityLinkRepository.findByVisitorIdAndTenantId("unknown", 2L)).thenReturn(Optional.empty());

    assertThat(resolver.resolveForTrack(2L, "unknown", null)).isNull();
    verify(visitorRepository, never()).save(any());
  }
}
