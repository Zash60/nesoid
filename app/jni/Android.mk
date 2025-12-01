LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := emu
LOCAL_SRC_FILES := common/emulator.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/..
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := nes
LOCAL_SRC_FILES := nes/NES.cpp nes/Mapper.cpp nes/PPU.cpp nes/APU.cpp nes/MMC3.cpp nes/NsfPlayer.cpp nes/Vrc6.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/nes
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := fceumm
LOCAL_SRC_FILES := fceu/mappers/UNIF.cpp fceu/mappers/UNIF_CIs.cpp fceu/mappers/UNIF_8001.cpp fceu/mappers/UNIF_B...
LOCAL_C_INCLUDES += $(LOCAL_PATH)/fceu
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_SHARED_LIBRARY)
