#!/bin/bash
function die() {
    echo "Error: $*"
    exit 1
}

set -e # fail early

# CD to the top android directory
D=`dirname "$0"`
cd "$D/../../../"

DEST="sdk/eclipse/plugins/com.android.ide.eclipse.traceview/libs"
# computes "../.." from DEST to here (in /android)
BACK=`echo $DEST | sed 's@[^/]*@..@g'`

mkdir -p $DEST

LIBS="traceview"

echo "make java libs ..."
make -j3 showcommands $LIBS || die "TRACEVIEW: Fail to build one of $LIBS."

echo "Copying java libs to $DEST"


HOST=`uname`
if [ "$HOST" == "Linux" ]; then
    for LIB in $LIBS; do
        ln -svf $BACK/out/host/linux-x86/framework/$LIB.jar "$DEST/"
    done

elif [ "$HOST" == "Darwin" ]; then
    for LIB in $LIBS; do
        ln -svf $BACK/out/host/darwin-x86/framework/$LIB.jar "$DEST/"
    done

elif [ "${HOST:0:6}" == "CYGWIN" ]; then
    for LIB in $LIBS; do
        cp -vf  out/host/windows-x86/framework/$LIB.jar "$DEST/"
    done

    chmod -v a+rx "$DEST"/*.jar
else
    echo "Unsupported platform ($HOST). Nothing done."
fi

