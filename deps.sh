#!/bin/bash

set -e
set -x

wget http://apache.osuosl.org/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
wget https://download.java.net/java/GA/jdk12.0.2/e482c34c86bd4bf8b56c0b35558996b9/10/GPL/openjdk-12.0.2_linux-x64_bin.tar.gz
wget https://github.com/google/or-tools/releases/download/v7.4/or-tools_ubuntu-16.04_v7.4.7247.tar.gz
wget https://github.com/MiniZinc/MiniZincIDE/releases/download/2.3.2/MiniZincIDE-2.3.2-bundle-linux-x86_64.tgz


tar -xzvf apache-maven-3.6.2-bin.tar.gz
tar -xzvf openjdk-12.0.2_linux-x64_bin.tar.gz
tar -xzvf or-tools_ubuntu-16.04_v7.4.7247.tar.gz
tar -xzvf MiniZincIDE-2.3.2-bundle-linux-x86_64.tgz
