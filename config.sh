#!/bin/sh
set -ue
cd ${1}
DHULB_PATHNAME="$(pwd)"
mkdir -p bin
printf "Usage: The argument is the path of the Dhulb home directory\n"
printf "LibPath=${DHULB_PATHNAME}/src/DLib\nExtPath=${DHULB_PATHNAME}/src/DExt\nDefPath=${DHULB_PATHNAME}/targets/linux_i386.dhulb_def\n" > ~/.dhulb_conf
printf "~/.dhulb_conf has been regenerated\n"
printf "export DHULB_PATH as ${DHULB_PATHNAME}\n"
printf "Commands are available in cmd/bin\n"
