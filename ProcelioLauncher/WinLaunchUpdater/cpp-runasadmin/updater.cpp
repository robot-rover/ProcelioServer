#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <set>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include "Windows.h"

#include "constants.h"
namespace fs = std::filesystem;

// "Global" variables (there's only one file in this program)
static fs::path executablePath; // the path of this program
static fs::path unzipPath; // the root of the "unzip" folder
static fs::path directoryRoot; // the root of the launcher directory (/**/launcher/)

// Return true iff subpath is contained within path (presumably a directory)
bool isSubDir(const fs::path& path, fs::path subpath);

// Find all current files inside of 'directoryRoot' that aren't this or unzip; store paths in the set
void findCurrentFiles(std::set<fs::path>& currentFiles, fs::path dir);
void findCurrentFiles(std::set<fs::path>& currentFiles);

// Replace all files with their corresponding file in unzipPath. Removes all matched files from set
bool updateFiles(std::set<fs::path>& currentFiles);

// Clears all files in the given set -- files which didn't have an equivalent in the new version
void clearFiles(std::set<fs::path>& remainingFiles);

void mainfunc(char* argv0);
int winmain(int argc, char** argv);
int lnxmain(int argc, char** argv);

struct {
	fs::path launcher;

	void setup() {
		launcher = executablePath;
		launcher.replace_filename("ProcelioLauncher.exe");
	}
} configuration;

void mainfunc(char* argv0) {
	executablePath = fs::current_path().append("LauncherUpdater.exe");// fs::canonical(argv[0]);
	directoryRoot = executablePath.parent_path();
	unzipPath = executablePath;
	unzipPath.replace_filename(DOWNLOAD_FOLDER);
	configuration.setup();



	if (fs::exists(unzipPath)) {
		std::set<fs::path> paths;
		std::cout << "Indexing files..." << std::endl;
		findCurrentFiles(paths);

		std::cout << "Copying new launcher files" << std::endl;
		updateFiles(paths);

		std::cout << "Clearing old files" << std::endl;
		clearFiles(paths);

		std::cout << "Deleting temp files" << std::endl;
		fs::remove_all(unzipPath);
	}

	std::cout << "Restarting launcher: " << configuration.launcher << std::endl;
	fs::permissions(configuration.launcher, fs::perms::all);
}

int winmain(int argc, char** argv) {
	mainfunc(argv[0]);
	std::wstring pt = configuration.launcher.wstring();

	STARTUPINFO info = { sizeof(info) };
	PROCESS_INFORMATION processInfo;
	if (CreateProcess(pt.c_str(), NULL, NULL, NULL, TRUE, 0, NULL, NULL, &info, &processInfo))
	{
		WaitForSingleObject(processInfo.hProcess, INFINITE);
		CloseHandle(processInfo.hProcess);
		CloseHandle(processInfo.hThread);
		return 0;
	}
	return 1;
}

int lnxmain(int argc, char** argv) {
	mainfunc(argv[0]);
	return system(("\"" + configuration.launcher.string() + "\"").c_str());
}


bool isSubDir(const fs::path& path, fs::path subpath) {
	if (path == subpath)
		return true;

	auto it1 = path.lexically_normal().begin();
	auto it2 = subpath.lexically_normal().begin();

	while (it1 != path.end() && it2 != subpath.end())
	{
		if ((*it1) != (*it2))
			return false;
		++it1;
		++it2;
	}

	return it1 == path.end();
}

void findCurrentFiles(std::set<fs::path>& currentFiles, fs::path dir) {
	for (auto& file : fs::directory_iterator(dir)) {
		if (file.is_directory()) {
			findCurrentFiles(currentFiles, file.path());
		}
		else {
			currentFiles.insert(file.path());
		}
	}
}

void findCurrentFiles(std::set<fs::path>& currentFiles) {
	for (auto& file : fs::directory_iterator(directoryRoot)) {
		if (file == executablePath)
			continue;
		if (file == unzipPath)
			continue;
		if (file.is_directory())
			findCurrentFiles(currentFiles, file);
		else
			currentFiles.insert(file.path());
	}
}

bool updateFiles(std::set<fs::path>& currentFiles) {
	for (auto& file : fs::recursive_directory_iterator(unzipPath)) {
		if (file.is_directory())
			continue;
		auto rel = fs::relative(file, unzipPath);
		auto newPos = directoryRoot;
		newPos /= rel;
		if (newPos == executablePath)
			continue;
		currentFiles.erase(newPos);

		try {
			if (fs::exists(newPos))
				fs::permissions(newPos, fs::perms::all);

			fs::copy(file, newPos, fs::copy_options::overwrite_existing);
		}
		catch (fs::filesystem_error fse) {
			std::cerr << "Error copying from " << fse.path1() << " to " << fse.path2() << std::endl;
			std::cerr << fse.code().message() << std::endl;
		}
	}
	return true;
}

void clearFiles(std::set<fs::path>& remainingFiles) {
	for (auto& path : remainingFiles) {
		fs::remove(path);
	}
}
