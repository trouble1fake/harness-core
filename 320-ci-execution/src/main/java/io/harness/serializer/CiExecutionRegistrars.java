/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.CIExecutionKryoRegistrar;
import io.harness.serializer.morphia.CIExecutionMorphiaRegistrar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class CiExecutionRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(CommonsRegistrars.kryoRegistrars)
          .addAll(CiBeansRegistrars.kryoRegistrars)
          .addAll(NGCoreRegistrars.kryoRegistrars)
          .addAll(ApiServicesRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
          .add(CIExecutionKryoRegistrar.class)
          .add(PipelineServiceUtilKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(CommonsRegistrars.morphiaRegistrars)
          .addAll(CiBeansRegistrars.morphiaRegistrars)
          .addAll(NGCoreRegistrars.morphiaRegistrars)
          .addAll(ApiServicesRegistrars.morphiaRegistrars)
          .addAll(SMCoreRegistrars.morphiaRegistrars)
          .addAll(ConnectorNextGenRegistrars.morphiaRegistrars)
          .add(CIExecutionMorphiaRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder()
          .addAll(PersistenceRegistrars.morphiaConverters)
          .addAll(DelegateTasksBeansRegistrars.morphiaConverters)
          .build();

  public static final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder()
          .addAll(OrchestrationRegistrars.springConverters)
          .build();
}
