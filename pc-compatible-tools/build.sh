#!/bin/sh
set -e
cp boot.s boot-inter.s
cat ../src/DLib/pc/io.s >> boot-inter.s
dhulbpp - - < test.dhulb > boot-precursor-pp.dhulb
dhulbc 16 -tNGT < boot-precursor-pp.dhulb >> boot-inter.s
as -arch i386 -m16 -o boot.o boot-inter.s
strip boot.o
FILEOFF=$(otool -l boot.o | grep "fileoff" | grep -oE -m 1 '[^ ]+$')
dd if=boot.o of=boot-inter.bin bs=${FILEOFF} skip=1
FILEOFF=$(otool -l boot.o | grep " size" | grep -oE -m 1 '[^ ]+$')
FILEOFF=$(printf "%d" ${FILEOFF})
dd if=boot-inter.bin of=boot.bin bs=${FILEOFF} count=1
dd if=/dev/zero of=boot.bin bs=512 count=1 seek=2879
