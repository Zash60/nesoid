LOCAL_PATH := $(call my-dir)

# Save the local path because including other makefiles overwrites LOCAL_PATH
ROOT_PATH := $(LOCAL_PATH)

# Include common module
include $(ROOT_PATH)/common/Android.mk

# Include NES library module
include $(ROOT_PATH)/neslib/Android.mk
