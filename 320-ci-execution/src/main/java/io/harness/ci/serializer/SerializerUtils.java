/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer;

import static java.lang.String.format;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SerializerUtils {
  public List<String> getEntrypoint(ParameterField<CIShellType> parametrizedShellType) {
    List<String> entrypoint;
    CIShellType shellType = RunTimeInputHandler.resolveShellType(parametrizedShellType);
    if (shellType == CIShellType.SH) {
      entrypoint = Arrays.asList("sh", "-c");
    } else if (shellType == CIShellType.BASH) {
      entrypoint = Arrays.asList("bash", "-c");
    } else if (shellType == CIShellType.POWERSHELL) {
      entrypoint = Arrays.asList("powershell", "-Command");
    } else if (shellType == CIShellType.PWSH) {
      entrypoint = Arrays.asList("pwsh", "-Command");
    } else {
      throw new CIStageExecutionException(format("Invalid shell type: %s", shellType));
    }
    return entrypoint;
  }

  public String getEarlyExitCommand(ParameterField<CIShellType> parametrizedShellType) {
    String cmd;
    CIShellType shellType = RunTimeInputHandler.resolveShellType(parametrizedShellType);
    if (shellType == CIShellType.SH || shellType == CIShellType.BASH) {
      cmd = "set -xe; ";
    } else if (shellType == CIShellType.POWERSHELL || shellType == CIShellType.PWSH) {
      cmd = "$ErrorActionPreference = 'Stop' \n";
    } else {
      throw new CIStageExecutionException(format("Invalid shell type: %s", shellType));
    }
    return cmd;
  }

  public String convertJsonNodeToString(String key, JsonNode jsonNode) {
    try {
      if (jsonNode.isValueNode()) {
        return jsonNode.asText("");
      } else if (jsonNode.isArray() && isPrimitiveArray(jsonNode)) {
        ArrayNode arrayNode = (ArrayNode) jsonNode;
        List<String> strValues = new ArrayList<>();
        for (JsonNode node : arrayNode) {
          strValues.add(node.asText(""));
        }

        return String.join(",", strValues);
      } else {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(jsonNode);
      }
    } catch (Exception ex) {
      throw new CIStageExecutionException(String.format("Invalid setting attribute %s value", key));
    }
  }

  // Return whether array contains only value node or not.
  private boolean isPrimitiveArray(JsonNode jsonNode) {
    ArrayNode arrayNode = (ArrayNode) jsonNode;
    for (JsonNode e : arrayNode) {
      if (!e.isValueNode()) {
        return false;
      }
    }
    return true;
  }
}
