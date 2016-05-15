#!/bin/bash

# usage: timetracker_collector.sh [raw log dir]

DATE=$(date "+%Y-%m-%d %T %Z")

LOGNAME=$(date -j -f "%a %b %d %T %Z %Y" "`date`" "+%Y%m%d.log")

# Query the IO registry to get information about power management.
EXTENDED_POWER_STATE=$(ioreg -n IODisplayWrangler | grep -i IOPowerManagement)

# Power states range from 0 - 4, inclusive. I believe these are related to ACPI
# global states, with the states numbers inverted.
# https://en.wikipedia.org/wiki/Advanced_Configuration_and_Power_Interface#Global_states
# 
#  0: (S4 ACPI) Hibernation or Suspend to Disk.
#  1: (S3 ACPI) Commonly referred to as Standby, Sleep, or Suspend to RAM.
#  2: (S2 ACPI) CPU powered off.
#  3: (S1 ACPI) Power on Suspend. Power to CPU(s) and RAM is maintained.
#  4: (S0 ACPI) Working.
POWER_STATE=$(echo $EXTENDED_POWER_STATE | perl -pe 's/^.*DevicePowerState\"=([0-9]+).*$/\1/')

# Milliseconds since activity.
echo -e "${DATE}\t${POWER_STATE}" >> $1/$LOGNAME

# TIME_SINCE_TICKLE=$(echo $EXTENDED_POWER_STATE | perl -pe 's/^.*TimeSinceLastTickle\"=([0-9]+).*$/\1/')
# echo -e "${DATE}\t${POWER_STATE}\t${TIME_SINCE_TICKLE}" >> $1/$LOGNAME
