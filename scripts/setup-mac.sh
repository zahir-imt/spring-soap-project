#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools
if [ "$(uname -s)" != Darwin ]; then
  echo "This helper is for macOS. On Windows/Linux install Java 17+ and use mvnw as described in README.md."
  exit 1
fi
if [ ! -x .tools/jdk/Contents/Home/bin/java ]; then
  case "$(uname -m)" in
    arm64) JDK_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.20.1_1.tar.gz'; JDK_SHA='196d13ba5f10414bef7f6a05a9b3f00edacb18ebacef2b99485db9e2ee18f0e8' ;;
    x86_64) JDK_URL='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_mac_hotspot_17.0.20.1_1.tar.gz'; JDK_SHA='c01975da12ed4235250ff891fe8bba73a9e73037d444b269c9d0922b5dbc8e0a' ;;
    *) echo "Unsupported Mac architecture"; exit 1 ;;
  esac
  echo "Preparing project-local Java. This can take a few minutes on first use."
  curl --fail --location --retry 3 "$JDK_URL" -o .tools/java.tar.gz
  ACTUAL=$(shasum -a 256 .tools/java.tar.gz | awk '{print $1}')
  [ "$ACTUAL" = "$JDK_SHA" ] || { echo "Java download verification failed."; exit 1; }
  mkdir -p .tools/jdk-stage
  tar -xzf .tools/java.tar.gz -C .tools/jdk-stage --strip-components=1
  mv .tools/jdk-stage .tools/jdk
  rm .tools/java.tar.gz
fi
if [ ! -x .tools/maven/bin/mvn ]; then
  echo "Preparing project-local Maven."
  curl --fail --location --retry 3 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz' -o .tools/maven.tar.gz
  ACTUAL=$(shasum -a 512 .tools/maven.tar.gz | awk '{print $1}')
  [ "$ACTUAL" = 'bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978' ] || { echo "Maven download verification failed."; exit 1; }
  mkdir -p .tools/maven-stage
  tar -xzf .tools/maven.tar.gz -C .tools/maven-stage --strip-components=1
  mv .tools/maven-stage .tools/maven
  rm .tools/maven.tar.gz
fi
echo "Project tools are ready."
