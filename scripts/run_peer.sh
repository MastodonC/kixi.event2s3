#!/bin/bash
#export MALLOC_ARENA_MAX=4 # Stop the JVM from being allowed to use up all of
# Docker's virtual memory. Use if it's a problem
# see https://siddhesh.in/posts/malloc-per-thread-arenas-in-glibc.html

PROFILE=${SYSTEM_PROFILE:-:production}

CGROUPS_MEM=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
MEMINFO_MEM=$(($(awk '/MemTotal/ {print $2}' /proc/meminfo)*1024))
MEM=$(($MEMINFO_MEM>$CGROUPS_MEM?$CGROUPS_MEM:$MEMINFO_MEM))
JVM_PEER_HEAP_RATIO=${JVM_PEER_HEAP_RATIO:-0.6}
XMX=$(awk '{printf("%d",$1*$2/1024^2)}' <<< " ${MEM} ${JVM_PEER_HEAP_RATIO} ")
# Use the container memory limit to set max heap size so that the GC
# knows to collect before it's hard-stopped by the container environment,
# causing OOM exception.

## Default BIND_ADDR to the machines hostname
: ${BIND_ADDR:=$(hostname)}
## For use when running a peer with access to the hosts eth0 interface
#: ${BIND_ADDR:=$(ifconfig eth0 | grep "inet addr:" | cut -d : -f 2 | cut -d " " -f 1)}
## For use when running a peer on AWS, where the routeable address is different
### from the address bound to eth0
#: ${BIND_ADDR:=$(curl http://169.254.169.254/latest/meta-data/public-ipv4)}

: ${PEER_JAVA_OPTS:='-XX:+UseG1GC -server'}
SANDBOX=${MESOS_SANDBOX:-"."}

echo "Using profile: ${PROFILE}"
echo "Starting peer id ${ONYX_ID} with ${NPEERS} peers"

/usr/bin/java $PEER_JAVA_OPTS \
              "-Xmx${XMX}m" \
              -XX:+HeapDumpOnOutOfMemoryError \
              -XX:HeapDumpPath=$SANDBOX \
              -XX:ErrorFile=$SANDBOX/hs_err_pid_%p.log \
              -Xloggc:$SANDBOX/gc_%p.log \
              -XX:+PrintGCCause \
              -XX:+UseGCLogFileRotation \
              -XX:NumberOfGCLogFiles=3 \
              -XX:GCLogFileSize=2M \
              -XX:+PrintGCDateStamps \
              -cp /opt/peer.jar \
             kixi.event2s3.core start-peers "$NPEERS" -p $PROFILE -c /opt/config.edn
