package com.example.nbar

import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.graphics.Color
import android.opengl.GLU
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.core.Config.PlaneFindingMode
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.*
import com.google.ar.sceneform.lullmodel.AxisSystem
import com.google.ar.sceneform.math.Matrix
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Quaternion.multiply
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.sceneform_animation.y
import com.google.sceneform_animation.z
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), Scene.OnUpdateListener {
    private lateinit var ar_fragment: ArFragment

    private val placedAnchors = ArrayList<Anchor>()
    private val placedAnchorNodes = ArrayList<AnchorNode>()
    private val tappedPoints = kotlin.collections.ArrayList<FloatArray>()
    private var cubeRenderable: ModelRenderable? = null
    private lateinit var clearButton: Button
    private lateinit var session: Session
    private lateinit var distanceText: TextView
    private lateinit var coordinatesTextView: TextView
    private lateinit var planeDetected: TextView
    private var hasDetectedPlane = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        clearButton = findViewById(R.id.clearScene)
        distanceText = findViewById(R.id.distanceText)
        coordinatesTextView = findViewById(R.id.coordinates)
        planeDetected = findViewById(R.id.planeDetectionTextview)
        ar_fragment = supportFragmentManager.findFragmentById(R.id.fragment) as ArFragment
//         session = Session(ar_fragment.context)
//        val config = Config(session)
//        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
//        config.updateMode=Config.UpdateMode.LATEST_CAMERA_IMAGE
//        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
//            config.depthMode = Config.DepthMode.AUTOMATIC
//        }
//        session.configure(config)
//        ar_fragment.arSceneView.setupSession(session)
        initAnchorView()
        ar_fragment.arSceneView.scene.addOnUpdateListener {
            if (!hasDetectedPlane) {
                val frame: Frame = ar_fragment.arSceneView.arFrame!!
                for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                    //  if (plane.trackingState === TrackingState.TRACKING && plane.type === Plane.Type.VERTICAL) {
                    // Create an anchor on the vertical plane
                    val anchor = plane.createAnchor(plane.centerPose)
                    // Do something with the anchor, for example, attach a model to it
                    hasDetectedPlane = true
                    planeDetected.visibility = View.GONE
                    clearButton.visibility = View.VISIBLE
                    println("dcba 4812 : " + plane.type)
                    Toast.makeText(this,"detected plane is : "+plane.type.toString(),Toast.LENGTH_SHORT).show()
                    val config: Config? = ar_fragment.getArSceneView().getSession()?.getConfig()
                    config?.setPlaneFindingMode(PlaneFindingMode.VERTICAL)
                    ar_fragment.getArSceneView().getSession()?.configure(config)
                    break
                    //}
                }
            }

        }

        ar_fragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            println("dcba 4812 : " + (hitResult.trackable as Plane).type)
           tappedPoints.add(hitResult.hitPose.transformPoint(floatArrayOf(0f, 0f, 0f)))
          addPointsToPlane(hitResult, plane, motionEvent)

