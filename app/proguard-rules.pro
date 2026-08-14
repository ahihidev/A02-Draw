# App-specific R8 rules. Retrofit, OkHttp, Room, Gson and Hilt publish consumer rules.
# Keep the metadata used by Retrofit/Gson when R8 performs full-mode optimization.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
