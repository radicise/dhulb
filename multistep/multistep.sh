#!/bin/sh
#dhulb-multistep <dir> <filename> 16|32|64
set -e
cd ${1}
dhulbpp - ${1} < ${2} > ${2}_pp.temp
dhulbc ${3} -N < ${2}_pp.temp > ${2}_comp.s
as -o ${2}_assembled -m${3} ${2}_comp.s
