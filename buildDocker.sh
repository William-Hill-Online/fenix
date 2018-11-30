#!/usr/bin/env bash

# set workspace
WORKSPACE=$(pwd)

# Build and create TGZ package application via SBT.
sbt clean universal:packageZipTarball

# Get the full path to the TGZ package.
cd $WORKSPACE/target/universal
APP_TGZ=$WORKSPACE/target/universal/$(ls *.tgz)

# Create docker target directory where we unpack the application TGZ package.
DOCKER_BUILD_DIR=$WORKSPACE/target/myDocker
mkdir -p $DOCKER_BUILD_DIR/opt

# Unpack TGZ package.
cd $DOCKER_BUILD_DIR/opt
tar -xzf $APP_TGZ

# Rename resulting dir.
mv $(ls) fenix

# Build docker image.
cp $WORKSPACE/Dockerfile $DOCKER_BUILD_DIR
cd $DOCKER_BUILD_DIR
docker --debug build -t martin.hatas/fenix .

# Tag it and push it to DEV registry.
#docker tag williamhill/fenix 10.210.201.139:5000/williamhill/fenix
#docker push docker-registry.prod.williamhill.plc/martin.hatas/fenix:1.0.0-SNAPSHOT

# Clean-up.
rm -rf $DOCKER_BUILD_DIR
