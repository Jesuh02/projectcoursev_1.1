package com.example.tareamov.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.VideoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class to manage video persistence
 */
class VideoManager(private val context: Context) {
    private val videoDao = AppDatabase.getDatabase(context).videoDao()
    private val videoCacheDir = File(context.cacheDir, "videos")
    private val uriPermissionManager = UriPermissionManager(context)

    init {
        // Ensure the cache directory exists
        if (!videoCacheDir.exists()) {
            videoCacheDir.mkdirs()
        }
    }    /**
     * Save a video to the local cache and update the database
     */
    suspend fun saveVideo(videoData: VideoData): VideoData = withContext(Dispatchers.IO) {
        try {
            Log.d("VideoManager", "Starting saveVideo for: ${videoData.title}")
            
            // First insert the video to get an ID if it doesn't have one
            var updatedVideoData = videoData
            val videoId = if (videoData.id == 0L) {
                val newId = videoDao.insertVideo(videoData)
                Log.d("VideoManager", "Inserted new video with ID: $newId") 
                newId
            } else {
                Log.d("VideoManager", "Updating existing video with ID: ${videoData.id}")
                videoData.id
            }

            // If we have a URI, try to save it locally
            val uriString = videoData.videoUriString
            if (!uriString.isNullOrEmpty()) {
                val uri = Uri.parse(uriString)
                Log.d("VideoManager", "Processing URI: $uri")

                // --- Skip MIUI Gallery and dummy/unsupported URIs ---
                if (uri.authority == "com.miui.gallery.provider.GalleryOpenProvider" ||
                    uri.authority == "com.miui.gallery.open" ||
                    uri.toString().contains("dummy") ||
                    uri.toString().contains("unsupported")) {
                    Log.e("VideoManager", "Cannot persist video from MIUI Gallery or dummy/unsupported URI. Please select from Files or Google Photos.")
                    return@withContext videoData.copy(localFilePath = null)
                }
                // --- END BLOCK ---

                // Always try to copy the video to local storage, regardless of provider
                try {
                    val localPath = saveVideoToLocalStorage(uri, videoId)
                    if (localPath != null) {
                        val updatedVideo = VideoData(
                            id = videoId,
                            username = videoData.username,
                            description = videoData.description,
                            title = videoData.title,
                            videoUriString = uriString,
                            localFilePath = localPath,
                            timestamp = videoData.timestamp,
                            isPaid = videoData.isPaid,
                            thumbnailUri = videoData.thumbnailUri,
                            price = videoData.price
                        )
                        videoDao.updateVideo(updatedVideo)
                        updatedVideoData = updatedVideo
                        Log.d("VideoManager", "Successfully saved video to local path: $localPath and updated database")
                    } else {
                        Log.e("VideoManager", "Failed to save video to local storage (null path)")
                        // Even if local storage fails, keep the database record
                        updatedVideoData = videoData.copy(id = videoId)
                    }
                } catch (se: SecurityException) {
                    Log.e("VideoManager", "Security exception saving video: ${se.message}")
                    // Keep the database record even if file copy fails
                    updatedVideoData = videoData.copy(id = videoId)
                } catch (e: Exception) {
                    Log.e("VideoManager", "Error saving video to local storage: ${e.message}")
                    // Keep the database record even if file copy fails
                    updatedVideoData = videoData.copy(id = videoId)
                }
            } else {
                // No URI provided, just update the database record
                updatedVideoData = videoData.copy(id = videoId)
                if (videoData.id != 0L) {
                    videoDao.updateVideo(updatedVideoData)
                }
            }

            Log.d("VideoManager", "Video save completed. Final ID: ${updatedVideoData.id}")
            return@withContext updatedVideoData
        } catch (e: Exception) {
            Log.e("VideoManager", "Error saving video", e)
            return@withContext videoData
        }    }

    private suspend fun saveVideoToLocalStorage(uri: Uri, videoId: Long): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("VideoManager", "Attempting to save video to local storage. URI: $uri, VideoID: $videoId")
            
            // Create a unique filename
            val fileName = "video_${videoId}_${System.currentTimeMillis()}.mp4"
            val destinationFile = File(videoCacheDir, fileName)
            
