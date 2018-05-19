package com.angelhack.denchan

import com.linecorp.bot.model.event.Event
import com.linecorp.bot.model.event.MessageEvent
import com.linecorp.bot.model.event.message.TextMessageContent
import com.linecorp.bot.model.message.TextMessage
import com.linecorp.bot.spring.boot.annotation.EventMapping
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@LineMessageHandler
class DenchanApplication {

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
}

