package com.angelhack.denchan

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.vision.v1.Vision
import com.google.api.services.vision.v1.VisionRequest
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.services.vision.v1.model.*
import com.linecorp.bot.client.LineMessagingClient
import com.linecorp.bot.model.event.Event
import com.linecorp.bot.model.event.MessageEvent
import com.linecorp.bot.model.event.message.ImageMessageContent
import com.linecorp.bot.model.event.message.TextMessageContent
import com.linecorp.bot.model.message.TextMessage
import com.linecorp.bot.spring.boot.annotation.EventMapping
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import javax.imageio.ImageIO

@SpringBootApplication
@LineMessageHandler
class DenchanApplication @Autowired constructor(val lineMessagingClient: LineMessagingClient){
    @Value("\${cloudVisionServerApi.key}")
    private val VISION_API_KEY: String? = null

    val ANDROID_CERT_HEADER = "X-Android-Cert"
    val ANDROID_PACKAGE_HEADER = "X-Android-Package"
    val MAX_LABEL_RESULTS = 10

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<DenchanApplication>(*args)
        }
    }

    @EventMapping
    @Throws(Exception::class)
    fun handleTextMessageEvent(event: MessageEvent<TextMessageContent>): TextMessage {
        println("event: $event")
        return TextMessage(event.message.text)
    }

    @EventMapping
    fun handleDefaultMessageEvent(event: Event) {
        System.out.print(event)
    }

    @EventMapping
    fun handleImageMessageEvent(event: MessageEvent<ImageMessageContent>): TextMessage {
        val stream = lineMessagingClient.getMessageContent(event.message.id).get().stream
        val image = ImageIO.read(stream)
        val result = prepareAnnotationRequest(image).execute()
        return TextMessage(convertResponseToString(result))
    }

    private fun prepareAnnotationRequest(bitmap: BufferedImage): Vision.Images.Annotate {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        val requestInitializer = object : VisionRequestInitializer(VISION_API_KEY) {
            /**
             * We override this so we can inject important identifying fields into the HTTP
             * headers. This enables use of a restricted cloud platform API key.
             */
            @Throws(IOException::class)
            override fun initializeVisionRequest(visionRequest: VisionRequest<*>?) {
                super.initializeVisionRequest(visionRequest)

                val packageName = "com.angelhack.denchan"

                visionRequest?.requestHeaders?.set(ANDROID_PACKAGE_HEADER, packageName)

//                val sig = PackageManagerUtils.getSignature(getPackageManager(), packageName)
//
//                visionRequest.requestHeaders.set(ANDROID_CERT_HEADER, sig)
            }
        }

        val builder = Vision.Builder(httpTransport, jsonFactory, null)
        builder.setVisionRequestInitializer(requestInitializer)

        val vision = builder.build()

        val batchAnnotateImagesRequest = BatchAnnotateImagesRequest()

        batchAnnotateImagesRequest.requests = object : ArrayList<AnnotateImageRequest>() {
            init {
                val annotateImageRequest = AnnotateImageRequest()

                // Add the image
                val base64EncodedImage = Image()
                // Convert the bitmap to a JPEG
                // Just in case it's a format that Android understands but Cloud Vision
                val byteArrayOutputStream = ByteArrayOutputStream()

                ImageIO.write(bitmap, "jpg", byteArrayOutputStream)

                val imageBytes = byteArrayOutputStream.toByteArray()

                // Base64 encode the JPEG
                base64EncodedImage.encodeContent(imageBytes)
                annotateImageRequest.image = base64EncodedImage

                // add the features we want
                annotateImageRequest.features = object : ArrayList<Feature>() {
                    init {
                        val labelDetection = Feature()
                        labelDetection.type = "FACE_DETECTION"
                        labelDetection.maxResults = 1
                        add(labelDetection)
                    }
                }

                // Add the list of one thing to the request
                add(annotateImageRequest)
            }
        }

        val annotateRequest = vision.images().annotate(batchAnnotateImagesRequest)
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotateRequest.disableGZipContent = true

        return annotateRequest
    }

    fun convertResponseToString(response: BatchAnnotateImagesResponse): String {
        var message = "I found these facial expressions:\n\n";
        val labels = response.getResponses().get(0).getFaceAnnotations();

        if (labels != null) {
            labels.forEach { label ->
                message += String.format("Joy：%s", label.getJoyLikelihood());
                message += "\n";
                message += String.format("Sorrow：%s", label.getSorrowLikelihood());
                message += "\n";
                message += String.format("Anger：%s", label.getAngerLikelihood());
                message += "\n";
                message += String.format("Surprise：%s", label.getSurpriseLikelihood());
            }
        } else {
            message += "nothing";
        }

        return message;
    }
}

