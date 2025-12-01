LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := emu
LOCAL_SRC_FILES := Emulator.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/..
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := nes
LOCAL_SRC_FILES := nes/NES.cpp nes/Mapper.cpp nes/PPU.cpp nes/APU.cpp nes/MMC3.cpp nes/NsfPlayer.cpp nes/Vrc6.cpp nes/FdsSound.cpp nes/Nmt.cpp nes/Mappers.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/nes
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := fceumm
LOCAL_SRC_FILES := fceu/mappers/UNIF.cpp fceu/mappers/UNIF_cis.cpp fceu/mappers/UNIF_8001.cpp fceu/mappers/UNIF_8002.cpp fceu/mappers/UNIF_LH32.cpp fceu/mappers/UNIF_LH53.cpp fceu/mappers/UNIF_SxROM.cpp fceu/mappers/UNIF_SxS.cpp fceu/mappers/UNIF_TLROM.cpp fceu/mappers/UNIF_UOROM.cpp fceu/mappers/UNIF_43272.cpp fceu/mappers/UNIF_8157.cpp fceu/mappers/UNIF_CC21.cpp fceu/mappers/UNIF_1K-VRC-E.cpp fceu/mappers/UNIF_16KB-EPR-ROM.cpp fceu/mappers/UNIF_2x8KB-EPR-ROM.cpp fceu/mappers/UNIF_4x4KB-GAMEMASTER.cpp fceu/mappers/UNIF_8KB-EPR-ROM.cpp fceu/mappers/UNIF_16KB-EEPROM.cpp fceu/mappers/UNIF_4x4KB-WRAM.cpp fceu/mappers/UNIF_32KB-EEPROM.cpp fceu/mappers/UNIF_8KB-MMC3.cpp fceu/mappers/UNIF_16KB-MMC3.cpp fceu/mappers/UNIF_32KB-MMC3.cpp fceu/mappers/UNIF_64KB-MMC3.cpp fceu/mappers/UNIF_128KB-MMC3.cpp fceu/mappers/UNIF_256KB-MMC3.cpp fceu/mappers/UNIF_512KB-MMC3.cpp fceu/mappers/UNIF_1MB-MMC3.cpp fceu/mappers/UNIF_2MB-MMC3.cpp fceu/mappers/UNIF_4MB-MMC3.cpp fceu/mappers/UNIF_8MB-MMC3.cpp fceu/mappers/UNIF_16MB-MMC3.cpp fceu/mappers/UNIF_32MB-MMC3.cpp fceu/mappers/UNIF_64MB-MMC3.cpp fceu/mappers/UNIF_128MB-MMC3.cpp fceu/mappers/UNIF_256MB-MMC3.cpp fceu/mappers/UNIF_512MB-MMC3.cpp fceu/mappers/UNIF_1GB-MMC3.cpp fceu/mappers/UNIF_2GB-MMC3.cpp fceu/mappers/UNIF_4GB-MMC3.cpp fceu/mappers/UNIF_8GB-MMC3.cpp fceu/mappers/UNIF_16GB-MMC3.cpp fceu/mappers/UNIF_32GB-MMC3.cpp fceu/mappers/UNIF_64GB-MMC3.cpp fceu/mappers/UNIF_128GB-MMC3.cpp fceu/mappers/UNIF_256GB-MMC3.cpp fceu/mappers/UNIF_512GB-MMC3.cpp fceu/mappers/UNIF_1TB-MMC3.cpp fceu/mappers/UNIF_2TB-MMC3.cpp fceu/mappers/UNIF_4TB-MMC3.cpp fceu/mappers/UNIF_8TB-MMC3.cpp fceu/mappers/UNIF_16TB-MMC3.cpp fceu/mappers/UNIF_32TB-MMC3.cpp fceu/mappers/UNIF_64TB-MMC3.cpp fceu/mappers/UNIF_128TB-MMC3.cpp fceu/mappers/UNIF_256TB-MMC3.cpp fceu/mappers/UNIF_512TB-MMC3.cpp fceu/mappers/UNIF_1PB-MMC3.cpp fceu/mappers/UNIF_2PB-MMC3.cpp fceu/mappers/UNIF_4PB-MMC3.cpp fceu/mappers/UNIF_8PB-MMC3.cpp fceu/mappers/UNIF_16PB-MMC3.cpp fceu/mappers/UNIF_32PB-MMC3.cpp fceu/mappers/UNIF_64PB-MMC3.cpp fceu/mappers/UNIF_128PB-MMC3.cpp fceu/mappers/UNIF_256PB-MMC3.cpp fceu/mappers/UNIF_512PB-MMC3.cpp fceu/mappers/UNIF_1EB-MMC3.cpp fceu/mappers/UNIF_2EB-MMC3.cpp fceu/mappers/UNIF_4EB-MMC3.cpp fceu/mappers/UNIF_8EB-MMC3.cpp fceu/mappers/UNIF_16EB-MMC3.cpp fceu/mappers/UNIF_32EB-MMC3.cpp fceu/mappers/UNIF_64EB-MMC3.cpp fceu/mappers/UNIF_128EB-MMC3.cpp fceu/mappers/UNIF_256EB-MMC3.cpp fceu/mappers/UNIF_512EB-MMC3.cpp fceu/mappers/UNIF_1ZB-MMC3.cpp fceu/mappers/UNIF_2ZB-MMC3.cpp fceu/mappers/UNIF_4ZB-MMC3.cpp fceu/mappers/UNIF_8ZB-MMC3.cpp fceu/mappers/UNIF_16ZB-MMC3.cpp fceu/mappers/UNIF_32ZB-MMC3.cpp fceu/mappers/UNIF_64ZB-MMC3.cpp fceu/mappers/UNIF_128ZB-MMC3.cpp fceu/mappers/UNIF_256ZB-MMC3.cpp fceu/mappers/UNIF_512ZB-MMC3.cpp fceu/mappers/UNIF_1YB-MMC3.cpp fceu/mappers/UNIF_2YB-MMC3.cpp fceu/mappers/UNIF_4YB-MMC3.cpp fceu/mappers/UNIF_8YB-MMC3.cpp fceu/mappers/UNIF_16YB-MMC3.cpp fceu/mappers/UNIF_32YB-MMC3.cpp fceu/mappers/UNIF_64YB-MMC3.cpp fceu/mappers/UNIF_128YB-MMC3.cpp fceu/mappers/UNIF_256YB-MMC3.cpp fceu/mappers/UNIF_512YB-MMC3.cpp fceu/mappers/UNIF_1DB-MMC3.cpp fceu/mappers/UNIF_2DB-MMC3.cpp fceu/mappers/UNIF_4DB-MMC3.cpp fceu/mappers/UNIF_8DB-MMC3.cpp fceu/mappers/UNIF_16DB-MMC3.cpp fceu/mappers/UNIF_32DB-MMC3.cpp fceu/mappers/UNIF_64DB-MMC3.cpp fceu/mappers/UNIF_128DB-MMC3.cpp fceu/mappers/UNIF_256DB-MMC3.cpp fceu/mappers/UNIF_512DB-MMC3.cpp fceu/mappers/UNIF_1NB-MMC3.cpp fceu/mappers/UNIF_2NB-MMC3.cpp fceu/mappers/UN
