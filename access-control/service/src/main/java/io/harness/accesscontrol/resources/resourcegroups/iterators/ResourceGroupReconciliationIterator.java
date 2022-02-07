/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups.iterators;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.accesscontrol.commons.iterators.AccessControlIteratorsConfig;
import io.harness.accesscontrol.commons.iterators.IteratorConfig;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO.ResourceGroupDBOKeys;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
public class ResourceGroupReconciliationIterator implements Handler<ResourceGroupDBO> {
  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final MongoTemplate mongoTemplate;
  private final HarnessResourceGroupService harnessResourceGroupService;
  private final ScopeService scopeService;
  private final IteratorConfig iteratorConfig;

  @Inject
  public ResourceGroupReconciliationIterator(AccessControlIteratorsConfig iteratorsConfig,
      PersistenceIteratorFactory persistenceIteratorFactory, @Named("mongoTemplate") MongoTemplate mongoTemplate,
      HarnessResourceGroupService harnessResourceGroupService, ScopeService scopeService) {
    this.iteratorConfig = iteratorsConfig.getResourceGroupIteratorConfig();
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
    this.harnessResourceGroupService = harnessResourceGroupService;
    this.scopeService = scopeService;
  }

  public void registerIterators() {
    Duration reconciliationInterval = Duration.ofSeconds(iteratorConfig.getTargetIntervalInSeconds());
    if (iteratorConfig.isEnabled()) {
      persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
          PersistenceIteratorFactory.PumpExecutorOptions.builder()
              .name("ResourceGroupReconciliationIterator")
              .poolSize(5)
              .interval(ofSeconds(5))
              .build(),
          ResourceGroupDBO.class,
          MongoPersistenceIterator.<ResourceGroupDBO, SpringFilterExpander>builder()
              .clazz(ResourceGroupDBO.class)
              .fieldName(ResourceGroupDBOKeys.nextReconciliationIterationAt)
              .targetInterval(reconciliationInterval.plus(Duration.ofMinutes(1)))
              .acceptableNoAlertDelay(reconciliationInterval.plus(reconciliationInterval))
              .handler(this)
              .schedulingType(REGULAR)
              .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
              .redistribute(true));
    }
  }

  @Override
  public void handle(ResourceGroupDBO entity) {
    harnessResourceGroupService.sync(entity.getIdentifier(),
        entity.getScopeIdentifier() == null ? null
                                            : scopeService.buildScopeFromScopeIdentifier(entity.getScopeIdentifier()));
  }
}
