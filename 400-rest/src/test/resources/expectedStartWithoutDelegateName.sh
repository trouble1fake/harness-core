#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p logs
(
echo
echo "` date +%d/%m/%Y%t%H:%M:%S `    ###########################"

if [ ! -e start.sh ]; then
  echo
  echo "Delegate must not be run from a different directory"
  echo
  exit 1
fi

JRE_DIR=jdk8u242-b08-jre
JRE_BINARY=$JRE_DIR/bin/java
case "$OSTYPE" in
  solaris*)
    OS=solaris
    ;;
  darwin*)
    OS=macosx
    JRE_DIR=jdk8u242-b08-jre
    JRE_BINARY=$JRE_DIR/Contents/Home/bin/java
    ;;
  linux*)
    OS=linux
    ;;
  bsd*)
    echo "freebsd not supported."
    exit 1;
    ;;
  msys*)
    echo "For windows execute run.bat"
    exit 1;
    ;;
  cygwin*)
    echo "For windows execute run.bat"
    exit 1;
    ;;
  *)
    echo "unknown: $OSTYPE"
    ;;
esac

JVM_URL=http://localhost:8888/jre/openjdk-8u242/jre_x64_${OS}_8u242b08.tar.gz

ALPN_BOOT_JAR_URL=http://localhost:8888/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

