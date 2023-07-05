#include "elementum.h"

#include <string>

int main(int argc, char *argv[]) {
	std::string str;
	for (auto i = 1; i < argc; ++i) {
		str += ' ';
		str += argv[i];
	}
	auto args = new char[str.length() + 1];
	strcpy(args, str.c_str());
	auto result = static_cast<int>(startWithArgs(args));
	delete[] args;
	return result;
}
