package fi.reuna.tekstitv

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import io.reactivex.Single
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.*

interface TTVService {

    @GET("ttvcontent?c=true")
    fun getPage(@Query("p") pageNumber: Int) : Single<TTVContent>

    @GET("ttvcontent?c=true")
    fun getPages(@Query("p") pageNumbers: List<Int>) : Single<TTVContent>

    @GET("ttvcontent?c=true&s=next")
    fun getNextPage(@Query("p") pageNumber: Int) : Single<TTVContent>

    @GET("ttvcontent?c=true&s=prev")
    fun getPreviousPage(@Query("p") pageNumber: Int) : Single<TTVContent>

    companion object TTVServiceProvider {

        val instance: TTVService
        private val client: OkHttpClient

        init {
            val cfg = ConfigurationProvider.cfg

            client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val newUrl = request.url().newBuilder().addQueryParameter("a", cfg.apiKey).build()
                        val newRequest = request.newBuilder().url(newUrl).build()
                        chain.proceed(newRequest)
                    }
                    .build()

            val moshi = Moshi.Builder()
                    .add(Date::class.java, Rfc3339DateJsonAdapter())
                    .build()

            val retrofit = Retrofit.Builder()
                    .baseUrl(cfg.baseUrl)
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .client(client)
                    .build()

            instance = retrofit.create(TTVService::class.java)
        }
    }
}

data class TTVContent(val status: Int,
                 val message: String?,
                 val timestamp: Date,
                 val pagesCount: Int,
                 val version: String,
                 val pages: List<TTVPage>)

data class TTVPage(val number: Int, val subpages: List<TTVSubpage>)  {

    fun getSubpage(index: Int): Page? {
        return if (index >= 0 && index < subpages.size) Page(Location(number, index), subpages[index].content) else null
    }
}

data class TTVSubpage(val number: Int, val timestamp: Date, val content: String) {

    @Transient
    val pieces: Array<Piece> = pageContentToPieces(content).toTypedArray()

}
