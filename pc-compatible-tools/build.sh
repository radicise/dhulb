#!/bin/sh
set -e
as -m16 -o boot.o boot-inter.s
rm boot-inter.s
strip boot.o
FILEOFF=$(otool -l boot.o | grep "fileoff" | grep -oE '[^ ]+$')
dd if=boot.o of=boot-inter.bin bs=${FILEOFF} skip=1
FILEOFF=$(otool -l boot.o | grep " size" | grep -oE '[^ ]+$')
FILEOFF=$(printf "%d" ${FILEOFF})
dd if=boot-inter.bin of=boot.bin bs=${FILEOFF} count=1
rm boot-inter.bin
