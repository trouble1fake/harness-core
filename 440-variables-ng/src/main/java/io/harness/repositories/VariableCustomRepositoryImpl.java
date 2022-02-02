/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.variable.entities.Variable.VARIABLE_COLLECTION_NAME;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.variable.VariableDTO;
import io.harness.variable.entities.Variable;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.helper.EntityDistinctElementHelper;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;
import io.harness.ng.core.utils.NGYamlUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@GitSyncableHarnessRepo
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PL)
public class VariableCustomRepositoryImpl implements VariableCustomRepository {
    private MongoTemplate mongoTemplate;
    private GitAwarePersistence gitAwarePersistence;

    @Override
    public Page<Variable> findAll(Criteria criteria, Pageable pageable, boolean getDistinctIdentifiers) {
        return null;
    }

    @Override
    public Page<Variable> findAll(
            Criteria criteria, Pageable pageable, String projectIdentifier, String orgIdentifier, String accountIdentifier) {
        return null;
    }

    @Override
    public Variable update(Criteria criteria, Update update, ChangeType changeType, String projectIdentifier,
                            String orgIdentifier, String accountIdentifier) {
        return null;
    }

    @Override
    public Variable update(Criteria criteria, Update update) {
        return null;
    }

    @Override
    public UpdateResult updateMultiple(Query query, Update update) {
        return null;
    }

    @Override
    public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
        return null;
    }

    @Override
    public Optional<Variable> findByFullyQualifiedIdentifierAndDeletedNot(String fullyQualifiedIdentifier,
                                                                           String projectIdentifier, String orgIdentifier, String accountIdentifier, boolean notDeleted) {
        return null;
    }

    @Override
    public boolean existsByFullyQualifiedIdentifier(
            String fullyQualifiedIdentifier, String projectIdentifier, String orgIdentifier, String accountId) {
        return false;
    }

    @Override
    public Variable save(Variable objectToSave, VariableDTO yaml) {
        return mongoTemplate.save(objectToSave);
//        return gitAwarePersistence.save(objectToSave, NGYamlUtils.getYamlString(yaml), ChangeType.ADD, Variable.class);
    }

    @Override
    public Variable save(Variable objectToSave, ChangeType changeType) {
        return mongoTemplate.save(objectToSave);
//        return gitAwarePersistence.save(objectToSave, changeType, Variable.class);
    }

    @Override
    public Variable save(Variable objectToSave, VariableDTO variableDTO, ChangeType changeType) {
        return mongoTemplate.save(objectToSave);
//        return gitAwarePersistence.save(objectToSave, NGYamlUtils.getYamlString(variableDTO), changeType, Variable.class);
    }

    @Override
    public Variable save(Variable objectToSave, VariableDTO variableDTO, ChangeType changeType, Supplier functor) {
        return mongoTemplate.save(objectToSave);
//        return gitAwarePersistence.save(
//                objectToSave, NGYamlUtils.getYamlString(variableDTO), changeType, Variable.class, functor);
    }

    @Override
    public Optional<Variable> findOne(Criteria criteria, String repo, String branch) {
        return null;
    }

    @Override
    public long count(Criteria criteria) {
        return mongoTemplate.count(new Query(criteria), Variable.class);
    }
}
