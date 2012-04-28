#!/bin/bash
#----------------------------------------------------------------------------|
# Creates the links to use hierarchyviewer{ui}lib in the eclipse-ide plugin.
# Run this from sdk/eclipse/scripts
#----------------------------------------------------------------------------|

set -e

D=`dirname "$0"`
source $D/common_setup.sh

# cd to the top android directory
cd "$D/../../../"

BASE="sdk/eclipse/plugins/com.android.ide.eclipse.hierarchyviewer"
DEST=$BASE/libs

mkdir -p $DEST

COPY_LIBS="hierarchyviewerlib"
ALL_LIBS="$COPY_LIBS swtmenubar"
echo "make java libs ..."
make -j3 showcommands $ALL_LIBS || die "Hierarchy Viewer: Fail to build one of $ALL_LIBS."

for LIB in $COPY_LIBS; do
    cpfile $DEST out/host/$PLATFORM/framework/$LIB.jar
done
