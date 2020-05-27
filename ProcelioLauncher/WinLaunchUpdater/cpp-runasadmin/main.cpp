#include <string>
#include <filesystem>

#include "constants.h"
namespace fs = std::filesystem;

int winmain(int argc, char** argv);
int lnxmain(int argc, char** argv);
int downloadmain(int argc, char** argv);

int main(int argc, char** argv) {
	// FOR BUILDING DOWNLOAD ELEVATER
	//return downloadmain(argc, argv);

	// FOR BUILDING UPDATER
	 return winmain(argc, argv);
}