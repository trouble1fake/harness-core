#!/bin/bash
#Merging Strategy: Fast-Forward Rebasing

function print_err(){
    local_cmd_status=$1
    local_cmd_name=$2
    if [ "$local_cmd_status" != 0 ]; then
        echo "ERROR: Line $LINENO: $2 Command Failed... Exiting..."
        exit 1
    fi
}

GIT_COMMAND="git log --graph --abbrev-commit --date=relative"

if [ -z "${BOT_USER}" ] && [ -z "${BOT_PWD}" ] && [ -z "${BOT_EMAIL}" ]; then
    echo "ERROR: Line: $LINENO: BOT Parameters are missing. Exiting..."
    exit 1
fi

echo "STEP 1: Setting up remote."
git remote set-url origin https://${BOT_USER}:${BOT_PWD}@github.com/harness/harness-core.git; git remote -v && git branch

echo "STEP 2: Setting Git Username and Password"
git config --global user.name "${BOT_USER}"
git config --global user.email "${BOT_EMAIL}"

echo "STEP 3: Checking out Master to local repo."
git fetch origin refs/heads/master; git checkout master && git branch

echo "STEP 4: Checking if Master branch is ahead of Develop branch"
MASTER_TO_DEVELOP=$($GIT_COMMAND develop..master)
echo "INFO: Diff: $MASTER_TO_DEVELOP"
if [ ! -z "$MASTER_TO_DEVELOP" ]; then
    echo "ERROR: Line $LINENO: Master branch is ahead of Develop branches. Exiting..."
    exit 1
fi

echo "STEP 4: Rebasing develop branch to Master branch"
git rebase develop #If we fire command in STEP 3
print_err "$?" "Rebasing"

# git rebase develop master #Does git checkout master and git rebase develop
# print_err "$?" "Rebasing"

#NOTE: Both commands in STEP 5 should give empty results to make sure that both have same commits.
echo "STEP 5: Matching the commits in master and develop"
MASTER_TO_DEVELOP=$($GIT_COMMAND develop..master)
DEVELOP_TO_MASTER=$($GIT_COMMAND master..develop)

if [ ! -z "$MASTER_TO_DEVELOP" ] && [ ! -z "$MASTER_TO_DEVELOP" ]; then
    echo "ERROR: Line $LINENO: Master and Develop branches are not identical after rebasing. Exiting..."
    exit 1
fi

echo "STEP 6: Pushing to target branch: master"
if [ -z "$MASTER_TO_DEVELOP" ] && [ -z "$MASTER_TO_DEVELOP" ]; then
    git push origin master
    print_err "$?" "Push to Master"
fi
