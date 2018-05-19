package com.angelhack.denchan

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.vision.v1.Vision
import com.google.api.services.vision.v1.VisionRequest
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.services.vision.v1.model.*
import com.linecorp.bot.client.LineMessagingClient
import com.linecorp.bot.model.ReplyMessage
import com.linecorp.bot.model.action.Action
import com.linecorp.bot.model.action.MessageAction
import com.linecorp.bot.model.event.Event
import com.linecorp.bot.model.event.MessageEvent
import com.linecorp.bot.model.event.message.ImageMessageContent
import com.linecorp.bot.model.event.message.TextMessageContent
import com.linecorp.bot.model.message.ImageMessage
import com.linecorp.bot.model.message.Message
import com.linecorp.bot.model.message.TemplateMessage
import com.linecorp.bot.model.message.TextMessage
import com.linecorp.bot.model.message.template.ButtonsTemplate
import com.linecorp.bot.model.message.template.ConfirmTemplate
import com.linecorp.bot.model.message.template.Template
import com.linecorp.bot.spring.boot.annotation.EventMapping
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.awt.Button
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import javax.imageio.ImageIO

@SpringBootApplication
@LineMessageHandler
class DenchanApplication @Autowired constructor(val lineMessagingClient: LineMessagingClient) {
    @Value("\${cloudVisionServerApi.key}")
    private val VISION_API_KEY: String? = null

    val ANDROID_PACKAGE_HEADER = "X-Android-Package"

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<DenchanApplication>(*args)
        }
    }

    @EventMapping
    @Throws(Exception::class)
    fun handleTextMessageEvent(event: MessageEvent<TextMessageContent>) {
        println("event: $event")
        val listOfMessages = mutableListOf<Message>()

        if (event.message.text == "今日") {
            listOfMessages.add(
                    TextMessage("おお。それはもう、頑張ってね！！応援しているよ。")
            )
        } else if (event.message.text == "3日以内") {
            listOfMessages.addAll(listOf(
                    TextMessage("そうなんだ！それならちょうどいい方法があるんだ！"),
                    TextMessage("ちょっと待ってね。。。"),

                    TextMessage("https://goo.gl/maps/HMm2LzL8jzN2"),

                    TextMessage("ここなんかどうかな！")
            ))
        } else if (event.message.text == "1週間以内" || event.message.text == "それ以降") {
            listOfMessages.add(
                    TextMessage("寂しい奴だなあ。")
            )
        }

        if (event.message.text == "教えて！！！") {
            listOfMessages.addAll(listOf(
                    TextMessage(
                            "ありがとう！" +
                                    "その前にもう一個質問！"),
                    TemplateMessage(
                            "。。。",
                            ButtonsTemplate(
                                    "https://storage.googleapis.com/angelhackdemo/february_valentine01.png",
                                    "予定を確認してもいいかな。",
                                    "次に大事な予定があるのはいつだろう。",
                                    listOf(
                                            MessageAction("今日！", "今日"),
                                            MessageAction("3日以内", "3日以内"),
                                            MessageAction("1週間以内", "1週間以内"),
                                            MessageAction("それ以降", "それ以降")
                                    )
                            )
                    )
            ))
        }

        if (event.message.text == "大きなお世話です。") {
            listOfMessages.add(TextMessage("虫歯になれ。"))
        }

        listOfMessages.add(TextMessage(event.message.text))

        lineMessagingClient.replyMessage(ReplyMessage(event.replyToken, listOfMessages))
    }

    @EventMapping
    fun handleDefaultMessageEvent(event: Event) {
        System.out.print(event)
    }

    @EventMapping
    fun handleImageMessageEvent(event: MessageEvent<ImageMessageContent>) {
        val stream = lineMessagingClient.getMessageContent(event.message.id).get().stream
        val image = ImageIO.read(stream)
        val result = prepareAnnotationRequest(image).execute()

        val listOfMessages = mutableListOf<Message>()

        val textDetectResult = convertResponseToSmileBoolean(result)

        if (textDetectResult) {
            listOfMessages.addAll(listOf(
                    TextMessage("みんなと比べた歯の白さは、、、"),

                    ImageMessage("https://storage.googleapis.com/angelhackdemo/dental_result.png",
                            "https://storage.googleapis.com/angelhackdemo/dental_result.png"),

                    TemplateMessage(
                            "よおし、",
                            ConfirmTemplate("もっと綺麗な歯を保つための秘訣があるんだけど、聞きたい？？",
                            MessageAction("はい。","教えて！！！"),
                            MessageAction("絶対いや。", "大きなお世話です。")))
            ))

        } else {
            listOfMessages.addAll(listOf(
                    TextMessage("うーん、、、"),
                    ImageMessage("https://storage.googleapis.com/angelhackdemo/character_hatena.png",
                            "https://storage.googleapis.com/angelhackdemo/character_hatena.png"),

                    TextMessage("もっと歯をよく見せて！！")
            ))
        }

        val reply = ReplyMessage(event.replyToken, listOfMessages)

        lineMessagingClient.replyMessage(reply)
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

    fun convertResponseToSmileBoolean(response: BatchAnnotateImagesResponse): Boolean {
        var message = "I found these facial expressions:\n\n";
        val labels = response.getResponses().get(0).getFaceAnnotations();

//        if (labels != null) {
//            labels.forEach { label ->
//                message += String.format("Joy：%s", label.getJoyLikelihood());
//                message += "\n";
//                message += String.format("Sorrow：%s", label.getSorrowLikelihood());
//                message += "\n";
//                message += String.format("Anger：%s", label.getAngerLikelihood());
//                message += "\n";
//                message += String.format("Surprise：%s", label.getSurpriseLikelihood());
//            }
//        } else {
//            message += "nothing";
//        }
        if (labels == null) return false

        return labels.any { it.joyLikelihood == "VERY_LIKELY" || it.joyLikelihood == "LIKELY" }
    }
}

