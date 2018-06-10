#!/usr/bin/env bash
cd ..
rm -r ProcelioLauncher/ProcelioGame/
mkdir ProcelioLauncher/ProcelioGame
cp -r ProcelioServer/gameBuilds/linux/builds/build-0.0.1/* ProcelioLauncher/ProcelioGame/
#cp -r ProcelioServer/gameBuilds/linux/builds/build-1.0.0/* ProcelioLauncher/ProcelioGame/