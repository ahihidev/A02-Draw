# Room and Retrofit ship their own consumer rules. These API DTOs are deserialized
# reflectively by Gson, so their wire-field names must remain stable in minified builds.
-keep,allowoptimization class com.a02.draw.data.remote.dto.** {
    <fields>;
}
