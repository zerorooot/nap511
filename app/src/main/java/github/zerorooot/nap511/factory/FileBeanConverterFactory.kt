package github.zerorooot.nap511.factory

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.bean.FileBean
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.Type


class FileBeanConverterFactory(private var gson: Gson) : Converter.Factory() {
    companion object {
        fun create(): Converter.Factory {
            return create(Gson())
        }

        private fun create(gson: Gson?): FileBeanConverterFactory {
            if (gson == null) throw NullPointerException("gson == null")
            return FileBeanConverterFactory(gson)
        }
    }


    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *> {
//        val adapter = gson.getAdapter(TypeToken.get(type))
        return MyResponseConverter<Any?>(gson, type)
    }

//    override fun requestBodyConverter(
//        type: Type,
//        parameterAnnotations: Array<out Annotation>,
//        methodAnnotations: Array<out Annotation>,
//        retrofit: Retrofit
//    ): Converter<*, RequestBody> {
//        return MyRequestConverter<Any?>(gson, type)
//    }
//
//    class MyRequestConverter<T>(
//        private val gson: Gson,
//        private val type: Type
//    ) : Converter<T, RequestBody>{
//        override fun convert(value: T): RequestBody? {
//            if (value is RenameBean) {
//                return value.getRequestBody()
//            }
//            return null
//        }
//    }

    class MyResponseConverter<T>(
        private val gson: Gson,
        private val type: Type
    ) :
        Converter<ResponseBody, T> {
        @Throws(IOException::class)
        override fun convert(value: ResponseBody): T? {
            val valueStream = value.charStream()
            val json = JsonParser().parse(valueStream.readText()).asJsonObject
            Log.d("nap511 json", json.toString())
            println(json.toString())
            return try {
                if (json.has("cost_time_1")) {
                    val data = json.getAsJsonArray("data")
                    val collectionType = object : TypeToken<List<FileBean>>() {}.type
                    gson.fromJson(data, collectionType) as T
                } else {
                    gson.fromJson(json, type)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                gson.fromJson(json, type)
            }

        }


    }
}


