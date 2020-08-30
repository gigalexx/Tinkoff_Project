package com.example.tinkoff_project

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val RANDOM_POST_URL = "https://developerslife.ru/random?json=true"
private val pastPostsStack = Stack<Post>()
private val futurePostsQueue: Queue<Post> = LinkedList()
private const val PRE_POPULATION_SIZE = 10


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_next.setOnClickListener {
            showNextPost()
        }

        btn_previous.setOnClickListener {
            showPreviousPost()
        }

        showNextPost()
    }

    private fun showPreviousPost() {
        if (pastPostsStack.peek().description == tv_description.text) pastPostsStack.pop()
        if (pastPostsStack.isNotEmpty()) {
            CoroutineScope(Main).launch {
                updateUI(pastPostsStack.pop())
            }
        }
    }

    private fun getNextPost() {
        pb_loading.visibility = View.VISIBLE
        if (isInternetAvailable()) {
            showPost()
        } else {
            showErrorPlaceHolder()
        }
    }

    private fun showErrorPlaceHolder() {
        pb_loading.visibility = View.GONE
        iv_image.setImageResource(R.drawable.generic_error_placeholder)
        tv_description.text = resources.getText(R.string.error_text)
    }


    private fun showNextPost() {
        tv_description.text = ""
        if (futurePostsQueue.isEmpty()) {
            getNextPost()
            populateFuturePosts()
        } else {
            val post = futurePostsQueue.poll()
            pastPostsStack.push(post)
            updateUI(post) // UI update
        }
    }

    private fun populateFuturePosts() {
        if (!isInternetAvailable()) return
        CoroutineScope(IO).launch {
            val postBody = async { getRandomPost(OkHttpClient()) } // async request
            val sPost = toPost(postBody.await())

            Glide.with(iv_image).load(sPost.gifURL)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        futurePostsQueue.offer(sPost)
                        if (futurePostsQueue.size < PRE_POPULATION_SIZE) populateFuturePosts()
                        return false
                    }
                }).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).preload()
        }
    }

    private fun showPost() {


        val parentJob = CoroutineScope(IO).launch {

            val postBody = async { getRandomPost(OkHttpClient()) } // async request

            val sPost = toPost(postBody.await())

            if (sPost.gifURL != null) {
                pastPostsStack.push(sPost)
                updateUI(sPost)                 // UI update
            } else {
                showErrorPlaceHolder()
            }
        }

        parentJob.invokeOnCompletion { throwable ->
            if (throwable != null) {
                showErrorPlaceHolder()
                Toast.makeText(this, "Something went wrong. Please try again", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    }

    private fun toPost(postBody: String): Post {
        val post = Gson().fromJson(postBody, Post::class.java);
        val url = post.gifURL
        if (url != null) {
            if (!url.contains("https")) post.gifURL = url.replace("http", "https")
        }
        return post
    }


    private suspend fun getRandomPost(client: OkHttpClient): String {
        return suspendCoroutine { continuation ->
            val request = Request.Builder()
                .url(RANDOM_POST_URL)
                .build()

            client.run {
                newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody: String? = response.body?.string()
                        continuation.resume(responseBody!!)
                    }
                })
            }
        }
    }

    private fun updateUI(post: Post) {
        CoroutineScope(Main).launch {
            Glide.with(iv_image).load(post.gifURL).listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    updateButtonState()
                    showErrorPlaceHolder()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    pb_loading.visibility = View.GONE
                    tv_description.text = post.description
                    updateButtonState()
                    return false
                }
            }).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(iv_image)

        }
    }

    private fun updateButtonState() {
        btn_previous.isEnabled =
            !(pastPostsStack.isEmpty() || pastPostsStack.size == 1 && pastPostsStack.peek().description == tv_description.text)
    }

    private fun isInternetAvailable(): Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }


}