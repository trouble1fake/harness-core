# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -xe

export BRANCH_PREFIX=`echo ${ghprbTargetBranch} | sed 's/\(........\).*/\1/g'`
echo "INFO: BRANCH_PREFIX=$BRANCH_PREFIX"

export VERSION_FILE=build.properties

export VERSION=`cat ${VERSION_FILE} | grep 'build.number=' | sed -e 's: *build.number=::g'`
echo "INFO: VERSION=$VERSION"

export OLD_VERSION=$(( ${VERSION}-1 ))
echo "INFO: OLD_VERSION=$OLD_VERSION"

export PATCH=`cat ${VERSION_FILE} | grep 'build.patch=' | sed -e 's: *build.patch=::g'`
echo "INFO: PATCH=$PATCH"

git checkout origin/${ghprbTargetBranch} -b temp246_test
git checkout ${ghprbTargetBranch}

VERSION_DIFF=$(git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | { grep "+build.number=$VERSION" || true;})
echo "INFO: $?: VERSION_DIFF=$VERSION_DIFF"

PATCH_DIFF=$(git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | { grep  "+build.patch=$PATCH" || true;})
echo "INFO: $?: PATCH_DIFF=$PATCH_DIFF"

if [ "${BRANCH_PREFIX}" = "release/" ] && [ ! $VERSION_DIFF ] && [ ! $PATCH_DIFF ]
then

  echo "ERROR:  Either build.number or build.patch must be incremented."
  exit 1

elif [ "${BRANCH_PREFIX}" = "release/" ] && [ $VERSION_DIFF ]
then

  echo "INFO:  OLD VERSION INFO."
  git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | grep "\-build.number=$OLD_VERSION"

elif [ "${BRANCH_PREFIX}" = "release/" ] && [ $PATCH_DIFF ]
then

  echo "INFO:  OLD PATCH INFO."
  export OLD_PATCH=$(printf %03d $(( ${PATCH}-1 )) )
  git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | grep "+build.patch=$PATCH"
  git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | grep "\-build.patch=$OLD_PATCH"

fi

if [ "${ghprbTargetBranch}" = "develop" ]
then
  git show ${VERSION_FILE} | grep "build.number" || exit 0 && exit 1
  git show ${VERSION_FILE} | grep "build.patch" || exit 0 && exit 1
fi
