/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.connector.ConnectorCategory.SECRET_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.encryption.Scope.ACCOUNT;
import static io.harness.encryption.Scope.ORG;
import static io.harness.encryption.Scope.PROJECT;
import static io.harness.filter.FilterType.CONNECTOR;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.ceawsconnector.CEAwsConfig.CEAwsConfigKeys;
import io.harness.connector.entities.embedded.ceazure.CEAzureConfig.CEAzureConfigKeys;
import io.harness.connector.entities.embedded.cek8s.CEK8sDetails.CEK8sDetailsKeys;
import io.harness.connector.entities.embedded.gcpccm.GcpCloudCostConfig.GcpCloudCostConfigKeys;
import io.harness.connector.entities.embedded.gcpkmsconnector.GcpKmsConnector;
import io.harness.connector.services.ConnectorFilterService;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.encryption.ScopeHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.utils.URLDecoderUtility;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

@OwnedBy(DX)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ConnectorFilterServiceImpl implements ConnectorFilterService {
  FilterService filterService;

  public static final String CREDENTIAL_TYPE_KEY = "credentialType";
  public static final String INHERIT_FROM_DELEGATE_STRING = "INHERIT_FROM_DELEGATE";

  @Override
  public Criteria createCriteriaFromConnectorListQueryParams(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filterIdentifier, String encodedSearchTerm, FilterPropertiesDTO filterProperties,
      Boolean includeAllConnectorsAccessibleAtScope, boolean isBuiltInSMDisabled) {
    if (isNotBlank(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }
    String searchTerm = URLDecoderUtility.getDecodedString(encodedSearchTerm);
    Criteria criteria = new Criteria();
    criteria.and(ConnectorKeys.accountIdentifier).is(accountIdentifier);
    if (includeAllConnectorsAccessibleAtScope != null && includeAllConnectorsAccessibleAtScope) {
      addCriteriaToReturnAllConnectorsAccessible(criteria, orgIdentifier, projectIdentifier);
    } else {
      criteria.and(ConnectorKeys.orgIdentifier).is(orgIdentifier);
      criteria.and(ConnectorKeys.projectIdentifier).is(projectIdentifier);
    }
    if (isBuiltInSMDisabled) {
      criteria.and(GcpKmsConnector.GcpKmsConnectorKeys.harnessManaged).ne(true);
    }
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));

    if (isEmpty(filterIdentifier) && filterProperties == null) {
      applySearchFilter(criteria, searchTerm);
      return criteria;
    }

    if (isNotBlank(filterIdentifier)) {
      populateSavedConnectorFilter(
          criteria, filterIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, searchTerm);
    } else {
      populateConnectorFiltersInTheCriteria(criteria, (ConnectorFilterPropertiesDTO) filterProperties, searchTerm);
    }
    return criteria;
  }

  private void applySearchFilter(Criteria criteria, String searchTerm) {
    if (isNotBlank(searchTerm)) {
      Criteria criteriaWithSearchTerm = getSearchTermFilter(criteria, searchTerm);
      criteria.andOperator(criteriaWithSearchTerm);
    }
  }

  private void addCriteriaToReturnAllConnectorsAccessible(
      Criteria criteria, String orgIdentifier, String projectIdentifier) {
    if (isNotBlank(projectIdentifier)) {
      Criteria orCriteria = new Criteria().orOperator(
          Criteria.where(ConnectorKeys.scope).is(PROJECT).and(ConnectorKeys.projectIdentifier).is(projectIdentifier),
          Criteria.where(ConnectorKeys.scope).is(ORG).and(ConnectorKeys.orgIdentifier).is(orgIdentifier),
          Criteria.where(ConnectorKeys.scope).is(ACCOUNT));
      criteria.andOperator(orCriteria);
    } else if (isNotBlank(orgIdentifier)) {
      Criteria orCriteria = new Criteria().orOperator(
          Criteria.where(ConnectorKeys.scope).is(ORG).and(ConnectorKeys.orgIdentifier).is(orgIdentifier),
          Criteria.where(ConnectorKeys.scope).is(ACCOUNT));
      criteria.andOperator(orCriteria);
    } else {
      criteria.and(ConnectorKeys.scope).is(ACCOUNT);
    }
  }

  private void populateSavedConnectorFilter(Criteria criteria, String filterIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String searchTerm) {
    FilterDTO connectorFilterDTO =
        filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, CONNECTOR);
    if (connectorFilterDTO == null) {
      throw new InvalidRequestException(String.format("Could not find a connector filter with the identifier %s, in %s",
          ScopeHelper.getScopeMessageForLogs(accountIdentifier, orgIdentifier, projectIdentifier)));
    }
    populateConnectorFiltersInTheCriteria(
        criteria, (ConnectorFilterPropertiesDTO) connectorFilterDTO.getFilterProperties(), searchTerm);
  }

  private void populateConnectorFiltersInTheCriteria(
      Criteria criteria, ConnectorFilterPropertiesDTO connectorFilter, String searchTerm) {
    if (connectorFilter == null) {
      return;
    }
    populateInFilter(criteria, ConnectorKeys.categories, connectorFilter.getCategories());
    populateInFilter(criteria, ConnectorKeys.type, connectorFilter.getTypes());
    populateNameDesciptionAndSearchTermFilter(criteria, connectorFilter.getConnectorNames(),
        connectorFilter.getDescription(), searchTerm, connectorFilter.getInheritingCredentialsFromDelegate());
    populateInFilter(criteria, ConnectorKeys.identifier, connectorFilter.getConnectorIdentifiers());
    populateInFilter(criteria, ConnectorKeys.connectionStatus, connectorFilter.getConnectivityStatuses());
    CcmConnectorFilter ccmConnectorFilter = connectorFilter.getCcmConnectorFilter();
    if (ccmConnectorFilter != null) {
      populateCcmFilters(criteria, ccmConnectorFilter);
    }
    populateTagsFilter(criteria, connectorFilter.getTags());
  }

  private void populateNameDesciptionAndSearchTermFilter(Criteria criteria, List<String> connectorNames,
      String description, String searchTerm, Boolean inheritingCredentialsFromDelegate) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria nameCriteria = getNameFilter(criteria, connectorNames);
    if (nameCriteria != null) {
      criteriaList.add(nameCriteria);
    }
    Criteria descriptionCriteria = getDescriptionFilter(criteria, description, searchTerm);
    if (descriptionCriteria != null) {
      criteriaList.add(descriptionCriteria);
    }
    Criteria inheritingFromDelegateCriteria =
        getInheritCredentialsFromDelegateFilter(criteria, inheritingCredentialsFromDelegate);
    if (inheritingFromDelegateCriteria != null) {
      criteriaList.add(inheritingFromDelegateCriteria);
    }
    Criteria searchCriteria = getSearchTermFilter(criteria, searchTerm);
    if (searchCriteria != null) {
      criteriaList.add(searchCriteria);
    }
    if (criteriaList.size() != 0) {
      criteria.andOperator(criteriaList.toArray(new Criteria[0]));
    }
  }

  private Criteria getNameFilter(Criteria criteria, List<String> connectorNames) {
    if (isEmpty(connectorNames)) {
      return null;
    }
    List<Criteria> criteriaForNames =
        connectorNames.stream()
            .map(
                name -> where(ConnectorKeys.name).regex(name, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS))
            .collect(Collectors.toList());
    return new Criteria().orOperator(criteriaForNames.toArray(new Criteria[0]));
  }

  private void populateTagsFilter(Criteria criteria, Map<String, String> tags) {
    if (isEmpty(tags)) {
      return;
    }
    criteria.and(ConnectorKeys.tags).in(TagMapper.convertToList(tags));
  }

  private Criteria getInheritCredentialsFromDelegateFilter(
      Criteria criteria, Boolean inheritingCredentialsFromDelegate) {
    if (inheritingCredentialsFromDelegate != null) {
      if (inheritingCredentialsFromDelegate.booleanValue()) {
        return addCriteriaForInheritingFromDelegate(criteria);
      } else {
        return addCriteriaForNotInheritingFromDelegate(criteria);
      }
    }
    return null;
  }

  private Criteria addCriteriaForNotInheritingFromDelegate(Criteria criteria) {
    return new Criteria().orOperator(
        where(CREDENTIAL_TYPE_KEY).exists(false), where(CREDENTIAL_TYPE_KEY).ne(INHERIT_FROM_DELEGATE_STRING));
  }

  private Criteria addCriteriaForInheritingFromDelegate(Criteria criteria) {
    return where(CREDENTIAL_TYPE_KEY).is(INHERIT_FROM_DELEGATE_STRING);
  }

  private String getPatternForMatchingAnyOneOf(List<String> wordsToBeMatched) {
    return StringUtils.collectionToDelimitedString(wordsToBeMatched, "|");
  }

  private Criteria getDescriptionFilter(Criteria criteria, String description, String searchTerm) {
    if (isBlank(description)) {
      return null;
    }
    String[] descriptionsWords = description.split(" ");
    if (isNotEmpty(descriptionsWords)) {
      String pattern = getPatternForMatchingAnyOneOf(Arrays.asList(descriptionsWords));
      return where(ConnectorKeys.description).regex(pattern, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS);
    }
    return null;
  }

  private Criteria getSearchTermFilter(Criteria criteria, String searchTerm) {
    if (isNotBlank(searchTerm)) {
      Criteria tagCriteria = createCriteriaForSearchingTag(searchTerm);
      return new Criteria().orOperator(
          where(ConnectorKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ConnectorKeys.identifier).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(ConnectorKeys.description).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          tagCriteria);
    }
    return null;
  }

  private Criteria createCriteriaForSearchingTag(String searchTerm) {
    String keyToBeSearched = searchTerm;
    String valueToBeSearched = "";
    if (searchTerm.contains(":")) {
      String[] split = searchTerm.split(":");
      keyToBeSearched = split[0];
      valueToBeSearched = split.length >= 2 ? split[1] : "";
    }
    return where(ConnectorKeys.tags).is(NGTag.builder().key(keyToBeSearched).value(valueToBeSearched).build());
  }

  public Criteria createCriteriaFromConnectorFilter(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, ConnectorType connectorType, ConnectorCategory category,
      ConnectorCategory sourceCategory, boolean isBuiltInSMDisabled) {
    Criteria criteria = new Criteria();
    criteria.and(ConnectorKeys.accountIdentifier).is(accountIdentifier);
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    criteria.and(ConnectorKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(ConnectorKeys.projectIdentifier).is(projectIdentifier);
    if (connectorType != null) {
      criteria.and(ConnectorKeys.type).is(connectorType.name());
    }

    if (category != null) {
      criteria.and(ConnectorKeys.categories).in(category);
    }
    if (sourceCategory != null && SECRET_MANAGER == sourceCategory) {
      criteria.and(ConnectorKeys.identifier).in(HARNESS_SECRET_MANAGER_IDENTIFIER);
    }
    if (isBuiltInSMDisabled) {
      criteria.and(GcpKmsConnector.GcpKmsConnectorKeys.harnessManaged).ne(true);
    }
    if (isNotBlank(searchTerm)) {
      Criteria seachCriteria = new Criteria().orOperator(where(ConnectorKeys.name).regex(searchTerm, "i"),
          where(NGCommonEntityConstants.IDENTIFIER_KEY).regex(searchTerm, "i"),
          where(NGCommonEntityConstants.TAGS_KEY).regex(searchTerm, "i"));
      criteria.andOperator(seachCriteria);
    }
    return criteria;
  }

  private void populateCcmFilters(Criteria criteria, CcmConnectorFilter ccmConnectorFilter) {
    populateAwsFilters(criteria, ccmConnectorFilter);
    populateAzureFilters(criteria, ccmConnectorFilter);
    populateGcpFilters(criteria, ccmConnectorFilter);
    populateK8sFilters(criteria, ccmConnectorFilter);
    populateInFilter(criteria, CEAzureConfigKeys.featuresEnabled, ccmConnectorFilter.getFeaturesEnabled());
  }

  private void populateAwsFilters(Criteria criteria, CcmConnectorFilter ccmConnectorFilter) {
    if (ccmConnectorFilter.getAwsAccountId() != null) {
      populateInFilter(criteria, CEAwsConfigKeys.awsAccountId, Arrays.asList(ccmConnectorFilter.getAwsAccountId()));
    }
  }

  private void populateGcpFilters(Criteria criteria, CcmConnectorFilter ccmConnectorFilter) {
    if (ccmConnectorFilter.getGcpProjectId() != null) {
      populateInFilter(criteria, GcpCloudCostConfigKeys.projectId, Arrays.asList(ccmConnectorFilter.getGcpProjectId()));
    }
  }

  private void populateK8sFilters(Criteria criteria, CcmConnectorFilter ccmConnectorFilter) {
    if (ccmConnectorFilter.getK8sConnectorRef() != null) {
      populateInFilter(criteria, CEK8sDetailsKeys.connectorRef, Arrays.asList(ccmConnectorFilter.getK8sConnectorRef()));
    }
  }

  private void populateAzureFilters(Criteria criteria, CcmConnectorFilter ccmConnectorFilter) {
    if (ccmConnectorFilter.getAzureSubscriptionId() != null) {
      populateInFilter(
          criteria, CEAzureConfigKeys.subscriptionId, Arrays.asList(ccmConnectorFilter.getAzureSubscriptionId()));
    }
    if (ccmConnectorFilter.getAzureTenantId() != null) {
      populateInFilter(criteria, CEAzureConfigKeys.tenantId, Arrays.asList(ccmConnectorFilter.getAzureTenantId()));
    }
  }
}
