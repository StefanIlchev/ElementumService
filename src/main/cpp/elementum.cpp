#include "elementum.h"

#include <cstring>

class Args {

	char *args;

public:
	Args(int argc, char *argv[]) {
		auto len = 1U;
		for (auto i = 1; i < argc; ++i) {
			++len;
			len += strlen(argv[i]);
		}
		args = new char[len]{};
		for (auto i = 1; i < argc; ++i) {
			strcat(args, " ");
			strcat(args, argv[i]);
		}
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
