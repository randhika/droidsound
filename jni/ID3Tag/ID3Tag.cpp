
#include <stdlib.h>
#include <stdio.h>

#include <string.h>
#include <ctype.h>

#include <android/log.h> 

#include <jni.h>

#include "libid3tag/id3tag.h"

#include "com_ssb_droidsound_utils_ID3Tag.h"

jstring NewString(JNIEnv *env, const char *str)
{
	static char temp[256];
	char *ptr = temp;
	while(*str) {
		char c = *str++;
		*ptr++ = (c < 0x7f && c >= 0x20) || c >= 0xa0 ? c : '?';
	}
	*ptr++ = 0;
	jstring j = env->NewStringUTF(temp);
	return j;
}

static jfieldID refField;


JNIEXPORT jboolean JNICALL Java_com_ssb_droidsound_utils_ID3Tag_openID3Tag(JNIEnv *env, jobject obj, jstring fileName)
{

	jboolean iscopy;
	const char *fname = env->GetStringUTFChars(fileName, &iscopy);

	struct id3_file *id3file = id3_file_open(fname, ID3_FILE_MODE_READONLY);

	__android_log_print(ANDROID_LOG_VERBOSE, "ID3Tag", "id3 %p", id3file);


	jclass cl = env->GetObjectClass(obj);
	refField = env->GetFieldID(cl, "id3Ref", "J");
	env->SetLongField(obj, refField, (jlong)id3file);

	__android_log_print(ANDROID_LOG_VERBOSE, "ID3Tag", "id3 %p", id3file);

	env->ReleaseStringUTFChars(fileName, fname);

}

JNIEXPORT void JNICALL Java_com_ssb_droidsound_utils_ID3Tag_closeID3Tag(JNIEnv *env, jobject obj)
{
	struct id3_file *id3file = (struct id3_file*)env->GetLongField(obj, refField);
	id3_file_close(id3file);

}

#define INFO_TITLE 0
#define INFO_AUTHOR 1
#define INFO_LENGTH 2
#define INFO_TYPE 3
#define INFO_COPYRIGHT 4
#define INFO_GAME 5
#define INFO_SUBTUNES 6
#define INFO_STARTTUNE 7

#define ID3INFO_GENRE 100
#define ID3INFO_COMMENT 101
#define ID3INFO_ALBUM 102
#define ID3INFO_TRACK 103

JNIEXPORT jstring JNICALL Java_com_ssb_droidsound_utils_ID3Tag_getStringInfo(JNIEnv *env, jobject obj, jint what)
{
	struct id3_file *id3file = (struct id3_file*)env->GetLongField(obj, refField);
	struct id3_tag *tag = id3_file_tag(id3file);
	const id3_ucs4_t *title = NULL;
	struct id3_frame *frame = NULL;

	__android_log_print(ANDROID_LOG_VERBOSE, "ID3Tag", "id3tag %p", tag);

	switch(what) {
	case INFO_TITLE:
		frame = id3_tag_findframe(tag, ID3_FRAME_TITLE, 0);
		break;
	case INFO_AUTHOR:
		frame = id3_tag_findframe(tag, ID3_FRAME_ARTIST, 0);
		break;
	case INFO_COPYRIGHT:
		frame = id3_tag_findframe(tag, ID3_FRAME_YEAR, 0);
		break;
	case ID3INFO_GENRE:
		frame = id3_tag_findframe(tag, ID3_FRAME_GENRE, 0);
		if(frame) {
			title = id3_field_getstrings(&frame->fields[1], 0);
			title = id3_genre_name(title);
		}
		break;
	case ID3INFO_ALBUM:
		frame = id3_tag_findframe(tag, ID3_FRAME_ALBUM, 0);
		break;
	case ID3INFO_TRACK:
		frame = id3_tag_findframe(tag, ID3_FRAME_TRACK, 0);
		break;
	case ID3INFO_COMMENT:
		frame = id3_tag_findframe(tag, ID3_FRAME_COMMENT, 0);
		if(frame) {
			__android_log_print(ANDROID_LOG_VERBOSE, "ID3Tag", "COMMENT %d fields", frame->nfields);
			if(frame->nfields >= 4)
				title = id3_field_getfullstring(&frame->fields[3]);
		}
		break;
	}

	if(frame) {

		__android_log_print(ANDROID_LOG_VERBOSE, "ID3Tag", "frame %p %d", frame, what);

		if(title == NULL)
			title = id3_field_getstrings(&frame->fields[1], 0);
		if(title) {
			__android_log_print(ANDROID_LOG_VERBOSE, "ID3Tag", "title %p", title);
			id3_utf8_t *titleu8 = id3_ucs4_utf8duplicate(title);
			jstring j = env->NewStringUTF((const char *)titleu8);
			return j;
		}
	}

	return NULL;

}

JNIEXPORT jint JNICALL Java_com_ssb_droidsound_utils_ID3Tag_getIntInfo(JNIEnv *env, jobject obj, jint what)
{
	struct id3_file *id3file = (struct id3_file*)env->GetLongField(obj, refField);
	return 0;
}