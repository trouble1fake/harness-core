# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

### Dockerization of Manager ####### Doc
set -x
set -e

SCRIPT_DIR="$(dirname $0)"
source "${SCRIPT_DIR}/portal-openjdk-bazel-commons.sh"

prepare_to_copy_jars

mkdir -p dist/platform-service
cd dist/platform-service

cp ${HOME}/.bazel-dirs/bin/820-platform-service/module_deploy.jar platform-service-capsule.jar
cp ../../820-platform-service/config.yml .
cp ../../820-platform-service/keystore.jks .
cp ../../820-platform-service/key.pem .
cp ../../820-platform-service/cert.pem .
#cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/platform-service/Dockerfile-platform-service-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/platform-service/Dockerfile-platform-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp ../../dockerization/build/Dockerfile-jenkins-slave-portal-jdk-11 ./Dockerfile-jenkins
cp -r ../../dockerization/platform-service/scripts .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
#java -jar platform-service-capsule.jar scan-classpath-metadata

cd ../..
