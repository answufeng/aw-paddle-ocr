#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#include "ppocrv5.h"
#include "ppocrv5_dict.h"

#define LOG_TAG "AwPPOCRv5"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static PPOCRv5* g_ppocrv5 = 0;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_answufeng_paddleocr_PPOCRv5Engine_nativeLoadModel(JNIEnv* env, jobject thiz, jobject assetManager, jstring modelType, jint targetSize, jboolean useGpu)
{
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr)
    {
        LOGE("Failed to get AssetManager");
        return JNI_FALSE;
    }

    const char* modeltype = env->GetStringUTFChars(modelType, 0);
    std::string det_parampath = std::string("PP_OCRv5_") + modeltype + "_det.ncnn.param";
    std::string det_modelpath = std::string("PP_OCRv5_") + modeltype + "_det.ncnn.bin";
    std::string rec_parampath = std::string("PP_OCRv5_") + modeltype + "_rec.ncnn.param";
    std::string rec_modelpath = std::string("PP_OCRv5_") + modeltype + "_rec.ncnn.bin";
    env->ReleaseStringUTFChars(modelType, modeltype);

    bool use_fp16 = (strcmp(modeltype, "mobile") == 0);

    if (g_ppocrv5)
    {
        delete g_ppocrv5;
        g_ppocrv5 = 0;
    }

    g_ppocrv5 = new PPOCRv5;

    int ret = g_ppocrv5->load(mgr, det_parampath.c_str(), det_modelpath.c_str(), rec_parampath.c_str(), rec_modelpath.c_str(), use_fp16, useGpu);
    if (ret != 0)
    {
        LOGE("Failed to load OCR model");
        delete g_ppocrv5;
        g_ppocrv5 = 0;
        return JNI_FALSE;
    }

    g_ppocrv5->set_target_size(targetSize);

    LOGI("OCR model loaded successfully, target_size=%d", targetSize);

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_answufeng_paddleocr_PPOCRv5Engine_nativeDetectAndRecognize(JNIEnv* env, jobject thiz, jobject bitmap)
{
    if (!g_ppocrv5)
    {
        LOGE("OCR engine not initialized");
        return env->NewStringUTF("");
    }

    AndroidBitmapInfo info;
    int ret = AndroidBitmap_getInfo(env, bitmap, &info);
    if (ret != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        LOGE("Failed to get bitmap info");
        return env->NewStringUTF("");
    }

    void* pixels = 0;
    ret = AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (ret != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        LOGE("Failed to lock bitmap pixels");
        return env->NewStringUTF("");
    }

    cv::Mat rgb;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        cv::Mat rgba(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(rgba, rgb, cv::COLOR_RGBA2RGB);
    }
    else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565)
    {
        cv::Mat rgb565(info.height, info.width, CV_8UC2, pixels);
        cv::cvtColor(rgb565, rgb, cv::COLOR_BGR5652RGB);
    }
    else
    {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("Unsupported bitmap format: %d", info.format);
        return env->NewStringUTF("");
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    std::vector<Object> objects;
    g_ppocrv5->detect_and_recognize(rgb, objects);

    std::string result;
    for (size_t i = 0; i < objects.size(); i++)
    {
        const Object& obj = objects[i];

        if (i > 0) result += "\n";

        cv::Point2f corners[4];
        obj.rrect.points(corners);

        char buf[256];
        snprintf(buf, sizeof(buf), "%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f|%.3f|%d|",
                 corners[0].x, corners[0].y,
                 corners[1].x, corners[1].y,
                 corners[2].x, corners[2].y,
                 corners[3].x, corners[3].y,
                 obj.prob, obj.orientation);
        result += buf;

        for (size_t j = 0; j < obj.text.size(); j++)
        {
            const Character& ch = obj.text[j];
            if (ch.id >= 0 && ch.id < character_dict_size)
            {
                result += character_dict[ch.id];
            }
        }
    }

    LOGI("OCR detected %d objects", (int)objects.size());

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_answufeng_paddleocr_PPOCRv5Engine_nativeRelease(JNIEnv* env, jobject thiz)
{
    if (g_ppocrv5)
    {
        delete g_ppocrv5;
        g_ppocrv5 = 0;
    }
    LOGI("OCR engine released");
}

}
