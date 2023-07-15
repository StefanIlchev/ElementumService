#include "elementum.h"

#include <string>

class Args {

	char *args;

public:
	Args(int argc, char *argv[]) {
		std::string str;
		for (auto i = 1; i < argc; ++i) {
			str += ' ';
			str += argv[i];
		}
		args = strcpy(new char[str.length() + 1], str.c_str());
	}

	~Args() {
		delete[] args;
	}

	char *get() {
		return args;
	}
};

int main(int argc, char *argv[]) {
	return static_cast<int>(startWithArgs(Args(argc, argv).get()));
}
