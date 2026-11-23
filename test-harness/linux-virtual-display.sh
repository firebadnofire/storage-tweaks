#!/bin/sh
# Run GUI fixtures on a private X server, never the user's WSLg display.
set -eu
: "${ST_GUI_TOOLS:?Set ST_GUI_TOOLS to the extracted, checksum-verified Linux tools directory}"
test "$#" -gt 0 || { echo 'Pass the test command and arguments' >&2; exit 2; }
test -f "$ST_GUI_TOOLS/root/usr/bin/Xvfb"
test -f "$ST_GUI_TOOLS/jdk8u472-b08/bin/java"
scratch=/tmp/storage-tweaks-gui-tools
if [ "$(id -u)" = 0 ]; then
    workspace=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
    validation=$(CDPATH= cd -- "$ST_GUI_TOOLS/.." && pwd)
    # DrvFS maps these task files to nobody/users. Drop privileges before tests.
    install -d -o 65534 -g 100 "$scratch" "$scratch/jdk8/bin" "$scratch/jdk8/jre/bin"
    install -d -m 700 -o 65534 -g 100 "$scratch/runtime"
    for binary in Xvfb xvfb-run xkbcomp; do
        cmp -s "$ST_GUI_TOOLS/root/usr/bin/$binary" "$scratch/$binary" ||
            install -m 755 "$ST_GUI_TOOLS/root/usr/bin/$binary" "$scratch/$binary"
    done
    for directory in bin jre/bin; do
        for binary in "$ST_GUI_TOOLS/jdk8u472-b08/$directory/"*; do
            destination="$scratch/jdk8/$directory/${binary##*/}"
            cmp -s "$binary" "$destination" || install -m 755 "$binary" "$destination"
        done
    done
    ln -sfn "$ST_GUI_TOOLS/jdk8u472-b08/lib" "$scratch/jdk8/lib"
    ln -sfn "$ST_GUI_TOOLS/jdk8u472-b08/jre/lib" "$scratch/jdk8/jre/lib"
    # Xvfb hardcodes /usr/bin/xkbcomp. Supply it in a private mount namespace;
    # no package installation or change to the host /usr/bin is necessary.
    mkdir -p "$scratch/bin/original"
    for binary in /usr/bin/*; do
        name=${binary##*/}
        [ "$name" = xkbcomp ] || ln -sfn "original/$name" "$scratch/bin/$name"
    done
    cmp -s "$scratch/xkbcomp" "$scratch/bin/xkbcomp" || install -m 755 "$scratch/xkbcomp" "$scratch/bin/xkbcomp"
    exec bwrap --ro-bind / / --dev-bind /dev /dev \
        --bind "$workspace" "$workspace" --bind "$validation" "$validation" --bind /tmp /tmp \
        --ro-bind "$scratch/bin" /usr/bin --ro-bind /usr/bin /usr/bin/original \
        --unshare-pid --die-with-parent \
        /usr/bin/setpriv --reuid=65534 --regid=100 --clear-groups \
        /usr/bin/env ST_GUI_TOOLS="$ST_GUI_TOOLS" /usr/bin/sh "$0" "$@"
fi
test "$(id -u)" = 65534 || { echo 'This DrvFS fixture runner expects uid 65534' >&2; exit 2; }
export JAVA_HOME="$scratch/jdk8"
export PATH="$scratch:$JAVA_HOME/bin:/usr/bin:/bin"
export LD_LIBRARY_PATH="$ST_GUI_TOOLS/root/usr/lib/x86_64-linux-gnu"
export HOME="$ST_GUI_TOOLS/home"
export TMPDIR="$scratch/runtime"
export LIBGL_ALWAYS_SOFTWARE=1
export LP_NUM_THREADS=2
export ALSOFT_DRIVERS=null
mkdir -p "$HOME" "$TMPDIR"
unset DISPLAY WAYLAND_DISPLAY
exec nice -n 10 "$scratch/xvfb-run" -a -e "$ST_GUI_TOOLS/xvfb.log" \
    -s '-screen 0 1280x720x24 -nolisten tcp' "$@"