            Log.d("VideoManager", "Destination file: ${destinationFile.absolutePath}")

            // Always use contentResolver.openInputStream for all URIs
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val bytesCopied = input.copyTo(output)
                        Log.d("VideoManager", "Copied $bytesCopied bytes to local storage")
                    }
                } ?: throw IOException("Could not open input stream for URI: $uri")
            } catch (se: SecurityException) {
                Log.e("VideoManager", "Security exception opening URI: $uri", se)
                return@withContext null
            } catch (e: Exception) {
                Log.e("VideoManager", "Error opening input stream for URI: $uri", e)
                return@withContext null
            }

            if (destinationFile.exists() && destinationFile.length() > 0) {
                Log.d("VideoManager", "Video saved successfully to ${destinationFile.absolutePath}, size: ${destinationFile.length()} bytes")
                return@withContext destinationFile.absolutePath
            } else {
                Log.e("VideoManager", "File was created but is empty or doesn't exist: ${destinationFile.absolutePath}")
                if (destinationFile.exists()) {
                    destinationFile.delete() // Clean up empty file
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("VideoManager", "Error saving video to local storage", e)
            return@withContext null
        }
    }

    /**
     * Load all videos from the database
     */
    suspend fun getAllVideos(): List<VideoData> = withContext(Dispatchers.IO) {
        return@withContext videoDao.getAllVideos()
    }

    /**
     * Get videos by username
     */
    suspend fun getVideosByUsername(username: String): List<VideoData> = withContext(Dispatchers.IO) {
        return@withContext videoDao.getVideosByUsername(username)
    }

    /**
     * Get video by ID
     */
    suspend fun getVideoById(videoId: Long): VideoData? = withContext(Dispatchers.IO) {
        return@withContext videoDao.getVideoById(videoId)
    }

    /**
     * Check if a video file exists and is accessible
     */
    fun videoFileExists(videoData: VideoData): Boolean {
        // First, check if we have a valid local file path and if it exists
        if (!videoData.localFilePath.isNullOrEmpty()) {
            val localFile = File(videoData.localFilePath)
            if (localFile.exists() && localFile.canRead()) {
                return true
            }
        }

        // If no local path or it's invalid, check the original URI
        if (videoData.videoUriString == null) return false

        try {
            val uri = Uri.parse(videoData.videoUriString)

            // --- Skip MIUI Gallery and dummy/unsupported URIs ---
            if (uri.authority == "com.miui.gallery.provider.GalleryOpenProvider" ||
                uri.authority == "com.miui.gallery.open" ||
                uri.toString().contains("dummy") ||
                uri.toString().contains("unsupported")) {
                Log.w("VideoManager", "Cannot check existence for restricted or dummy/unsupported URI: $uri. Relying on local copy if available.")
                return false
            }
            // --- END BLOCK ---

            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = File(path)
                    return file.exists() && file.canRead()
                }
            } else if (uri.scheme == "content") {
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.close() }
                    return true
                } catch (e: SecurityException) {
                    Log.w("VideoManager", "SecurityException checking content URI: $uri - ${e.message}")
                    return false
                } catch (e: Exception) {
                    Log.e("VideoManager", "Error checking content URI: $uri", e)
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e("VideoManager", "Error checking if video file exists", e)
        }

        return false
    }

    /**
     * Get a file path from a URI
     */
    suspend fun getFilePathFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "content") {
                // Avoid querying restricted providers directly for _data column
                if (uri.authority != "com.miui.gallery.provider.GalleryOpenProvider") {
                    val cursor = context.contentResolver.query(uri, arrayOf("_data"), null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val columnIndex = it.getColumnIndex("_data")
                            if (columnIndex >= 0) {
                                val path = it.getString(columnIndex)
                                if (path != null) {
                                    // Check if the path is actually valid before returning
                                    val file = File(path)
                                    if (file.exists() && file.canRead()) {
                                        Log.d("VideoManager", "Found direct path from content URI: $path")
                                        return@withContext path
                                    } else {
                                        Log.w("VideoManager", "Direct path from content URI is invalid or inaccessible: $path")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.w("VideoManager", "Skipping direct path query for restricted provider URI: $uri")
                }

                // If we couldn't get the path from the cursor or it's a restricted provider,
                // copy the file to app's cache as a fallback
                Log.d("VideoManager", "Attempting to copy content URI to cache: $uri")
                val inputStream = context.contentResolver.openInputStream(uri) // This might still fail for some URIs
                if (inputStream != null) {
                    val fileName = "video_copy_${System.currentTimeMillis()}.mp4"
                    val cacheFile = File(videoCacheDir, fileName)

                    inputStream.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        Log.d("VideoManager", "Copied content URI to cache: ${cacheFile.absolutePath}")
                        return@withContext cacheFile.absolutePath
                    } else {
                        Log.e("VideoManager", "Failed to copy content URI to cache or file is empty: $uri")
                        cacheFile.delete() // Clean up empty file
                    }
                } else {
                    Log.e("VideoManager", "Could not open input stream for content URI: $uri")
                }
            } else if (uri.scheme == "file") {
                val path = uri.path
                if (path != null && File(path).exists()) {
                    return@withContext path
                }
            }

            return@withContext null
        } catch (se: SecurityException) {
            Log.e("VideoManager", "SecurityException getting file path from URI: $uri - ${se.message}")
            return@withContext null // Permission denied
        } catch (e: Exception) {
            Log.e("VideoManager", "Error getting file path from URI: $uri", e)
            return@withContext null
        }
    }

    /**
     * Get a valid URI for a video, trying different sources
     */
    fun getValidVideoUri(videoData: VideoData): Uri? {
        try {
            // Priority 1: Check the local file path if it exists
            if (!videoData.localFilePath.isNullOrEmpty()) {
                val file = File(videoData.localFilePath)
                if (file.exists() && file.canRead()) {
                    Log.d("VideoManager", "Using local file path URI: ${videoData.localFilePath}")
                    return Uri.fromFile(file) // Use file URI for local files
                } else {
                    Log.w("VideoManager", "Local file path exists in DB but file not found or unreadable: ${videoData.localFilePath}")
                }
            }

            // Priority 2: Try the original URI, checking permissions and accessibility
            if (!videoData.videoUriString.isNullOrEmpty()) {
                val uri = Uri.parse(videoData.videoUriString)

                // Skip direct access check for restricted providers
                if (uri.authority == "com.miui.gallery.provider.GalleryOpenProvider") {
                    Log.w("VideoManager", "Skipping direct access check for restricted provider URI: $uri")
                    // Cannot directly use this URI, rely on local copy if created.
                }
                // For other URIs, check permission and try to open
                else if (uriPermissionManager.hasPermissionForUri(uri)) {
                    try {
                        // Test if we can open the URI
                        context.contentResolver.openInputStream(uri)?.use { it.close() }
                        Log.d("VideoManager", "Using original URI with permission: $uri")
                        return uri
                    } catch (e: SecurityException) {
                        Log.w("VideoManager", "SecurityException opening original URI even with persisted permission: $uri - ${e.message}")
                    } catch (e: Exception) {
                        Log.e("VideoManager", "Cannot open original URI: $uri - ${e.message}")
                    }
                } else {
                    Log.w("VideoManager", "No permission for original URI: $uri")
                }
            }

            // If both failed, return null
            Log.w("VideoManager", "No valid/accessible URI found for video ID: ${videoData.id}")
            return null
        } catch (e: Exception) {
            Log.e("VideoManager", "Error getting valid video URI for video ID: ${videoData.id}", e)
            return null
        }
    }    /**
     * Verify if a video was successfully saved to the database
     */
    suspend fun verifyVideoSaved(videoId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val video = videoDao.getVideoById(videoId)
            val exists = video != null
            Log.d("VideoManager", "Video verification for ID $videoId: exists=$exists")
            if (exists && video != null) {
                Log.d("VideoManager", "Verified video: title='${video.title}', username='${video.username}', localPath='${video.localFilePath}'")
            }
            return@withContext exists
        } catch (e: Exception) {
            Log.e("VideoManager", "Error verifying video save for ID: $videoId", e)
            return@withContext false
        }
    }
}