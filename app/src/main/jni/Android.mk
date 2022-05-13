LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := sgwsq-jni
LOCAL_SRC_FILES := jniSgWSQ.cpp \
    $(LOCAL_PATH)/sgwsq/src/wsq/cropcoeff.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/decoder.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/encoder.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/globals.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/huff.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/ppi.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/sd14util.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/tableio.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/tree.c \
    $(LOCAL_PATH)/sgwsq/src/wsq/util.c \
    $(LOCAL_PATH)/sgwsq/src/jpegl/jhuff.c \
    $(LOCAL_PATH)/sgwsq/src/jpegl/jhuftable.c \
    $(LOCAL_PATH)/sgwsq/src/jpegl/jtableio.c \
    $(LOCAL_PATH)/sgwsq/src/common/clapck/strfet.c \
    $(LOCAL_PATH)/sgwsq/src/common/fet/allocfet.c \
    $(LOCAL_PATH)/sgwsq/src/common/fet/delfet.c \
    $(LOCAL_PATH)/sgwsq/src/common/fet/extrfet.c \
    $(LOCAL_PATH)/sgwsq/src/common/fet/freefet.c \
    $(LOCAL_PATH)/sgwsq/src/common/fet/lkupfet.c \
    $(LOCAL_PATH)/sgwsq/src/common/fet/nistcom.c \
    $(LOCAL_PATH)/sgwsq/src/common/fet/updatfet.c \
    $(LOCAL_PATH)/sgwsq/src/common/ioutil/dataio.c \
    $(LOCAL_PATH)/sgwsq/src/common/ioutil/filesize.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/bres.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/bubble.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/computil.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/fatalerr.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/invbyte.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/invbytes.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/memalloc.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/ssxstats.c \
    $(LOCAL_PATH)/sgwsq/src/common/util/syserr.c

LOCAL_C_INCLUDES += $(LOCAL_PATH)/sgwsq/include/common
LOCAL_C_INCLUDES += $(LOCAL_PATH)/sgwsq/include/wsq

LOCAL_CFLAGS += -DTARGET_OS
LOCAL_CFLAGS += -D__NBISLE__
LOCAL_CFLAGS += -D_DEFAULT_SOURCE

LOCAL_CPPFLAGS += -std=c++11

LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)