package com.example.tareamov.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tareamov.R
import com.example.tareamov.data.AppDatabase
import com.example.tareamov.data.entity.VideoData
import com.example.tareamov.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContentUploadFragment : Fragment() {

    private val PICK_VIDEO_REQUEST = 1
    private val PICK_IMAGE_REQUEST = 2
    private lateinit var sessionManager: SessionManager

    // --- Agrega esta bandera ---
    private var isSavingVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize SessionManager
        sessionManager = SessionManager.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_content_upload, container, false)
    }    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure loading overlay is hidden initially
        hideLoading()

        // Set up close button to navigate back
        val closeButton = view.findViewById<ImageButton>(R.id.closeButton)
        closeButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Set up video upload button
        val videoUploadOption = view.findViewById<LinearLayout>(R.id.videoUploadOption)
        videoUploadOption.setOnClickListener {
            openGalleryForVideo()
        }

        // Set up short upload button
        val shortUploadOption = view.findViewById<LinearLayout>(R.id.shortUploadOption)
        shortUploadOption.setOnClickListener {
            openGalleryForVideo()
        }

        // Set up live stream button
        val liveUploadOption = view.findViewById<LinearLayout>(R.id.liveUploadOption)
        liveUploadOption.setOnClickListener {
            // For live streaming, we would typically navigate to a camera preview
            // For now, just show a message or navigate back
            findNavController().navigateUp()
        }

        // Set up publication upload button
        val publicationUploadOption = view.findViewById<LinearLayout>(R.id.publicationUploadOption)
        publicationUploadOption.setOnClickListener {
            // Navigate to course creation screen
            findNavController().navigate(R.id.action_contentUploadFragment_to_courseCreationFragment)
        }
    }

    private fun openGalleryForVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "video/*"
        startActivityForResult(intent, PICK_VIDEO_REQUEST)
    }

    private fun openGalleryForImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val selectedVideoUri = data.data

            // --- Evita doble guardado ---
            if (isSavingVideo) return
            isSavingVideo = true

            // Mostrar indicador de carga
            showLoading()

            // Check if user is logged in
            if (sessionManager.getUsername() == null) {
                Toast.makeText(context, "Debes iniciar sesión para subir videos", Toast.LENGTH_LONG).show()
                isSavingVideo = false
                hideLoading()
                return
            }

            Log.d("ContentUploadFragment", "Selected video URI: $selectedVideoUri")
            if (selectedVideoUri != null) {
                lifecycleScope.launch {
                    try {
                        val videoManager = com.example.tareamov.util.VideoManager(requireContext())
                        val username = sessionManager.getUsername() ?: "Usuario"

                        val tempVideoData = VideoData(
                            username = username,
                            description = "",
                            title = "Mi video",
                            videoUriString = selectedVideoUri.toString()
                        )

                        val savedVideo = videoManager.saveVideo(tempVideoData)
                        Log.d("ContentUploadFragment", "Video saved with ID: ${savedVideo.id}, localPath: ${savedVideo.localFilePath}")

                        val isVerified = videoManager.verifyVideoSaved(savedVideo.id)
                        Log.d("ContentUploadFragment", "Video verification result: $isVerified")

                        if (isVerified && savedVideo.localFilePath != null) {
                            val bundle = Bundle().apply {
                                putParcelable("videoUri", Uri.fromFile(java.io.File(savedVideo.localFilePath)))
                                putLong("videoId", savedVideo.id)
                            }

                            withContext(Dispatchers.Main) {
                                hideLoading()
                                findNavController().navigate(R.id.action_contentUploadFragment_to_videoDetailsFragment, bundle)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                val errorMsg = if (!isVerified) {
                                    "Error: El video no se guardó correctamente en la base de datos"
                                } else {
                                    "No se pudo guardar el video localmente. Intenta seleccionar desde Archivos o Google Fotos."
                                }
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                hideLoading()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error al procesar el video: ${e.message}", Toast.LENGTH_LONG).show()
                            hideLoading()
                        }
                        Log.e("ContentUploadFragment", "Error processing video", e)
                    } finally {
                        isSavingVideo = false
                    }
                }
            } else {
                Log.e("ContentUploadFragment", "Selected video URI is null")
                isSavingVideo = false
                hideLoading()
            }
        }
    }

    private fun showLoading() {
        view?.findViewById<FrameLayout>(R.id.loadingOverlay)?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        view?.findViewById<FrameLayout>(R.id.loadingOverlay)?.visibility = View.GONE
    }    override fun onStop() {
        super.onStop()
        // Ensure loading overlay is hidden when fragment is stopped
        hideLoading()
        // Reset saving video flag
        isSavingVideo = false
    }
}