//            val frame = ar_fragment.arSceneView.arFrame
//            val cameraPose = frame?.camera?.pose
//
//// Perform a hit test at the center of the screen
//            val hits = frame?.hitTest(ar_fragment.arSceneView.width / 2f,  ar_fragment.arSceneView.height / 2f)
//
//// Get the hit pose at the center of the screen
//            val hitPose = hits?.get(0)?.hitPose
//
//// Calculate the focus point by moving 1 meter in front of the hit pose
//            val forwardVector = hitPose?.zAxis ?: floatArrayOf(0.0f, 0.0f, -1.0f, 0.0f)
//            val focusPoint = hitPose?.compose(Pose.makeTranslation(0.0f, 0.0f, -1.0f))?.extractTranslation()
//            cameraPose?.transformPoint(forwardVector, 0, focusPoint?.translation, 0)
//// Create a new anchor at the focus point
//            val anchor = ar_fragment.arSceneView.session?.createAnchor(Pose(cameraPose?.translation, floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)))
//            addPointsToPlane(anchor!!)

        }

        clearButton.setOnClickListener {
            clearAllAnchors()
        }
        println("dcba 4812 :  ar core is available " + isARCoreSupportedAndUpToDate())
    }

    fun isARCoreSupportedAndUpToDate(): Boolean {
        return when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD, ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                try {
                    // Request ARCore installation or update if needed.
                    when (ArCoreApk.getInstance().requestInstall(this, true)) {
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            Log.i(TAG, "ARCore installation requested.")
                            false
                        }
                        ArCoreApk.InstallStatus.INSTALLED -> true
                    }
                } catch (e: UnavailableException) {
                    Log.e(TAG, "ARCore not installed", e)
                    false
                }
            }

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ->
                // This device is not supported for AR.
                false

            ArCoreApk.Availability.UNKNOWN_CHECKING -> {
                false
                // ARCore is checking the availability with a remote query.
                // This function should be called again after waiting 200 ms to determine the query result.
            }
            ArCoreApk.Availability.UNKNOWN_ERROR, ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                false
                // There was an error checking for AR availability. This may be due to the device being offline.
                // Handle the error appropriately.
            }
        }
    }


    private fun drawLine(node1: AnchorNode, node2: AnchorNode) {
        //Draw a line between two AnchorNodes
        val point1: Vector3
        val point2: Vector3
        point1 = node1.worldPosition
        point2 = node2.worldPosition


        //First, find the vector extending between the two points and define a look rotation
        //in terms of this Vector.
        val difference = Vector3.subtract(point1, point2)
        val directionFromTopToBottom = difference.normalized()
        val dx = getDistanceIncm(Math.abs(point1.x - point2.x))
        val dy = getDistanceIncm(Math.abs(point1.y - point2.y))
        val dz = getDistanceIncm(Math.abs(point1.z - point2.z))
        val lcm = getDistanceIncm(difference.length())
        coordinatesTextView.setText("dx= $dx   dy=$dy   dz=$dz $lcm")
        coordinatesTextView.visibility = View.VISIBLE
        val rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
        MaterialFactory.makeOpaqueWithColor(applicationContext, Color(100f, 100f, 100f))
            .thenAccept { material: Material? ->
                val model = ShapeFactory.makeCube(
                    Vector3(.005f, .005f, difference.length()),
                    Vector3.zero(), material
                )
                /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
           the midpoint between the given points . */
                val lineAnchor = node2.anchor
                nodeForLine?.let {
                    ar_fragment.arSceneView.scene.removeChild(it)
                    it.setParent(null)
                }
                nodeForLine = Node()
                nodeForLine?.let {
                    it.setParent(ar_fragment.arSceneView.scene)
                    it.setRenderable(model)
                    it.setWorldPosition(Vector3.add(point1, point2).scaled(.5f))
                    it.setWorldRotation(rotationFromAToB)
                }
            }
    }

    private var nodeForLine: Node? = null


    private var planeFindingMode: Config.PlaneFindingMode = Config.PlaneFindingMode.VERTICAL
    private fun clearAllAnchors() {
        nodeForLine?.let {
            ar_fragment.arSceneView.scene.removeChild(it)
            it.setParent(null)
        }
        distanceText.visibility = View.GONE
        coordinatesTextView.visibility = View.GONE
        placedAnchors.clear()
        for (anchorNode in placedAnchorNodes) {
            ar_fragment.arSceneView.scene.removeChild(anchorNode)
            anchorNode.isEnabled = false
            anchorNode.anchor!!.detach()
            anchorNode.setParent(null)
        }
        placedAnchorNodes.clear()
        tappedPoints.clear()
        val config: Config? = ar_fragment.getArSceneView().getSession()?.getConfig()
        if (planeFindingMode == PlaneFindingMode.VERTICAL) {
            config?.setPlaneFindingMode(PlaneFindingMode.HORIZONTAL)
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        } else {
            config?.setPlaneFindingMode(PlaneFindingMode.VERTICAL)
            planeFindingMode = Config.PlaneFindingMode.VERTICAL
        }
        Toast.makeText(this,planeFindingMode.toString(),Toast.LENGTH_SHORT).show()
        val arSceneView=findViewById<ArSceneView>(com.google.ar.sceneform.ux.R.id.sceneform_ar_scene_view)
        ar_fragment.getArSceneView().getSession()?.configure(config)
        arSceneView.session?.resume()
    }

    private fun initAnchorView() {
        MaterialFactory.makeTransparentWithColor(
            this,
            Color(Color.RED)
        )
            .thenAccept { material: Material? ->
                cubeRenderable = ShapeFactory.makeSphere(
                    0.005f,
                    Vector3.zero(),
                    material
                )
                cubeRenderable!!.setShadowCaster(false)
                cubeRenderable!!.setShadowReceiver(false)
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
    }

    private fun addPointsToPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if(placedAnchorNodes.size>=2){
            clearAllAnchors()
        }
        placeAnchor(hitResult, cubeRenderable!!, plane, motionEvent)
    }

    private fun addPointsToPlane(anchor: Anchor) {
        if(placedAnchorNodes.size>=2){
            clearAllAnchors()
        }
            placeAnchor(anchor, cubeRenderable!!)
    }

    private fun placeAnchor(
        hitResult: HitResult,
        renderable: Renderable, plane: Plane, motionEvent: MotionEvent
    ) {
        var anchor = hitResult.createAnchor()
        if (plane.type == Plane.Type.VERTICAL) {
            var newPose =
                Pose.makeTranslation(anchor.pose.tx(), anchor.pose.ty(), plane.centerPose.tz())
            if (placedAnchorNodes.size == 1) {
                newPose =
                    Pose.makeTranslation(
                        anchor.pose.tx(),
                        anchor.pose.ty(),
                        placedAnchorNodes[0].anchor?.pose?.tz() ?: 0f
                    )
            }
            anchor = ar_fragment.arSceneView.session?.createAnchor(newPose)
        } else if (plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING || plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
            var newPose =
                Pose.makeTranslation(anchor.pose.tx(), plane.centerPose.ty(), anchor.pose.tz())
            if (placedAnchorNodes.size == 1) {
                newPose =
                    Pose.makeTranslation(
                        anchor.pose.tx(),
                        placedAnchorNodes[0].anchor?.pose?.ty() ?: 0f,
                        anchor.pose.tz()
                    )
            }
            anchor = ar_fragment.arSceneView.session?.createAnchor(newPose)
        }
        placedAnchors.add(anchor)
        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(ar_fragment.arSceneView.scene)
        }
        placedAnchorNodes.add(anchorNode)

        val node = TransformableNode(ar_fragment.transformationSystem)
            .apply {
                this.rotationController.isEnabled = false
                this.scaleController.isEnabled = false
                this.translationController.isEnabled = true
                this.renderable = renderable
                setParent(anchorNode)
            }
        ar_fragment.arSceneView.scene.addOnUpdateListener(this)
        ar_fragment.arSceneView.scene.addChild(anchorNode)
        node.select()
    }

    private fun placeAnchor(
        anchor: Anchor,
        renderable: Renderable
    ) {
        placedAnchors.add(anchor)
        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(ar_fragment.arSceneView.scene)
        }
        placedAnchorNodes.add(anchorNode)

        val node = TransformableNode(ar_fragment.transformationSystem)
            .apply {
                this.rotationController.isEnabled = false
                this.scaleController.isEnabled = false
                this.translationController.isEnabled = true
                this.renderable = renderable
                setParent(anchorNode)
            }
        ar_fragment.arSceneView.scene.addOnUpdateListener(this)
        ar_fragment.arSceneView.scene.addChild(anchorNode)
        node.select()
    }

    private fun projectScreenPointOntoPlane(
        screenX: Float, screenY: Float,
        planeX: Float, planeY: Float, planeZ: Float,
        planeNormalX: Float, planeNormalY: Float, planeNormalZ: Float
    ): FloatArray {
        val ray = ar_fragment.arSceneView.scene.camera.screenPointToRay(screenX, screenY)
        val rayDirection = floatArrayOf(ray.direction.x, ray.direction.y, ray.direction.z)
        val rayOrigin = floatArrayOf(ray.origin.x, ray.origin.y, ray.origin.z)

        // Calculate the distance along the ray to the plane
        val t = ((planeX - rayOrigin[0]) * planeNormalX +
                (planeY - rayOrigin[1]) * planeNormalY +
                (planeZ - rayOrigin[2]) * planeNormalZ) /
                (rayDirection[0] * planeNormalX +
                        rayDirection[1] * planeNormalY +
                        rayDirection[2] * planeNormalZ)

        // Calculate the intersection point
        val intersectionX = rayOrigin[0] + rayDirection[0] * t
        val intersectionY = rayOrigin[1] + rayDirection[1] * t
        val intersectionZ = rayOrigin[2] + rayDirection[2] * t

        return floatArrayOf(intersectionX, intersectionY, intersectionZ)
    }

    override fun onUpdate(p0: FrameTime?) {
        measureDistanceOf2Points()
    }

    private fun measureDistanceOf2Points() {
        if (placedAnchorNodes.size == 2) {
            val distanceMeter = getDistanceBetweenTwoPoints(
                placedAnchorNodes[0].worldPosition,
                placedAnchorNodes[1].worldPosition
            )
            println(
                "coordinates : " + placedAnchorNodes[1].worldPosition.x + "  " + placedAnchorNodes[1].worldPosition.y + "  " +
                        placedAnchorNodes[1].worldPosition.z
            )
            var anotherdist = 0f.toDouble()
            if (tappedPoints.size == 2) {
                val point1 = tappedPoints[0]
                val point2 = tappedPoints[1]
                anotherdist = Math.sqrt(
                    Math.pow(point2[0].toDouble() - point1[0].toDouble(), 2.0) +
                            Math.pow(point2[1].toDouble() - point1[1].toDouble(), 2.0)
                )
            }
            nodeForLine?.let {
                ar_fragment.arSceneView.scene.removeChild(it)
                it.setParent(null)
                nodeForLine = null
            }
            drawLine(placedAnchorNodes[0], placedAnchorNodes[1])
            measureDistanceOf2Points(distanceMeter, anotherdist)
        }
    }

    private fun getDistanceBetweenTwoPoints(objectPose0: Vector3, objectPose1: Vector3): Float =
        sqrt(
            (objectPose0.x - objectPose1.x).pow(2) + (objectPose0.y - objectPose1.y).pow(2) + (objectPose0.z - objectPose1.z).pow(
                2
            )
        )

    private fun measureDistanceOf2Points(distanceMeter: Float, anotherDist: Double) {
        val distanceTextCM = getDistanceIncm(distanceMeter)
        if (placedAnchorNodes.size == 2) {
            distanceText.setText("Refreshed Gap : " + distanceTextCM +"  "+getDistanceIncm(anotherDist.toFloat()))
            distanceText.visibility = View.VISIBLE
        }
        //Toast.makeText(this, getDistanceIncm(anotherDist.toFloat()), Toast.LENGTH_SHORT).show()
    }

    private fun getDistanceIncm(distanceMeter: Float): String {
        val distanceCM = distanceMeter * 100
        val distanceCMFloor = "%.2f".format(distanceCM)
        return "${distanceCMFloor} cm"
    }


}