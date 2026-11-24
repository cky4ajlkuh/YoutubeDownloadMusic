package com.example.youtubedownloadmusic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.youtubedownloadmusic.databinding.ActivityMainBinding
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val savedUri = getSharedPreferences(YTDL_PREFS, MODE_PRIVATE)
            .getString(DOWNLOAD_TREE_URI, null)

        if (savedUri == null) {
            binding.folderInput.setText(getDefaultDownloadDir().absolutePath)
        } else {
            // Восстанавливаем выбранную папку
            val docFile = DocumentFile.fromTreeUri(this, savedUri.toUri())
            binding.folderInput.setText(docFile?.name ?: savedUri)
        }

        binding.folderInputLayout.setEndIconOnClickListener {
            folderPickerLauncher.launch(null)
        }

        initDownloader()

        binding.download.setOnClickListener {
            val url = binding.urlInput.text?.toString()?.trim().orEmpty()

            if (url.isEmpty()) {
                binding.urlInputLayout.error = "URL is empty!"
                return@setOnClickListener
            }
            if (!isYouTubeUrl(url)) {
                binding.urlInputLayout.error = "Wrong URL!"
                return@setOnClickListener
            }
            binding.urlInputLayout.error = null

            downloadAudio(url)
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val host = url.toUri().host?.lowercase() ?: return false
        return host == "youtube.com" ||
                host == "www.youtube.com" ||
                host == "m.youtube.com" ||
                host == "music.youtube.com" ||
                host == "youtu.be"
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                getSharedPreferences(YTDL_PREFS, MODE_PRIVATE)
                    .edit { putString(DOWNLOAD_TREE_URI, it.toString()) }

                val docFile = DocumentFile.fromTreeUri(this, it)
                binding.folderInput.setText(docFile?.name ?: it.toString())
            }
        }

    /** Инициализация yt-dlp и FFmpeg. Делается один раз. */
    private fun initDownloader() {
        try {
            YoutubeDL.getInstance().init(application)
            FFmpeg.getInstance().init(application)
        } catch (e: Exception) {
            Log.e(TAG, "Loader initialization error", e)
            Toast.makeText(this, "Failed to initialize loader", Toast.LENGTH_LONG).show()
            return
        }
        updateYtDlpIfNeeded()
    }

    private fun updateYtDlpIfNeeded() {
        val prefs = getSharedPreferences(YTDL_PREFS, MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_update_check", 0L)
        val dayMs = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastCheck < dayMs) {
            Log.d(TAG, "Проверка обновлений недавно выполнялась, пропускаем")
            return
        }

        lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        applicationContext,
                        YoutubeDL.UpdateChannel._STABLE
                    )
                }
                Log.i(TAG, "Обновление yt-dlp: $status")

                if (status == YoutubeDL.UpdateStatus.DONE) {
                    Toast.makeText(
                        this@MainActivity,
                        "yt-dlp обновлён. Перезапустите приложение для применения.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                prefs.edit { putLong("last_update_check", System.currentTimeMillis()) }
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось обновить yt-dlp", e)
            }
        }
    }

    private fun downloadAudio(url: String) {
        binding.download.isEnabled = false
        binding.download.text = getString(R.string.btn_initialization)
        binding.progressBar.isIndeterminate = true
        binding.progressBar.visibility = View.VISIBLE

        val savedUri = getSharedPreferences(YTDL_PREFS, MODE_PRIVATE)
            .getString(DOWNLOAD_TREE_URI, null)

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    downloadAudioTo(url, savedUri?.toUri())
                }
                val msg = if (success) "File downloaded successfully" else "Failed to save the file"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Loading error: ", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.download.isEnabled = true
                binding.download.text = getString(R.string.btn_download)
                binding.progressBar.isIndeterminate = false
                binding.progressBar.progress = 0
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Скачивает аудиодорожку с YouTube в формате MP3 и сохраняет в указанную папку.
     *
     * Если [treeUri] равен `null`, файл сохраняется в папку по умолчанию —
     * `Downloads/youtubedl-android` (см. [getDefaultDownloadDir]).
     * Если [treeUri] задан, файл сначала скачивается во временную директорию
     * приложения, а затем копируется в выбранную пользователем папку.
     *
     * @param url ссылка на видео YouTube, из которого нужно извлечь аудио.
     * @param treeUri URI дерева папки, куда сохранить файл. Если параметр не указан, то
     *        использовать папку по умолчанию.
     *
     * @return `true`, если файл успешно скачан и сохранён; `false` — если
     *         не удалось найти скачанный файл, получить доступ к папке или
     *         скопировать данные.
     */
    private fun downloadAudioTo(url: String, treeUri: Uri?): Boolean {
        // 1. Определяем, куда физически будет писать yt-dlp
        val isDefaultDir = treeUri == null
        val targetDir: File = if (isDefaultDir) {
            getDefaultDownloadDir()
        } else {
            File(cacheDir, "temp_download").apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
        }

        // 2. Готовим и выполняем запрос
        val request = YoutubeDLRequest(url).apply {
            addOption("--extract-audio")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "0")
            addOption("-o", "${targetDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--no-mtime")
        }

        var downloadStarted = false
        var convertingStarted = false

        YoutubeDL.getInstance().execute(request) { progress, _, _ ->
            runOnUiThread {
                if (!downloadStarted && progress > 0f) {
                    downloadStarted = true
                    binding.progressBar.isIndeterminate = false
                    binding.download.text = getString(R.string.btn_loading)
                }
                if (downloadStarted && !convertingStarted) {
                    binding.progressBar.progress = progress.toInt()
                    if (progress >= 100f) {
                        convertingStarted = true
                        binding.progressBar.isIndeterminate = true
                        binding.download.text = getString(R.string.btn_converting)
                    }
                }
            }
        }

        // 3. Если писали напрямую в папку по умолчанию — всё готово
        if (isDefaultDir) return true

        // 4. Иначе копируем из temp в выбранную пользователем папку
        val downloadedFile = targetDir.listFiles()?.maxByOrNull { it.lastModified() }
            ?: return false

        val docDir = DocumentFile.fromTreeUri(this, treeUri) ?: return false
        val newDoc = docDir.createFile("audio/mpeg", downloadedFile.name)
            ?: return false

        contentResolver.openOutputStream(newDoc.uri)?.use { out ->
            downloadedFile.inputStream().use { it.copyTo(out) }
        } ?: return false

        // 5. Чистим временные файлы
        downloadedFile.delete()
        targetDir.delete()
        return true
    }

    /**
     * Возвращает директорию по умолчанию для сохранения аудио —
     * `Download/youtube-download-music` (относительно корня внешнего хранилища).
     *
     * Если директория не существует, она создаётся (включая родительские папки).
     *
     * @return объект [File], указывающий на директорию по умолчанию.
     */
    private fun getDefaultDownloadDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "youtube-download-music"
        ).apply { mkdirs() }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val YTDL_PREFS = "ytdl_prefs"
        private const val DOWNLOAD_TREE_URI = "download_tree_uri"
    }
}