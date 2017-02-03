#!/bin/bash
#export MALLOC_ARENA_MAX=4 # Stop the JVM from being allowed to use up all of
# Docker's virtual memory. Use if it's a problem
# see https://siddhesh.in/posts/malloc-per-thread-arenas-in-glibc.html

ONYX_ID=khow
NPEERS=10

echo "Using Hecuba user: ${HECUBA_USERNAME}"
echo "Using Hecuba endpoint: ${HECUBA_ENDPOINT}"

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

echo "Starting peer id ${ONYX_ID} with ${NPEERS} peers"


/usr/bin/java $PEER_JAVA_OPTS \
              "-Xmx${XMX}m" \
              -cp /opt/peer.jar \
             kixi.event2s3.core start-peers "$NPEERS" -p :mesos -c /opt/config.edn
