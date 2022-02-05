#!/usr/bin/env bash
# Copyright 2019 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p /opt/harness/logs
touch /opt/harness/logs/batch-processing.log

if [[ -v "{hostname}" ]]; then
   export HOSTNAME=$(hostname)
fi

if [[ -z "$MEMORY" ]]; then
   export MEMORY=4096
fi

echo "Using memory " $MEMORY

if [[ -z "$CAPSULE_JAR" ]]; then
   export CAPSULE_JAR=/opt/harness/batch-processing-capsule.jar
fi

export JAVA_OPTS="-Xms${MEMORY}m -Xmx${MEMORY}m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:mygclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8"

function attach_jvm_profiler_agent() {
  echo "Downloading YourKit-JavaProfiler agent"

  DOWNLOAD_PATH="/tmp/YourKit-JavaProfiler-2021.11-b227.zip"
  wget https://www.yourkit.com/download/YourKit-JavaProfiler-2021.11-b227.zip -O "${DOWNLOAD_PATH}"
  unzip "${DOWNLOAD_PATH}" -d /opt/harness/
  rm "${DOWNLOAD_PATH}"

  JAVA_OPTS=$JAVA_OPTS" -agentpath:/opt/harness/YourKit-JavaProfiler-2021.11/bin/linux-x86-64/libyjpagent.so"

  echo "Added Profiling agent JAVA_OPTS=${JAVA_OPTS}"
}
attach_jvm_profiler_agent

if [[ "${DEPLOY_MODE}" == "KUBERNETES" ]] || [[ "${DEPLOY_MODE}" == "KUBERNETES_ONPREM" ]]; then
    java $JAVA_OPTS -jar $CAPSULE_JAR /opt/harness/batch-processing-config.yml
else
    java $JAVA_OPTS -jar $CAPSULE_JAR /opt/harness/batch-processing-config.yml > /opt/harness/logs/batch-processing.log 2>&1
fi
