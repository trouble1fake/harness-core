package io.harness.repositories.stepDetail;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionsInfo;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(HarnessTeam.PIPELINE)
@HarnessRepo
public interface NodeExecutionsInfoRepository extends PagingAndSortingRepository<NodeExecutionsInfo, String> {
  Optional<NodeExecutionsInfo> findByNodeExecutionId(String nodeExecutionId);
}