function jar_app_version() {
  JAR=$1
  if unzip -l $JAR | grep -q io/harness/versionInfo.yaml
  then
    VERSION=$(unzip -c $JAR io/harness/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
  fi

  if [ -z "$VERSION" ]
  then
    if unzip -l $JAR | grep -q main/resources-filtered/versionInfo.yaml
    then
      VERSION=$(unzip -c $JAR main/resources-filtered/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
    fi
  fi

  if [ -z "$VERSION" ]
  then
    VERSION=$(unzip -c $JAR META-INF/MANIFEST.MF | grep Application-Version | cut -d "=" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
  fi
  echo $VERSION
}

ULIM=$(ulimit -n)
if [[ "$ULIM" == "unlimited" || $ULIM -lt 10000 ]]; then
  echo
  echo "WARNING: ulimit -n is too low ($ULIM)"
  echo
  echo "Run the following command to set it to 10000 or greater:"
  echo
  echo "ulimit -n 10000"
  echo
  echo "Continuing in 15 seconds. Ctrl-C to quit."
  sleep 15s
  echo
fi

if [[ "$OSTYPE" == darwin* ]]; then
  MEM=$(top -l 1 -n 0 | grep PhysMem | cut -d ' ' -f 2 | cut -d 'G' -f 1)
  if [[ $MEM =~ "M" ]]; then
    MEM=$(($(echo $MEM | cut -d 'M' -f 1)/1024))
  fi
  if [[ $MEM -lt 6 ]]; then
    echo
    echo "WARNING: Not enough memory ($MEM). Minimum 6 GB required."
    echo
    echo "Continuing in 15 seconds. Ctrl-C to quit."
    sleep 15s
    echo
  fi
else
  MEM=$(free -m | grep Mem | awk '{ print $2 }')
  if [[ $MEM -lt 6000 ]]; then
    echo
    echo "WARNING: Not enough memory ($MEM MB). Minimum 6 GB required."
    echo
    echo "Continuing in 15 seconds. Ctrl-C to quit."
    sleep 15s
    echo
  fi
fi

export MANAGER_HOST_AND_PORT=https://localhost:9090
if [[ -e proxy.config ]]; then
  source proxy.config
  if [[ $PROXY_HOST != "" ]]; then
    echo "Using proxy $PROXY_SCHEME://$PROXY_HOST:$PROXY_PORT"
    if [[ $PROXY_USER != "" ]]; then
      export PROXY_USER
      if [[ "$PROXY_PASSWORD_ENC" != "" ]]; then
        export PROXY_PASSWORD=$(echo $PROXY_PASSWORD_ENC | openssl enc -d -a -des-ecb -K 4143434f554e)
      fi
      export PROXY_CURL="-x "$PROXY_SCHEME"://"$PROXY_USER:$PROXY_PASSWORD@$PROXY_HOST:$PROXY_PORT
    else
      export PROXY_CURL="-x "$PROXY_SCHEME"://"$PROXY_HOST:$PROXY_PORT
      export http_proxy=$PROXY_SCHEME://$PROXY_HOST:$PROXY_PORT
      export https_proxy=$PROXY_SCHEME://$PROXY_HOST:$PROXY_PORT
    fi
    PROXY_SYS_PROPS="-DproxyScheme=$PROXY_SCHEME -Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
  fi

  if [[ $PROXY_MANAGER == "true" || $PROXY_MANAGER == "" ]]; then
    export MANAGER_PROXY_CURL=$PROXY_CURL
  else
    HOST_AND_PORT_ARRAY=(${MANAGER_HOST_AND_PORT//:/ })
    MANAGER_HOST="${HOST_AND_PORT_ARRAY[1]}"
    MANAGER_HOST="${MANAGER_HOST:2}"
    echo "No proxy for Harness manager at $MANAGER_HOST"
    if [[ $NO_PROXY == "" ]]; then
      NO_PROXY=$MANAGER_HOST
    else
      NO_PROXY="$NO_PROXY,$MANAGER_HOST"
    fi
  fi

  if [[ $NO_PROXY != "" ]]; then
    echo "No proxy for domain suffixes $NO_PROXY"
    export no_proxy=$NO_PROXY
    SYSTEM_PROPERTY_NO_PROXY=`echo $NO_PROXY | sed "s/\,/|*/g"`
    PROXY_SYS_PROPS=$PROXY_SYS_PROPS" -Dhttp.nonProxyHosts=*$SYSTEM_PROPERTY_NO_PROXY"
  fi
fi

if [[ "$OSTYPE" == linux* ]]; then
  touch /tmp/exec-test.sh && chmod +x /tmp/exec-test.sh
  /tmp/exec-test.sh
  if [ ! $? -eq 0 ]; then
    echo "/tmp is mounted noexec. Overriding tmpdir"
    export OVERRIDE_TMP_PROPS="-Djava.io.tmpdir=$DIR/tmp"
    export JAVA_OPTS
  fi
fi

ACCOUNT_STATUS=$(curl $MANAGER_PROXY_CURL -ks https://localhost:9090/api/account/ACCOUNT_ID/status | cut -d ":" -f 3 | cut -d "," -f 1 | cut -d "\"" -f 2)
if [[ $ACCOUNT_STATUS == "DELETED" ]]; then
  rm README.txt delegate.sh proxy.config start.sh stop.sh
  touch __deleted__
  exit 0
fi

if [ ! -d $JRE_DIR -o ! -e $JRE_BINARY ]; then
  echo "Downloading JRE packages..."
  JVM_TAR_FILENAME=$(basename "$JVM_URL")
  curl $MANAGER_PROXY_CURL -#kLO $JVM_URL
  echo "Extracting JRE packages..."
  rm -rf $JRE_DIR
  tar xzf $JVM_TAR_FILENAME
  rm -f $JVM_TAR_FILENAME
fi

if [ ! -d $JRE_DIR  -o ! -e $JRE_BINARY ]; then
  echo "No JRE available. Exiting."
  exit 1
fi

export DEPLOY_MODE=KUBERNETES

echo "Checking Watcher latest version..."
WATCHER_STORAGE_URL=http://localhost:8888
REMOTE_WATCHER_LATEST=$(curl $MANAGER_PROXY_CURL -ks $WATCHER_STORAGE_URL/watcherci.txt)
if [[ $DEPLOY_MODE != "KUBERNETES" ]]; then
    REMOTE_WATCHER_URL=$WATCHER_STORAGE_URL/$(echo $REMOTE_WATCHER_LATEST | cut -d " " -f2)
else
    REMOTE_WATCHER_URL=http://localhost:9500/builds/$(echo $REMOTE_WATCHER_LATEST | cut -d " " -f2)
fi
REMOTE_WATCHER_VERSION=$(echo $REMOTE_WATCHER_LATEST | cut -d " " -f1)

if [ ! -e watcher.jar ]; then
  echo "Downloading Watcher $REMOTE_WATCHER_VERSION ..."
  curl $MANAGER_PROXY_CURL -#k $REMOTE_WATCHER_URL -o watcher.jar
else
  WATCHER_CURRENT_VERSION=$(jar_app_version watcher.jar)
  if [[ $REMOTE_WATCHER_VERSION != $WATCHER_CURRENT_VERSION ]]; then
    echo "The current version $WATCHER_CURRENT_VERSION is not the same as the expected remote version $REMOTE_WATCHER_VERSION"
    echo "Downloading Watcher $REMOTE_WATCHER_VERSION ..."
    mkdir -p watcherBackup.$WATCHER_CURRENT_VERSION
    cp watcher.jar watcherBackup.$WATCHER_CURRENT_VERSION
    curl $MANAGER_PROXY_CURL -#k $REMOTE_WATCHER_URL -o watcher.jar
  fi
fi

if [[ $DEPLOY_MODE != "KUBERNETES" ]]; then
  echo "Checking Delegate latest version..."
  DELEGATE_STORAGE_URL=http://localhost:8888
  REMOTE_DELEGATE_LATEST=$(curl $MANAGER_PROXY_CURL -ks $DELEGATE_STORAGE_URL/delegateci.txt)
  REMOTE_DELEGATE_URL=$DELEGATE_STORAGE_URL/$(echo $REMOTE_DELEGATE_LATEST | cut -d " " -f2)
  REMOTE_DELEGATE_VERSION=$(echo $REMOTE_DELEGATE_LATEST | cut -d " " -f1)

  if [ ! -e delegate.jar ]; then
    echo "Downloading Delegate $REMOTE_DELEGATE_VERSION ..."
    curl $MANAGER_PROXY_CURL -#k $REMOTE_DELEGATE_URL -o delegate.jar
  else
    DELEGATE_CURRENT_VERSION=$(jar_app_version delegate.jar)
    if [[ $REMOTE_DELEGATE_VERSION != $DELEGATE_CURRENT_VERSION ]]; then
      echo "The current version $DELEGATE_CURRENT_VERSION is not the same as the expected remote version $REMOTE_DELEGATE_VERSION"
      echo "Downloading Delegate $REMOTE_DELEGATE_VERSION ..."
      mkdir -p backup.$DELEGATE_CURRENT_VERSION
      cp delegate.jar backup.$DELEGATE_CURRENT_VERSION
      curl $MANAGER_PROXY_CURL -#k $REMOTE_DELEGATE_URL -o delegate.jar
    fi
  fi
fi

if [ ! -e config-watcher.yml ]; then
  echo "accountId: ACCOUNT_ID" > config-watcher.yml
fi
test "$(tail -c 1 config-watcher.yml)" && `echo "" >> config-watcher.yml`
set +x
if ! `grep accountSecret config-watcher.yml > /dev/null`; then
  echo "accountSecret: ACCOUNT_KEY" >> config-watcher.yml
fi
set -x
if ! `grep managerUrl config-watcher.yml > /dev/null`; then
  echo "managerUrl: https://localhost:9090/api/" >> config-watcher.yml
fi
if ! `grep doUpgrade config-watcher.yml > /dev/null`; then
  echo "doUpgrade: true" >> config-watcher.yml
fi
if ! `grep upgradeCheckLocation config-watcher.yml > /dev/null`; then
  echo "upgradeCheckLocation: http://localhost:8888/watcherci.txt" >> config-watcher.yml
else
  sed -i.bak "s|^upgradeCheckLocation:.*$|upgradeCheckLocation: http://localhost:8888/watcherci.txt|" config-watcher.yml
fi
if ! `grep upgradeCheckIntervalSeconds config-watcher.yml > /dev/null`; then
  echo "upgradeCheckIntervalSeconds: 1200" >> config-watcher.yml
fi
if ! `grep delegateCheckLocation config-watcher.yml > /dev/null`; then
  echo "delegateCheckLocation: http://localhost:8888/delegateci.txt" >> config-watcher.yml
else
  sed -i.bak "s|^delegateCheckLocation:.*$|delegateCheckLocation: http://localhost:8888/delegateci.txt|" config-watcher.yml
fi
if ! `grep fileHandlesMonitoringEnabled config-watcher.yml > /dev/null`; then
  echo "fileHandlesMonitoringEnabled: false" >> config-watcher.yml
fi
if ! `grep fileHandlesMonitoringIntervalInMinutes config-watcher.yml > /dev/null`; then
  echo "fileHandlesMonitoringIntervalInMinutes: 15" >> config-watcher.yml
fi
if ! `grep fileHandlesLogsRetentionInMinutes config-watcher.yml > /dev/null`; then
  echo "fileHandlesLogsRetentionInMinutes: 1440" >> config-watcher.yml
fi

rm -f -- *.bak

export DELEGATE_PROFILE=QFWin33JRlKWKBzpzE5A9A
export DELEGATE_TYPE=SHELL_SCRIPT

export HOSTNAME
export CAPSULE_CACHE_DIR="$DIR/.cache"

if [[ $1 == "upgrade" ]]; then
  echo "Upgrade"
  WATCHER_CURRENT_VERSION=$(jar_app_version watcher.jar)
  mkdir -p watcherBackup.$WATCHER_CURRENT_VERSION
  cp watcher.jar watcherBackup.$WATCHER_CURRENT_VERSION
  $JRE_BINARY $JAVA_OPTS $PROXY_SYS_PROPS $OVERRIDE_TMP_PROPS -Dwatchersourcedir="$DIR" -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:mygclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 -jar watcher.jar config-watcher.yml upgrade $2
else
  if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
    echo "Watcher already running"
  else
    nohup $JRE_BINARY $JAVA_OPTS $PROXY_SYS_PROPS $OVERRIDE_TMP_PROPS -Dwatchersourcedir="$DIR" -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:mygclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 -jar watcher.jar config-watcher.yml >nohup-watcher.out 2>&1 &
    sleep 1
    if [ -s nohup-watcher.out ]; then
      echo "Failed to start Watcher."
      echo "$(cat nohup-watcher.out)"
      exit 1
    else
      sleep 3
      if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
        echo "Watcher started"
      else
        echo "Failed to start Watcher."
        echo "$(tail -n 30 watcher.log)"
        exit 1
      fi
    fi
  fi
fi ) 2>&1 | tee -a logs/log_clean.log && sed '/######################################################################## 100.0%/d' logs/log_clean.log >> logs/startscript.log
rm logs/log_clean.log
