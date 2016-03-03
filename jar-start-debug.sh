#!/bin/sh

java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=4000,suspend=n \
    -Dvtk.listen=localhost:9321,localhost:9322 \
    -Dvtk.webdav.port=9321 -Dvtk.web.port=9322 \
    -Dvtk.configLocations=$HOME/.vtk.properties \
    -jar $@
