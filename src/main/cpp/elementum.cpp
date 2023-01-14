#include "elementum.h"

#include <string>

int main(int argc, char *argv[]) {
	std::string args;
	for (auto i = 1; i < argc; ++i) {
		args += ' ';
		args += argv[i];
	}
	return static_cast<int>(startWithArgs({args.c_str(), static_cast<ptrdiff_t>(args.length())}));
}
