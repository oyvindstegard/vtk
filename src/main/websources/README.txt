Readme for websources
=====================

Uses Javascript tools to proccess web resources. It is run by Maven using Frontend Maven Plugin_,
which takes care of downloading, installing and running the Javascript tools.

The tooling is based on Node_ as a runtime. NPM_ takes care of managing the dependencies for the
tools we use. Bower_ is for managing front-end dependencies. Gulp_ is the build tool which will
proccess the web resources and copy them to the bulid target directory.

During development you might want to run npm, bower or gulp directly. For this I have added the
the three shell scripts npm.sh, bower.sh and gulp.sh. You should change the
webResources.physicalLocation property to point to the location of the processed resources
(e.g: ``webResources.physicalLocation = file:///path/to/vtk/target/classes/web/``).
This can be done in the ``.vtk.properties`` or ``.vrtx.properties`` files.
You can then run ``./gulp.sh watch`` in this directory, which will proccess the files as you
change them.


.. _Node: https://nodejs.org/
.. _NPM: https://www.npmjs.com/
.. _Bower: http://bower.io/
.. _Gulp: http://gulpjs.com/
.. _Frontend Maven Plugin: https://github.com/eirslett/frontend-maven-plugin
