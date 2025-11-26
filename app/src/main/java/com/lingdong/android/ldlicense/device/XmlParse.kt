package com.lingdong.android.ldlicense.device

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class XmlParse {
    fun parseDeviceXml(xml: String): DeviceResponse {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var cmd = ""
        var status = ""
        var stringValue = ""

        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Cmd" -> cmd = parser.nextText()
                    "Status" -> status = parser.nextText()
                    "String" -> stringValue = parser.nextText()
                }
            }
            try {
                eventType = parser.next()
            }catch (e: Exception){
                status = "XML 解析错误: ${xml}"
                break
            }
        }

        return DeviceResponse(cmd, status, stringValue)
    }
}