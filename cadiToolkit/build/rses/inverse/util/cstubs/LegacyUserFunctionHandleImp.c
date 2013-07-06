#include "LegacyUserFunctionHandle.h"
#include <stdlib.h>
#include <stdio.h>

extern void initialize_(char* path, int* pathlenptr, int* junk);
extern void user_init_(int* nd, float* ranges, float* scales);
extern void forward_(int* nd, float* model, float* misfit);




/*
 * A wrapper function to call the users initialize_() function.
 */
JNIEXPORT void JNICALL Java_rses_inverse_util_LegacyUserFunctionHandle_initialize
  (JNIEnv* env, jclass clazz, jcharArray charArr, jint pathlen)
{
	jchar* jpathchars;
	char* cpathchars;
	int len;
	int i;
	fprintf(stderr, "JNIstub: start of initialize\n"); 

	jpathchars = (*env)->GetCharArrayElements(env,charArr,NULL);
	len = (int) pathlen;
	cpathchars = malloc(sizeof(char)*(len+1));
	for(i=0; i < len; i++)
		cpathchars[i] = (char) jpathchars[i];
	cpathchars[len] = '\0';
	fprintf(stderr, "JNIstub: about to call user initialize... path is %s\n", cpathchars); 
		
	initialize_(cpathchars, &len, &len);
	(*env)->ReleaseCharArrayElements(env, charArr, jpathchars, 0);

	/* NB: we do NOT free the char array, as the user's code
	 * may want to hang on to a reference to it */
}


/* A wrapper function to get to native code from Java code.
 *
 */
JNIEXPORT jint JNICALL Java_rses_inverse_util_LegacyUserFunctionHandle_userinit
(JNIEnv * env, jclass clazz, jobjectArray boundArr, jfloatArray scaleArr)
{
	int nd, maxdim, i;
	float* bounds = NULL;
	jfloat* jscales = NULL;
	float* scales = NULL;
	jfloat* tmpfptr = NULL;
	jobject obj;

	fprintf(stderr, "JNIstub: start of userinit\n"); 
	maxdim = (*env)->GetArrayLength(env,scaleArr); 

	fprintf(stderr, "JNIstub: got max dimension -- %d\n", maxdim); 
	
	jscales = (*env)->GetFloatArrayElements(env,scaleArr,NULL);

	fprintf(stderr, "JNIstub: got scales\n"); 
	
	/* this is meant to be a 2 dimensional array of floats
	 * but for the time being we just treat it as a
	 * contiguous block of memmory
	 */
	bounds = (float*) malloc(sizeof(float)*maxdim*2);
	if(bounds == NULL)
		(*env)->FatalError(env, "JNIstub: Couldnt allocate memory -- aborting\n");

	fprintf(stderr, "JNIstub: about to call user version of user_init\n");
	
	/* we need to go through the hassle of creating a
	 * float array for scales, and passing that to 
	 * user_init_, after which we copy the results 
	 * back to jscales.
	 *
	 * Actually this isn't always required (if jfloat and float
	 * are the same data type then its not necessary), but
	 * since this call to user_init only happens once we
	 * just do it anyway, regardless. 
	 */
	scales = (float*) malloc(sizeof(float)*maxdim);
	if(scales == NULL)
		(*env)->FatalError(env, "JNIstub: Couldnt allocate memory -- aborting\n");			
			
	/* call user code */
	user_init_(&nd, bounds, scales);

	/* copy the results from the float array to the jfloat array */
	for(i = 0; i < maxdim; i++) 
		jscales[i] = (jfloat) scales[i];
	
	free(scales);
	
	
	/* OK, if we get to here then we have called user_init_
	 * and the results of that call are stored in 
	 * int nd -- number of parameters
	 * float bounds -- bounds on parameter space
	 * jfloat jscales -- scale factors for parameters
	 */
	
	fprintf(stderr, "JNIstub: got back from call to user code\n");
	
	/* Now lets copy the result back to the calling Java code */

	/* first, we copy back the bounds information from the 
	 * bounds array */
	for(i = 0; i < maxdim; i++)
	{
		obj = (*env)->GetObjectArrayElement(env, boundArr, i);
		tmpfptr = (*env)->GetFloatArrayElements(env,obj,NULL);
		tmpfptr[0] = (jfloat) bounds[i*2];
		tmpfptr[1] = (jfloat) bounds[i*2+1];

		/* copy the changes back to the original java array */
		(*env)->ReleaseFloatArrayElements(env, obj, tmpfptr, 0);
	}
	
	
	fprintf(stderr, "JNIstub: finished fiddling with boundsArray...\n");
	
	free(bounds); /* free the memory, we dont need it anymore */

	/* release the scale array and copy it back to the original */
	(*env)->ReleaseFloatArrayElements(env, scaleArr, jscales, 0);
		

	fprintf(stderr, "JNIstub: return from user_init, nd is %d\n", nd);
	
	return nd;
}




/* A wrapper function to get to native code from Java */
JNIEXPORT jfloat JNICALL Java_rses_inverse_util_LegacyUserFunctionHandle_forward
(JNIEnv* env, jclass clazz, jint numDimensions, jfloatArray model)
{
	float misfit;
	int nd = numDimensions;
	jfloat* modeljfloat = NULL;
	float* modelfloat = NULL;

	/* fprintf(stderr, "jumped from java into glue code for forward\n"); */
	
	/* Not initialized yet. Somehow user_init has not been called yet */
	if(nd == 0)
		(*env)->FatalError(env, "number of dimensions not initialized prior to call to forward().\nAborting\n");
	
	/* If this code is called with a negative value for nd it means
	 * we only want the prior. The convention is that if forward_ is
	 * called with a negative value for nd, then -nd is the size of 
	 * the model array, and the prior for that model, NOT the misfit, 
	 * should be returned.
	 */
	 
	if(sizeof(jfloat) != sizeof(float))
		(*env)->FatalError(env, "jfloat and float are not the same size!\n This may cause programs to crash..\nAborting\n");

	/* get the array of jfloats */
	modeljfloat = (*env)->GetFloatArrayElements(env,model,NULL);
	modelfloat = modeljfloat;
	
	/* fprintf(stderr, "OK, about to jump to actual users code for forward()\n"); */

	/* Call the users implementation of forward */
	forward_(&nd, modelfloat ,&misfit);

	/* fprintf(stderr, "Got back from user's forward() implementation\n"); */	

	/* release the floats, and don't copy back changes */
	(*env)->ReleaseFloatArrayElements(env, model, modeljfloat, JNI_ABORT);
	
	/* fprintf(stderr, "got to end of glue code forward(), returning result\n"); */
	return misfit;
}




