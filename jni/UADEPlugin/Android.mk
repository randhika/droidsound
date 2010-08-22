LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := uade

LOCAL_SRC_FILES :=  UADEPlugin.cpp uadecontrol.c uadeconf.c md5.c eagleplayer.c amifilemagic.c

# MY_SOURCES := $(wildcard $(LOCAL_PATH)/gme/*.cpp)
# LOCAL_SRC_FILES += $(MY_SOURCES:$(LOCAL_PATH)%=%)

#LOCAL_CFLAGS := -I$(LOCAL_PATH)/include/sidplay -I$(LOCAL_PATH)/include
#LOCAL_LDLIBS := -llog -L$(LOCAL_PATH)/lib/sidplay/builders -lresid-builder -L$(LOCAL_PATH)/lib -lresid -lsidplay2

LOCAL_CFLAGS := -I$(LOCAL_PATH)/uade/include

LOCAL_LDLIBS := -llog -L$(LOCAL_PATH)/uade -luade

include $(BUILD_SHARED_LIBRARY)

# gcc -I. -I./include -c  -fomit-frame-pointer -Wall -Wno-unused -Wno-format -Wmissing-prototypes -Wstrict-prototypes -DGCCCONSTFUNC="__attribute__((const))" -D_REENTRANT -fno-exceptions -fno-strength-reduce -DREGPARAM= -D__inline__=inline -O2  -g   main.c -o main.o