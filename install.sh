#!/bin/bash

cd ${0%/*}   # Make sure everything is relative to the projects root dir.

TIMETRACKER_DIR=$(pwd)
ESCAPED_TIMETRACKER_DIR=$(echo $TIMETRACKER_DIR | sed 's/\//\\\//g')

mkdir -p $TIMETRACKER_DIR/logs/raw
mkdir -p $TIMETRACKER_DIR/logs/processed

COLLECTOR_JOB=com.github.deaktator.timetracker.collector
COLLECTOR_LAUNCHD_LOCAL=${TIMETRACKER_DIR}/LaunchAgents/${COLLECTOR_JOB}.plist
COLLECTOR_LAUNCHD=$HOME/Library/LaunchAgents/${COLLECTOR_JOB}.plist
if [[ $(launchctl list | grep "$COLLECTOR_JOB") ]]; then 
  launchctl unload $COLLECTOR_LAUNCHD
  rm -f $COLLECTOR_LAUNCHD 2>/dev/null
fi

cat $COLLECTOR_LAUNCHD_LOCAL \
| sed "s/__TIMETRACKER_DIR__/$ESCAPED_TIMETRACKER_DIR/g" \
> $COLLECTOR_LAUNCHD

launchctl load $COLLECTOR_LAUNCHD

