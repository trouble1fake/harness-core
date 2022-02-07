package io.harness.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.testing.TestExecution;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(PL)
public class ResourceGroupComponentTest extends ResourceGroupTestBase {
  @Inject private Map<String, TestExecution> tests;

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void componentNotificationServiceTests() {
    for (Map.Entry<String, TestExecution> test : tests.entrySet()) {
      assertThatCode(() -> test.getValue().run()).as(test.getKey()).doesNotThrowAnyException();
      log.info("{} passed", test.getKey());
    }
  }
}
