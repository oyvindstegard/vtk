Strobe Media Playback build README
==================================
Date: 2013-12-18, author: oyviste

This document contains instructions for building the SMP media player from
source code with our custom patches.


Pre-requisites
--------------

1. Player source code is part of OSMF.
The source code is available here:
http://osmf.org/

Download the most recent OSMF source code release (usually a largeish zip file).


2. A working Flash Builder installation.
Try the one available on kiosk.uio.no. If Flash Builder on kiosk.uio.no fails
with a cryptic error message on startup, it may be due to a permission problem
on your network home directory. In that case, try:

$ chmod -R 755 ~/pc/.swt/

Then try to start Flash Builder again.


3. Adobe Flex SDK 4.5.1 if you wish to target the build for Flash Player 10.X as
minimum requirement. Otherwise you may use the bundled Flex SDK in Flash
Builder, which will likely be newer. (This also raises Flash Player version
requirement.)

Adobe Flex SDK 4.5.1 can be downloaded the following URL:
http://sourceforge.net/adobe/flexsdk/wiki/Download%20Flex%204.5/


How to build StrobeMediaPlayback from OSMF source
-------------------------------------------------

1. Unzip OSMF source code zip file to a location available to Flash Builder.
This will typically be somewhere under your network home directory if running
Flash Builder on a Windows terminal server.


2. If you are going to use Flex 4.5.1, you will need to unzip that somewhere as
well, to make it available to Flash Builder. (Otherwise, just use the most
recent Flex version bundled with Flash Builder.)


3. Start Flash Builder.
If using Flex 4.5.1, you must add the SDK to Flash Builder preferences.
Go to "Window -> Preferences -> Flash Builder -> Installed Flex SDKs" and add
the unzipped Flex 4.5.1 SDK there.


4. Import the OSMF framework project into Flash Builder.
Go to "File -> Import Flash Builder Project" and select "Project folder", then
browse to "<location of unzipped OSMF source>/framework/OSMF" and import that.

If using Flex 4.5.1, then you'll need to adjust OSMF project properties. In
project properties, go to "Flex Library Compiler", select "Use a specific SDK"
and choose Flex 4.5.1.


5. Import the SMP player project into Flash Builder.
Go to "File -> Import Flash Builder Project" and select "Project folder", then
browse to "<location of unzipped OSMF>/player/StrobeMediaPlayback" and import
that.

Make sure you use the same Flex version as OSMF project set up in part 4 (same
procedure). The dependency from StrobeMediaPlayback to OSMF should be detected
and set up by Flash Builder automatically. If not, you may need to adjust the
"Action Script Build Path" for the StrobeMediaPlayback project.


6. Apply custom OSMF patches.
Apply patches against the OSMF project. Patch files are located under
'OSMF_2.0-patches/' in VTK source code.

Review each patch and check if it is necessary with the OSMF version you are
building. One patch is Vortex specific and fixes thumbnail URLs for poster
images.


7. Check for errors in Flash Builder (the "Problems" view) and make sure there
are none.


8. Do a release build of StrobeMediaPlayback.
Make sure StrobeMediaPlayback is the active project, then go to "Project ->
Export release build" and select folder for export.

On successful completion, the packaged StrobeMediaPlayback product should be
present in the folder, typically named "bin-release" (resources, SWF files, etc.)

Add the packaged version to VTK source code tree in a versioned directory
name and update references in VTK code as necessary.
