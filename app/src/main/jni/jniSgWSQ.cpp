//
// Created by sbyu on 2018-12-20.
//
#ifdef __cplusplus
extern "C" {
#endif
#include <wsq.h>
#include <ihead.h>
#include <dataio.h>
#include <version.h>
#ifdef __cplusplus
}
#endif

//#define _DEBUG_MSG_
#include <com_secugen_u20_bt_android_ble_demo_DeviceControlActivity.h>

#ifdef _DEBUG_MSG_
#include "android/log.h"
#endif

int debug = 0;

/*
 * Class:     com_secugen_u20_bt_android_ble_demo_DeviceControlActivity
 * Method:    jniSgWSQDecode
 * Signature: (Lcom/secugen/u20_bt_android_ble_demo/WSQInfoClass;[BI)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_secugen_u20_1bt_1android_1ble_1demo_DeviceControlActivity_jniSgWSQDecode
  (JNIEnv *env, jobject obj, jobject wsqInfo, jbyteArray wsqImage, jint wsqImageLength)
{

  jfieldID field;
  jclass clsWSQInfo = env->GetObjectClass(wsqInfo);

  unsigned char *fingerImageOut = 0;
  int width = 0;
  int height = 0;
  int pixelDepth = 0;
  int ppi = 0;
  int lossyFlag = 0;

#ifdef _DEBUG_MSG_
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(wsqImageLength:%d)", wsqImageLength);
#endif

  jbyte *jwsqbuf = env->GetByteArrayElements(wsqImage, NULL);

  int ret = wsq_decode_mem(&fingerImageOut,
                            &width,
                            &height,
                            &pixelDepth,
                            &ppi,
                            &lossyFlag,
                            (unsigned char*)jwsqbuf,
                            (const int) wsqImageLength);

  field = env->GetFieldID(clsWSQInfo, "width", "I");
  env->SetIntField(wsqInfo, field, width);

  field = env->GetFieldID(clsWSQInfo, "height", "I");
  env->SetIntField(wsqInfo, field, height);

  field = env->GetFieldID(clsWSQInfo, "pixelDepth", "I");
  env->SetIntField(wsqInfo, field, pixelDepth);

  field = env->GetFieldID(clsWSQInfo, "ppi", "I");
  env->SetIntField(wsqInfo, field, ppi);

  field = env->GetFieldID(clsWSQInfo, "lossyFlag", "I");
  env->SetIntField(wsqInfo, field, lossyFlag);

#ifdef _DEBUG_MSG_
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(wsq_decode_mem return:%d)", ret);
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(width:%d)", width);
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(height:%d)", height);
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(pixelDepth:%d)", pixelDepth);
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(ppi:%d)", ppi);
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(lossyFlag:%d)", lossyFlag);
  __android_log_print(ANDROID_LOG_INFO, "JNI_LOG", "(last data:0x%02X 0x%02X)", (unsigned char)jwsqbuf[wsqImageLength-2], (unsigned char)jwsqbuf[wsqImageLength-1]);
#endif

  jbyteArray returnValue = env->NewByteArray(width*height);
  env->SetByteArrayRegion(returnValue, 0, width*height, (jbyte*)fingerImageOut);

  return returnValue;
}