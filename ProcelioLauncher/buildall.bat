dir
mkdir Release
rmdir /Q /S .\jpckg\ProcelioLauncher
call ..\gradlew clean &  call ..\gradlew makeImage
copy .\jpckg\LauncherUpdater.exe .\jpckg\ProcelioLauncher\LauncherUpdater.exe
copy .\jpckg\RunLaunchUpdate.exe .\jpckg\ProcelioLauncher\RunLaunchUpdate.exe
copy .\jpckg\try.bat .\jpckg\ProcelioLauncher\try.bat
7z a .\Release\windows-zip.zip .\jpckg\ProcelioLauncher\*
"C:\Users\Brennan\AppData\Local\Programs\Inno Setup 6\ISCC.exe" .\WinInstaller\inno_launcherbuilder.iss
move .\WinInstaller\ProcelioLauncherInstaller.exe .\Release\windows-installer.exe