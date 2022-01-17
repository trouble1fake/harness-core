package io.harness.cdng.k8s;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

import java.util.Set;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("k8sRollingRollbackOutcome")
@JsonTypeName("k8sRollingRollbackOutcome")
@RecasterAlias("io.harness.cdng.k8s.K8sRollingRollbackOutcome")
public class K8sRollingRollbackOutcome implements Outcome, ExecutionSweepingOutput {
    Set<KubernetesResourceId> recreatedResourceIds;
}
