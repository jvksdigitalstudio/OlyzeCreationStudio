# Pruebas del núcleo nativo en el host. Uso: make -C app/src/test/cpp test
CXX      ?= g++
CORE     := ../../main/cpp/core
SRCS     := engine_tests.cpp $(CORE)/engine.cpp $(CORE)/timeline.cpp $(CORE)/reverb.cpp $(CORE)/resampler.cpp $(CORE)/mapped_wav.cpp $(CORE)/wav_io.cpp
FLAGS    := -std=c++17 -O2 -Wall -Wextra -Werror -pthread -ffp-contract=off

engine_tests: $(SRCS) $(wildcard $(CORE)/*.h)
	$(CXX) $(FLAGS) $(SRCS) -o $@

# Mismas pruebas bajo sanitizadores: detectan carreras de datos y accesos fuera de rango.
engine_tests_tsan: $(SRCS) $(wildcard $(CORE)/*.h)
	$(CXX) -std=c++17 -O1 -g -Wall -Wextra -pthread -ffp-contract=off -fsanitize=thread $(SRCS) -o $@
engine_tests_asan: $(SRCS) $(wildcard $(CORE)/*.h)
	$(CXX) -std=c++17 -O1 -g -Wall -Wextra -pthread -ffp-contract=off -fsanitize=address,undefined $(SRCS) -o $@

# Archivo que escribe StereoReverbGoldenTest.kt (`gradle testDebugUnitTest`). Falla si no existe.
GOLDEN ?= ../../../build/native-golden/reverb_golden.f32

test: engine_tests
	./engine_tests
golden: engine_tests
	OLYZE_TEST_QUICK=1 OLYZE_REVERB_GOLDEN=$(GOLDEN) ./engine_tests
test-sanitizers: engine_tests_tsan engine_tests_asan
	./engine_tests_asan && ./engine_tests_tsan
clean:
	rm -f engine_tests engine_tests_tsan engine_tests_asan
.PHONY: test golden test-sanitizers clean
