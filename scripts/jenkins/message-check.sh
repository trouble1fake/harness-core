#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set +e

COMMIT_CONTENT="\[feat]|\[fix]|\[techdebt]|feat|fix|techdebt"
PROJECTS="BT|CCE|CCM|CDB|CDC|CDNG|CDP|CE|CI|CV|CVNG|CVS|DEL|DOC|DX|ER|OPS|PIE|PL|SEC|SWAT|GTM|FFM|OPA|ONP|LWG|ART|GIT|OENG|COMP"

# Check commit message if there's a single commit
#if [ $(git rev-list --count $ghprbActualCommit ^origin/master)  -eq 1 ]; then
#    ghprbPullTitle=$(git log -1 --format="%s" $ghprbActualCommit)
#fi

ghprbPullTitle="$1"

PR_MESSAGE=`echo "${ghprbPullTitle}" | grep -iE "^(${COMMIT_CONTENT}): \[(${PROJECTS})-[0-9]+]: "`

if [ -z "$PR_MESSAGE" ]
then
    echo The PR title \"${ghprbPullTitle}\"
    echo "does not match the expectations"
    echo "Make sure that your commit message is in format -> ${COMMIT_CONTENT}: [${PROJECTS}-<number>]: <description>"
    echo "Example -> \"feat: [BT-100]: Commit Message\""
    exit 1
fi

KEY=`echo "${ghprbPullTitle}" | grep -o -iqE "(${PROJECTS})-[0-9]+"`

#TODO: enable priorities check

#AUTHOR=`echo ${ghprbPullAuthorEmail} | sed 's/\(.*\)@harness.io$/\1/g'`

#curl -v https://harness.atlassian.net/rest/api/3/search?jql=filter=12111%20AND%20assignee=$AUTHOR\&fields=key --user $JIRA_USERNAME:$JIRA_PASSWORD -o result.txt > /dev/null 2>&1

#PRIORITY_ISSUES=`cat result.txt | tr "," "\n" | grep -o -e "HAR-[0-9]+"`

#if [ ! -z "$PRIORITY_ISSUES" ]
#then
#    if ! echo "${PRIORITY_ISSUES}" | grep ${KEY}
#    then
#        echo The issue ${KEY} is not your imidiate priority.
#        echo Please first address the folowing issues: ${PRIORITY_ISSUES}
#        #exit 1
#    fi
#fi
