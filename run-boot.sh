#!/bin/bash

WEB_PORT=9322
WEBDAV_PORT=9321
CLUSTER_PORTS=( )
CONFIG_LOCATIONS=$HOME/.vtk.properties
DEBUG_PORT=
VTK_EXTENSIONS=
JAR_FILE=

while [[ $# > 0 ]]
do
    key="$1"
    case $key in
        -wp|--web-port)
        WEB_PORT="$2"
        shift
        ;;
        -dp|--dav-port)
        WEBDAV_PORT="$2"
        shift
        ;;
        -c|--configs)
        CONFIG_LOCATIONS="$2"
        shift
        ;;
        -cps|--cluster-ports)
        IFS=',' read -r -a CLUSTER_PORTS <<< "$2" #Comma separated string to array
        shift
        ;;
        -d|--debug)
        DEBUG_PORT="$2"
        shift
        ;;
        -e|--extensions)
        VTK_EXTENSIONS="$2,"
        shift
        ;;
        -j|--jar)
        JAR_FILE="$2,"
        shift
        break # Rest is passed as arguments to the jar file
        ;;
        *)
        # unknown option
        ;;
    esac
    shift # past argument or value
done

JAVA_ARGS="-Dvtk.listen=localhost:$WEB_PORT,localhost:$WEBDAV_PORT \
-Dvtk.web.port=$WEB_PORT -Dvtk.webdav.port=$WEBDAV_PORT \
-Dvtk.configLocations=$CONFIG_LOCATIONS"

if [ ! -z $DEBUG_PORT ]
then
    DEBUG_ARGS="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=$DEBUG_PORT,suspend=n"
fi

if [ ${#CLUSTER_PORTS[@]} -gt 0 ]
then
    VTK_EXTENSIONS="${VTK_EXTENSIONS}cluster-akka,shared-session-akka,"
    JAVA_ARGS="$JAVA_ARGS -Dvtk.cluster.port=${CLUSTER_PORTS[0]}"
    for index in "${!CLUSTER_PORTS[@]}"
    do
        JAVA_ARGS="$JAVA_ARGS -Dakka.cluster.seed-nodes.${index}=akka.tcp://vtk-cluster@localhost:${CLUSTER_PORTS[$index]}"
    done
fi

if [ ! -z $VTK_EXTENSIONS ]
then
    JAVA_ARGS="$JAVA_ARGS -Dvtk.extensions=$VTK_EXTENSIONS"
fi

# If jar file is specified
if [ ! -z $JAR_FILE ]
then
    EXEC="java $DEBUG_ARGS $JAVA_ARGS -jar $@"
else
    EXEC="MAVEN_OPTS=$DEBUG_ARGS mvn $JAVA_ARGS spring-boot:run"
fi
echo "Run: $EXEC"
$EXEC
