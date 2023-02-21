#/bin/sh
set -ue
printf "Usage: The argument is the directory of the Dhulb compiler source repository\n"
cd ${1}
mkdir -p bin
cd src
javac -d ../bin Dhulb/Dhulb.java
javac -d ../bin Dhulb/Preprocessor.java
javac -d ../bin Dhulb/Dhelp.java
