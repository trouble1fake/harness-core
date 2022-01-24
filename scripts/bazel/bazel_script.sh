#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

#local_repo=${HOME}/.m2/repository
BAZEL_ARGUMENTS=
if [ "${PLATFORM}" == "jenkins" ]; then
  bazelrc=--bazelrc=bazelrc.remote
  #local_repo=/root/.m2/repository
  if [ ! -z "${DISTRIBUTE_TESTING_WORKER}" ]; then
    bash scripts/bazel/testDistribute.sh
  fi
fi

BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --show_timestamps --announce_rc"

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"

#if [[ ! -z "${OVERRIDE_LOCAL_M2}" ]]; then
#  local_repo=${OVERRIDE_LOCAL_M2}
#fi

# Enable caching by default. Turn it off by exporting CACHE_TEST_RESULTS=no
# to generate full call-graph for Test Intelligence
if [[ ! -z "${CACHE_TEST_RESULTS}" ]]; then
  export CACHE_TEST_RESULTS_ARG=--cache_test_results=${CACHE_TEST_RESULTS}
fi

bazel ${bazelrc} build ${BAZEL_ARGUMENTS}  //:resource
cat ${BAZEL_DIRS}/out/stable-status.txt
cat ${BAZEL_DIRS}/out/volatile-status.txt

if [ "${RUN_BAZEL_TESTS}" == "true" ]; then
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -- //... -//product/... -//commons/... \
  && bazel ${bazelrc} test ${CACHE_TEST_RESULTS_ARG} --define=HARNESS_ARGS=${HARNESS_ARGS} --keep_going ${BAZEL_ARGUMENTS} -- \
  //... -//product/... -//commons/... -//200-functional-test/... -//190-deployment-functional-tests/...
  exit $?
fi

if [ "${RUN_CHECKS}" == "true" ]; then
  TARGETS=$(bazel query 'attr(tags, "checkstyle", //...:*)')
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS}
  exit $?
fi

if [ "${RUN_PMDS}" == "true" ]; then
  TARGETS=$(bazel query 'attr(tags, "pmd", //...:*)')
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS}
  exit $?
fi

BAZEL_MODULES="\
  //800-pipeline-service:module \
  //820-platform-service:module \
  //820-platform-service:module_deploy.jar \
  //878-pipeline-service-utilities:module \
"

bazel ${bazelrc} build $BAZEL_MODULES ${BAZEL_ARGUMENTS} --remote_download_outputs=all

build_bazel_module() {
  module=$1
  BAZEL_MODULE="//${module}:module"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_tests() {
  module=$1
  BAZEL_MODULE="//${module}:supporter-test"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_application() {
  module=$1
  BAZEL_MODULE="//${module}:module"
  BAZEL_DEPLOY_MODULE="//${module}:module_deploy.jar"

  bazel ${bazelrc} build $BAZEL_MODULES ${BAZEL_ARGUMENTS}

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi

  if ! grep -q "$BAZEL_DEPLOY_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_DEPLOY_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_application_module() {
  module=$1
  BAZEL_MODULE="//${module}:module"
  BAZEL_DEPLOY_MODULE="//${module}:module_deploy.jar"

  if [ "${BUILD_BAZEL_DEPLOY_JAR}" == "true" ]; then
    bazel ${bazelrc} build $BAZEL_DEPLOY_MODULE ${BAZEL_ARGUMENTS}
  fi

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_java_proto_module() {
  module=$1
  modulePath=$module/src/main/proto

  build_proto_module $module $modulePath
}

build_proto_module() {
  module=$1
  modulePath=$2

  BAZEL_MODULE="//${modulePath}:all"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi

  bazel_library=$(echo ${module} | tr '-' '_')
}

build_protocol_info(){
  module=$1
  moduleName=$2

  bazel query "deps(//${module}:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${module}:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
}

build_bazel_application 820-platform-service
build_bazel_module 878-pipeline-service-utilities

#bazel ${bazelrc} run ${BAZEL_ARGUMENTS} //001-microservice-intfc-tool:module | grep "Codebase Hash:" > protocol.info

#if [ "${PLATFORM}" == "jenkins" ]; then
# build_protocol_info 800-pipeline-service pipeline-service
#fi
