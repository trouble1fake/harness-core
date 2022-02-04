/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.expressions.AmbianceExpressionEvaluator;
import io.harness.expression.field.dummy.DummyOrchestrationField;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class AmbianceExpressionEvaluatorTest extends OrchestrationTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;

  @Before
  public void setup() {
    when(planExecutionService.get(anyString())).thenReturn(null);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithoutExpressions() {
    DummyB dummyB1 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c11").build())
                         .cVal2(DummyOrchestrationField.createValueField(DummyC.builder().strVal("c12").build()))
                         .strVal1("b11")
                         .strVal2(DummyOrchestrationField.createValueField("b12"))
                         .intVal1(11)
                         .intVal2(DummyOrchestrationField.createValueField(12))
                         .build();
    DummyB dummyB2 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c21").build())
                         .cVal2(DummyOrchestrationField.createValueField(DummyC.builder().strVal("c22").build()))
                         .strVal1("b21")
                         .strVal2(DummyOrchestrationField.createValueField("b22"))
                         .intVal1(21)
                         .intVal2(DummyOrchestrationField.createValueField(22))
                         .build();
    DummyA dummyA = DummyA.builder()
                        .bVal1(dummyB1)
                        .bVal2(DummyOrchestrationField.createValueField(dummyB2))
                        .strVal1("a1")
                        .strVal2(DummyOrchestrationField.createValueField("a2"))
                        .build();

    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(ImmutableMap.of("obj", DummyOrchestrationField.createValueField(dummyA)));

    validateExpression(evaluator, "bVal1.cVal1.strVal", "c11");
    validateExpression(evaluator, "bVal1.cVal2.strVal", "c12");
    validateExpression(evaluator, "bVal1.strVal1", "b11");
    validateExpression(evaluator, "bVal1.strVal2", "b12");
    validateExpression(evaluator, "bVal1.intVal1", 11);
    validateExpression(evaluator, "bVal1.intVal2", 12);
    validateExpression(evaluator, "bVal2.cVal1.strVal", "c21");
    validateExpression(evaluator, "bVal2.cVal2.strVal", "c22");
    validateExpression(evaluator, "bVal2.strVal1", "b21");
    validateExpression(evaluator, "bVal2.strVal2", "b22");
    validateExpression(evaluator, "bVal2.intVal1", 21);
    validateExpression(evaluator, "bVal2.intVal2", 22);
    validateExpression(evaluator, "strVal1", "a1");
    validateExpression(evaluator, "strVal2", "a2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithExpressions() {
    DummyB dummyB1 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c11").build())
                         .cVal2(DummyOrchestrationField.createExpressionField("<+c12>"))
                         .strVal1("b11")
                         .strVal2(DummyOrchestrationField.createValueField("b12"))
                         .intVal1(11)
                         .intVal2(DummyOrchestrationField.createValueField(12))
                         .build();
    DummyB dummyB2 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c21").build())
                         .cVal2(DummyOrchestrationField.createExpressionField("<+c22>"))
                         .strVal1("<+b21>")
                         .strVal2(DummyOrchestrationField.createExpressionField("<+b22>"))
                         .intVal1(21)
                         .intVal2(DummyOrchestrationField.createExpressionField("<+i22>"))
                         .build();
    DummyA dummyA = DummyA.builder()
                        .bVal1(dummyB1)
                        .bVal2(DummyOrchestrationField.createValueField(dummyB2))
                        .strVal1("a1")
                        .strVal2(DummyOrchestrationField.createValueField("a2"))
                        .build();

    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                             .put("obj", DummyOrchestrationField.createValueField(dummyA))
                                             .put("c12", "finalC12")
                                             .put("c22", "finalC22")
                                             .put("b21", "finalB21")
                                             .put("i22", 222)
                                             .build());

    validateSingleExpression(evaluator, "bVal1CVal1.strVal", "c11", false);
    validateExpression(evaluator, "bVal1.cVal1.strVal", "c11");
    validateExpression(evaluator, "bVal1.cVal2.strVal", "finalC12", true);
    validateExpression(evaluator, "bVal1.strVal1", "b11");
    validateExpression(evaluator, "bVal1.strVal2", "b12");
    validateExpression(evaluator, "bVal1.intVal1", 11);
    validateExpression(evaluator, "bVal1.intVal2", 12);
    validateExpression(evaluator, "bVal2.cVal1.strVal", "c21");
    validateExpression(evaluator, "bVal2.cVal2.strVal", "finalC22", true);
    validateExpression(evaluator, "bVal2.strVal1", "finalB21", true);
    validateSingleExpression(evaluator, "bVal2.strVal2", "null", true);
    validateSingleExpression(evaluator,
        "obj."
            + "bVal2.strVal2",
        null, false);
    assertThat(evaluator.evaluateExpression("<+bVal2.strVal2>")).isEqualTo(null);
    validateExpression(evaluator, "bVal2.intVal1", 21);
    validateExpression(evaluator, "bVal2.intVal2", 222, true);
    validateExpression(evaluator, "strVal1", "a1");
    validateExpression(evaluator, "strVal2", "a2");
  }

  private void validateExpression(EngineExpressionEvaluator evaluator, String expression, Object expected) {
    validateExpression(evaluator, expression, expected, false);
  }

  private void validateExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    validateExpressionWithObjExpression(evaluator, expression, expected, skipEvaluate);
  }

  private void validateExpressionWithObjExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    validateSingleExpression(evaluator, expression, expected, skipEvaluate);
    validateSingleExpression(evaluator, "obj." + expression, expected, skipEvaluate);
  }

  private void validateSingleExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    expression = "<+" + expression + ">";
    assertThat(evaluator.renderExpression(expression)).isEqualTo(String.valueOf(expected));
    if (skipEvaluate) {
      return;
    }
    assertThat(evaluator.evaluateExpression(expression)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testParameterFieldResolution() {
    DummyD dummyD = DummyD.builder().dummyD(ParameterField.createExpressionField(true, "<+c>", null, false)).build();

    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(ImmutableMap.of("a", "str1", "b", 100, "c",
        DummyD.builder()
            .strVal("p")
            .iVal(ParameterField.createExpressionField(true, "<+b>", null, false))
            .strVal2(ParameterField.createExpressionField(true, "<+a>", null, true))
            .dummyD(ParameterField.createExpressionField(true, "<+d>", null, false))
            .build(),
        "d",
        DummyD.builder().strVal("q").strVal2(ParameterField.createExpressionField(true, "<+a>", null, true)).build()));

    Pair<Map<String, Object>, Object> pair = executeResolve(evaluator, dummyD);
    Object resp = pair.getRight();
    assertThat(resp).isNotNull();
    assertThat(resp).isInstanceOf(Map.class);

    DummyD out = RecastOrchestrationUtils.fromMap(pair.getLeft(), DummyD.class);
    assertThat(out).isNotNull();

    ParameterField<DummyD> innerPF = out.getDummyD();
    assertThat(innerPF).isNotNull();
    assertThat(innerPF.isExpression()).isFalse();

    DummyD inner = innerPF.getValue();
    assertThat(inner).isNotNull();
    assertThat(inner.getStrVal()).isEqualTo("p");
    assertThat(inner.getIVal().getValue()).isEqualTo(100);
    assertThat(inner.getStrVal2().getValue()).isEqualTo("str1");

    ParameterField<DummyD> inner2PF = inner.getDummyD();
    assertThat(innerPF).isNotNull();
    assertThat(innerPF.isExpression()).isFalse();

    DummyD inner2 = inner2PF.getValue();
    assertThat(inner2).isNotNull();
    assertThat(inner2.getStrVal()).isEqualTo("q");
    assertThat(inner.getStrVal2().getValue()).isEqualTo("str1");
  }

  private Pair<Map<String, Object>, Object> executeResolve(EngineExpressionEvaluator evaluator, Object o) {
    Map<String, Object> docOriginal = RecastOrchestrationUtils.toMap(o);
    evaluator.resolve(docOriginal, false);

    // using recaster to obtain deep copy of the object
    Map<String, Object> docCopy = RecastOrchestrationUtils.toMap(o);

    return Pair.of(docOriginal, NodeExecutionUtils.resolveObject(docCopy));
  }

  @Value
  @Builder
  public static class DummyA {
    DummyB bVal1;
    DummyOrchestrationField<DummyB> bVal2;
    String strVal1;
    DummyOrchestrationField<String> strVal2;
  }

  @Value
  @Builder
  public static class DummyB {
    DummyC cVal1;
    DummyOrchestrationField<DummyC> cVal2;
    String strVal1;
    DummyOrchestrationField<String> strVal2;
    int intVal1;
    DummyOrchestrationField<Integer> intVal2;
  }

  @Value
  @Builder
  public static class DummyC {
    String strVal;
  }

  @Value
  @Builder
  public static class DummyD {
    String strVal;
    ParameterField<DummyD> dummyD;
    ParameterField<Integer> iVal;
    ParameterField<String> strVal2;
    DummyD dummyD2;
  }

  private EngineExpressionEvaluator prepareEngineExpressionEvaluator(Map<String, Object> contextMap) {
    SampleEngineExpressionEvaluator evaluator = new SampleEngineExpressionEvaluator();
    on(evaluator).set("planExecutionService", planExecutionService);
    on(evaluator).set("inputSetValidatorFactory", inputSetValidatorFactory);
    if (EmptyPredicate.isEmpty(contextMap)) {
      return evaluator;
    }

    for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
      evaluator.addToContext(entry.getKey(), entry.getValue());
    }
    return evaluator;
  }

  public static class SampleEngineExpressionEvaluator extends AmbianceExpressionEvaluator {
    public SampleEngineExpressionEvaluator() {
      super(null, Ambiance.newBuilder().build(), null, false);
    }

    @Override
    protected void initialize() {
      super.initialize();
      addStaticAlias("bVal1CVal1", "bVal1.cVal1");
    }

    @NotNull
    protected List<String> fetchPrefixes() {
      return ImmutableList.of("obj", "");
    }

    @Override
    protected Object evaluateInternal(String expression, EngineJexlContext ctx) {
      Object value = super.evaluateInternal(expression, ctx);
      if (value instanceof DummyOrchestrationField) {
        return ((DummyOrchestrationField) value).fetchFinalValue();
      }
      return value;
    }
  }
}
