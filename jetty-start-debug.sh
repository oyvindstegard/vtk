#!/bin/sh

MAVEN_OPTS="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=4000,suspend=n" mvn jetty:run
