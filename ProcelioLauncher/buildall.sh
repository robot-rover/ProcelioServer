#/bin/bash
rm -rf ./jpckg/ProcelioLauncher
../gradlew clean; ../gradlew makeImage
cp ./jpckg/constants.h ./jpckg/ProcelioLauncher
cp ./jpckg/INFO.txt ./jpckg/ProcelioLauncher
cp ./jpckg/LauncherUpdater ./jpckg/ProcelioLauncher
cp ./jpckg/updater.cpp ./jpckg/ProcelioLauncher
cp ./jpckg/launch.sh ./jpckg/ProcelioLauncher
7z a ./Release/linux-zip.zip ./jpckg/ProcelioLauncher/*
