#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_projectguardian_guardian_RangeFinderActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
