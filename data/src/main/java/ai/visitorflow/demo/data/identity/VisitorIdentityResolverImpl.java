package ai.visitorflow.demo.data.identity;

import ai.visitorflow.demo.data.identity.model.IdentityLinkEntity;
import ai.visitorflow.demo.data.identity.model.VisitorEntity;
import ai.visitorflow.demo.data.identity.repository.IdentityLinkRepository;
import ai.visitorflow.demo.data.identity.repository.VisitorRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class VisitorIdentityResolverImpl implements VisitorIdentityResolver {
  private final VisitorRepository visitorRepository;
  private final IdentityLinkRepository identityLinkRepository;

  @Override
  @Transactional
  public long resolveForAssign(Long tenantId, String visitorId, Long anonVisitorId) {
    if (anonVisitorId != null) {
      if (hasText(visitorId)) {
        identityLinkRepository.insertIgnore(visitorId, anonVisitorId, Instant.now(), tenantId);
      }
      return anonVisitorId;
    }
    if (!hasText(visitorId)) {
      return mint(tenantId);
    }
    return identityLinkRepository.findByVisitorIdAndTenantId(visitorId, tenantId)
      .map(IdentityLinkEntity::getAnonVisitorId)
      .orElseGet(() -> mintAndLink(visitorId, tenantId));
  }

  @Override
  @Transactional(readOnly = true)
  public Long resolveForTrack(Long tenantId, String visitorId, Long anonVisitorId) {
    if (anonVisitorId != null) {
      return anonVisitorId;
    }
    if (!hasText(visitorId)) {
      return null;
    }
    return identityLinkRepository.findByVisitorIdAndTenantId(visitorId, tenantId)
      .map(IdentityLinkEntity::getAnonVisitorId)
      .orElse(null);
  }

  private Long mintAndLink(String visitorId, Long tenantId) {
    Long minted = mint(tenantId);
    int inserted = identityLinkRepository.insertIgnore(visitorId, minted, Instant.now(), tenantId);
    if (inserted == 1) {
      return minted;
    }
    return identityLinkRepository.findByVisitorIdAndTenantId(visitorId, tenantId)
      .map(IdentityLinkEntity::getAnonVisitorId)
      .orElse(minted);
  }

  private Long mint(Long tenantId) {
    return visitorRepository.save(VisitorEntity.builder().tenantId(tenantId).build()).getId();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
