#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CHECK_MESSAGE=hook.commit-msg.check
if [ "`git config $CHECK_MESSAGE`" == "false" ]
then
    echo -e '\033[0;31m' checking message is disabled - to enable: '\033[0;37m'git config --unset $CHECK_MESSAGE '\033[0m'
else
    echo -e '\033[0;34m' checking message ... to disable: '\033[0;37m'git config --add $CHECK_MESSAGE false '\033[0m'

    # regex to validate in commit msg
    commit_regex='^\[(BT|CCE|CCM|CDB|CDC|CDNG|CDP|CE|CI|CV|CVNG|CVS|DEL|DOC|DX|ER|FFM|OPA|ONP|OPS|PIE|PL|SEC|SWAT|GTM|LWG|OENG|COMP)-[0-9]+]: |Merge branch '
    error_msg="Aborting commit. [`cat $1`] is missing a JIRA Issue and Commit Content. Example Commit Message: \"[JIRAProject-123]: Message \""

    if [ ! -z "`cat $1`" ]
    then
        if ! grep -iqE "$commit_regex" "$1"; then
            echo "$error_msg" >&2
            exit 1
        fi
    fi
fi
