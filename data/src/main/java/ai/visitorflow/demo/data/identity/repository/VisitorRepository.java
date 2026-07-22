package ai.visitorflow.demo.data.identity.repository;

import ai.visitorflow.demo.data.identity.model.VisitorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitorRepository extends JpaRepository<VisitorEntity, Long> {
}
