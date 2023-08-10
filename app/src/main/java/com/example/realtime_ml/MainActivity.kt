package com.example.realtime_ml

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.realtime_ml.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class MainActivity : AppCompatActivity() {
    var colors = listOf<Int>(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    val paint=Paint()
    lateinit var labels: List<String>
    lateinit var textureView:TextureView
    lateinit var img:ImageView
    lateinit var btn: Button
    lateinit var bitmap:Bitmap
     lateinit var imgProcesser:ImageProcessor
    lateinit var CameraManager:CameraManager
    lateinit var handler:Handler
    lateinit var cameraDevice:CameraDevice
    lateinit var model:SsdMobilenetV11Metadata1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermission()
        labels = FileUtil.loadLabels(this, "labels.txt")
         model = SsdMobilenetV11Metadata1.newInstance(this)
        paint.setColor(Color.BLUE)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5.0f
//        paint.textSize = paint.textSize*3

        Log.d("labels", labels.toString())

        val intent = Intent()
        intent.setAction(Intent.ACTION_GET_CONTENT)
        intent.setType("image/*")

        btn = findViewById(R.id.btn)
       val imgProcesser=ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()


        btn.setOnClickListener {
            startActivityForResult(intent, 101)
        }

        val handlerThread=HandlerThread("videoThread")
        handlerThread.start()
        handler= Handler(handlerThread.looper)
        img=findViewById(R.id.img)
        textureView=findViewById(R.id.texure)

        textureView.surfaceTextureListener=object :TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()

            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return  false

            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap=textureView.bitmap!!


                var image = TensorImage.fromBitmap(bitmap)
                image=imgProcesser.process(image)



                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

// Releases model resources if no longer used.
                var mutable=bitmap.copy(Bitmap.Config.ARGB_8888,true)
                var canvas=Canvas(mutable)
                var h = mutable.height
                var w = mutable.width


                paint.textSize = h/15f
                paint.strokeWidth = h/85f
                scores.forEachIndexed { index, fl ->
                    if(fl > 0.5){
                        var x = index
                        x *= 4
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(locations.get(x+1)*w, locations.get(x)*h, locations.get(x+3)*w, locations.get(x+2)*h), paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labels[classes.get(index).toInt()] + " " + fl.toString(), locations.get(x+1)*w, locations.get(x)*h, paint)
                    }
                }

                img.setImageBitmap(mutable)



            }

        }
        CameraManager=getSystemService(Context.CAMERA_SERVICE) as CameraManager

    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 101){
            var uri = data?.data
            bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            getPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA),101)
        }
    }
    @SuppressLint("MissingPermission")
    fun openCamera(){
        CameraManager.openCamera(CameraManager.cameraIdList[0],object :CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice=p0
                var surfaceTexture=textureView.surfaceTexture
                var surface=Surface(surfaceTexture)
                var captureRequest=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)
                cameraDevice.createCaptureSession(listOf(surface),object :CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(),null,null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                        TODO("Not yet implemented")
                    }
                }
                    ,handler)
            }

            override fun onDisconnected(p0: CameraDevice) {
                TODO("Not yet implemented")
            }

            override fun onError(p0: CameraDevice, p1: Int) {
                TODO("Not yet implemented")
            }
        },
            handler)

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0]!=PackageManager.PERMISSION_GRANTED)
            getPermission()
    }
}