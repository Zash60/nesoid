# Configure to build only for 32-bit ARM as requested
APP_ABI := armeabi-v7a
APP_PLATFORM := android-21
APP_STL := c++_static
APP_CFLAGS += -fPIC
CFLAGS += -DNOCRYPT
