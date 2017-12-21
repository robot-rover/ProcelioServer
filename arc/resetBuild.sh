#!/usr/bin/env bash
cd ..
rm -r ProcelioGame/
mkdir ProcelioGame
cp -r ProcelioServer/gameBuilds/linux/builds/build-0.0.1/* ProcelioGame/
cp -r ProcelioServer/gameBuilds/linux/builds/build-1.0.0/* ProcelioGame/