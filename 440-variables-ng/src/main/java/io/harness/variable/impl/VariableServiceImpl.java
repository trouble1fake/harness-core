/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.variable.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.harness.NgAutoLogContext;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.entities.Connector;
import io.harness.repositories.VariableRepository;
import io.harness.repositories.VariableCustomRepository;
import io.harness.variable.VariableDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.variable.VariableDTO;
import io.harness.variable.VariableInfoDTO;
import io.harness.variable.entities.Variable;
import io.harness.variable.services.VariableService;
import io.harness.variable.events.VariableCreateEvent;
import io.harness.exception.DuplicateFieldException;
import io.harness.outbox.OutboxEvent;
import io.harness.variable.VariableDTO;
import io.harness.variable.VariableInfoDTO;
import io.harness.variable.VariableResponseDTO;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.logging.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.git.model.ChangeType;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.variable.helper.VariableLogContext;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;

import javax.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.function.Supplier;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.git.model.ChangeType.ADD;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static java.lang.String.format;

@AllArgsConstructor(onConstructor = @__({ @Inject}))
public class VariableServiceImpl implements VariableService {

    private final ProjectService projectService;
    private final OrganizationService organizationService;
    private final VariableRepository variableRepository;

    private void validateThatAVariableWithThisNameDoesNotExists(
            VariableInfoDTO variableRequestDTO, String accountIdentifier) {
        return;
    }

    // implement
    public boolean validateTheIdentifierIsUnique(
            String accountIdentifier, String orgIdentifier, String projectIdentifier, String variableIdentifier) {
        String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, variableIdentifier);
        return !variableRepository.existsByFullyQualifiedIdentifier(
                fullyQualifiedIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
    }

    public VariableResponseDTO create(VariableDTO variable, String accountIdentifier) {
        return createInternal(variable, accountIdentifier);
    }

    private void checkThatTheProjectExists(String orgIdentifier, String projectIdentifier, String accountIdentifier) {
        if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
            final Optional<Project> project = projectService.get(accountIdentifier, orgIdentifier, projectIdentifier);
            if (!project.isPresent()) {
                throw new NotFoundException(String.format("project [%s] not found.", projectIdentifier));
            }
        }
    }

    private void checkThatTheOrganizationExists(String orgIdentifier, String accountIdentifier) {
        if (isNotEmpty(orgIdentifier)) {
            final Optional<Organization> organization = organizationService.get(accountIdentifier, orgIdentifier);
            if (!organization.isPresent()) {
                throw new NotFoundException(String.format("org [%s] not found.", orgIdentifier));
            }
        }
    }

    void assureThatTheProjectAndOrgExists(VariableDTO variableDTO, String accountIdentifier) {
        String orgIdentifier = variableDTO.getVariableInfo().getOrgIdentifier();
        String projectIdentifier = variableDTO.getVariableInfo().getProjectIdentifier();

        if (isNotEmpty(projectIdentifier)) {
            // its a project level variable
            if (isEmpty(orgIdentifier)) {
                throw new InvalidRequestException(
                        String.format("Project %s specified without the org Identifier", projectIdentifier));
            }
            checkThatTheProjectExists(orgIdentifier, projectIdentifier, accountIdentifier);
        } else if (isNotEmpty(orgIdentifier)) {
            // its a org level variable
            checkThatTheOrganizationExists(orgIdentifier, accountIdentifier);
        }
    }

    @VisibleForTesting
    void assurePredefined(VariableDTO variableDTO, String accountIdentifier) {
        assureThatTheProjectAndOrgExists(variableDTO, accountIdentifier);
    }

    private VariableResponseDTO createInternal(
            VariableDTO variable, String accountIdentifier) {
        try (AutoLogContext ignore1 = new NgAutoLogContext(variable.getVariableInfo().getProjectIdentifier(),
                variable.getVariableInfo().getOrgIdentifier(), accountIdentifier, OVERRIDE_ERROR);
             AutoLogContext ignore2 =
                     new VariableLogContext(variable.getVariableInfo().getIdentifier(), OVERRIDE_ERROR)) {
            VariableInfoDTO variableInfo = variable.getVariableInfo();

                VariableResponseDTO variableResponse;

            assurePredefined(variable, accountIdentifier);
            final boolean isIdentifierUnique = validateTheIdentifierIsUnique(accountIdentifier,
                    variableInfo.getOrgIdentifier(), variableInfo.getProjectIdentifier(), variableInfo.getIdentifier());
            if (!isIdentifierUnique) {
                throw new InvalidRequestException(
                        String.format("The variable with identifier %s already exists in the account %s, org %s, project %s",
                                variableInfo.getIdentifier(), accountIdentifier, variableInfo.getOrgIdentifier(),
                                variableInfo.getProjectIdentifier()));
            }
            validateThatAVariableWithThisNameDoesNotExists(variable.getVariableInfo(), accountIdentifier);
            Variable variableEntity = variableMapper.toVariable(variable, accountIdentifier);
            variableEntity.setTimeWhenVariableIsLastUpdated(System.currentTimeMillis());
            Variable savedVariableEntity = null;
            try {
                Supplier<OutboxEvent> supplier = ()
                            -> outboxService.save(new VariableCreateEvent(accountIdentifier, variable.getVariableInfo()));
                savedVariableEntity = variableRepository.save(variableEntity, variable, ADD, supplier);
            } catch (DuplicateKeyException ex) {
                throw new DuplicateFieldException(format("Variable [%s] already exists", variableEntity.getIdentifier()));
            }
            variableResponse = getResponse(
                    accountIdentifier, variableEntity.getOrgIdentifier(), variableEntity.getProjectIdentifier(), variableEntity);

                return variableResponse;

        } catch (Exception ex) {
            throw ex;
        }
    }
}
