package com.example.compiler

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, zero-dependency Android Binary XML (AXML) generator.
 * Encodes AndroidManifest.xml into standard compiled Binary XML format (chunk 0x0003)
 * so that Android OS PackageParser and getPackageArchiveInfo() can parse and install APKs cleanly.
 */
object BinaryAxmlGenerator {

    private const val RES_XML_TYPE: Short = 0x0003
    private const val RES_STRING_POOL_TYPE: Short = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE: Short = 0x0180
    private const val RES_XML_START_NAMESPACE_TYPE: Short = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE: Short = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE: Short = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE: Short = 0x0103

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12

    // Android standard resource attribute IDs
    private const val ATTR_LABEL = 0x01010001
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_EXPORTED = 0x01010010
    private const val ATTR_MIN_SDK = 0x0101020c
    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c
    private const val ATTR_TARGET_SDK = 0x01010270

    data class AttrData(
        val nsIndex: Int,
        val nameIndex: Int,
        val rawValueIndex: Int,
        val dataType: Int,
        val data: Int
    )

    fun generate(
        packageName: String,
        appName: String,
        mainActivityName: String,
        minSdk: Int = 21,
        targetSdk: Int = 34,
        versionCode: Int = 1,
        versionName: String = "1.0",
        permissions: List<String> = emptyList()
    ): ByteArray {
        val cleanPkg = packageName.ifBlank { "com.example.app" }
        val cleanApp = appName.ifBlank { "App" }
        val fullActivity = if (mainActivityName.startsWith(".")) {
            cleanPkg + mainActivityName
        } else if (!mainActivityName.contains(".")) {
            "$cleanPkg.$mainActivityName"
        } else {
            mainActivityName
        }

        val strings = mutableListOf(
            "http://schemas.android.com/apk/res/android", // 0
            "android",                                   // 1
            "manifest",                                  // 2
            "package",                                   // 3
            "versionCode",                               // 4
            "versionName",                               // 5
            "uses-sdk",                                  // 6
            "minSdkVersion",                             // 7
            "targetSdkVersion",                          // 8
            "application",                               // 9
            "label",                                     // 10
            "activity",                                  // 11
            "name",                                      // 12
            "exported",                                  // 13
            "intent-filter",                             // 14
            "action",                                    // 15
            "category",                                  // 16
            "android.intent.action.MAIN",                // 17
            "android.intent.category.LAUNCHER",          // 18
            "uses-permission",                           // 19
            cleanPkg,                                    // 20
            versionName,                                 // 21
            cleanApp,                                    // 22
            fullActivity                                 // 23
        )

        // Add unique permission strings
        val permIndices = mutableListOf<Int>()
        for (p in permissions.distinct()) {
            if (p.isNotBlank()) {
                val idx = strings.size
                strings.add(p)
                permIndices.add(idx)
            }
        }

        // Build UTF-16 string pool
        val strDataStream = ByteArrayOutputStream()
        val strOffsets = mutableListOf<Int>()
        for (s in strings) {
            strOffsets.add(strDataStream.size())
            val chars = s.toCharArray()
            val lenBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(chars.size.toShort()).array()
            strDataStream.write(lenBytes)
            for (c in chars) {
                val cBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putChar(c).array()
                strDataStream.write(cBytes)
            }
            strDataStream.write(byteArrayOf(0, 0)) // 2-byte null terminator
        }

        // Pad string data to 4-byte boundary
        while (strDataStream.size() % 4 != 0) {
            strDataStream.write(0)
        }
        val strData = strDataStream.toByteArray()

        val spHeaderSize = 28
        val spSize = spHeaderSize + (strOffsets.size * 4) + strData.size
        val strPoolBuf = ByteBuffer.allocate(spSize).order(ByteOrder.LITTLE_ENDIAN)
        strPoolBuf.putShort(RES_STRING_POOL_TYPE)
        strPoolBuf.putShort(spHeaderSize.toShort())
        strPoolBuf.putInt(spSize)
        strPoolBuf.putInt(strings.size)
        strPoolBuf.putInt(0) // styleCount
        strPoolBuf.putInt(0) // flags
        strPoolBuf.putInt(spHeaderSize + (strOffsets.size * 4)) // stringsStart
        strPoolBuf.putInt(0) // stylesStart
        for (off in strOffsets) {
            strPoolBuf.putInt(off)
        }
        strPoolBuf.put(strData)
        val strPoolBytes = strPoolBuf.array()

        // Resource map array aligned with strings
        val resIds = IntArray(strings.size)
        resIds[4] = ATTR_VERSION_CODE
        resIds[5] = ATTR_VERSION_NAME
        resIds[7] = ATTR_MIN_SDK
        resIds[8] = ATTR_TARGET_SDK
        resIds[10] = ATTR_LABEL
        resIds[12] = ATTR_NAME
        resIds[13] = ATTR_EXPORTED

        val resMapSize = 8 + (resIds.size * 4)
        val resMapBuf = ByteBuffer.allocate(resMapSize).order(ByteOrder.LITTLE_ENDIAN)
        resMapBuf.putShort(RES_XML_RESOURCE_MAP_TYPE)
        resMapBuf.putShort(8.toShort())
        resMapBuf.putInt(resMapSize)
        for (id in resIds) {
            resMapBuf.putInt(id)
        }
        val resMapBytes = resMapBuf.array()

        // XML Nodes
        val nodesStream = ByteArrayOutputStream()

        fun writeStartNs() {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(RES_XML_START_NAMESPACE_TYPE)
            buf.putShort(16.toShort())
            buf.putInt(24)
            buf.putInt(1) // line
            buf.putInt(-1) // comment
            buf.putInt(1) // prefix "android"
            buf.putInt(0) // uri
            nodesStream.write(buf.array())
        }

        fun writeEndNs() {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(RES_XML_END_NAMESPACE_TYPE)
            buf.putShort(16.toShort())
            buf.putInt(24)
            buf.putInt(1)
            buf.putInt(-1)
            buf.putInt(1)
            buf.putInt(0)
            nodesStream.write(buf.array())
        }

        fun writeStartElement(nsIndex: Int, nameIndex: Int, attrs: List<AttrData>) {
            val chunkSize = 16 + 20 + (attrs.size * 20)
            val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(RES_XML_START_ELEMENT_TYPE)
            buf.putShort(16.toShort())
            buf.putInt(chunkSize)
            buf.putInt(1)
            buf.putInt(-1)
            buf.putInt(nsIndex)
            buf.putInt(nameIndex)
            buf.putShort(20.toShort()) // attributeStart
            buf.putShort(20.toShort()) // attributeSize
            buf.putShort(attrs.size.toShort()) // attributeCount
            buf.putShort(0.toShort()) // idIndex
            buf.putShort(0.toShort()) // classIndex
            buf.putShort(0.toShort()) // styleIndex
            for (attr in attrs) {
                buf.putInt(attr.nsIndex)
                buf.putInt(attr.nameIndex)
                buf.putInt(attr.rawValueIndex)
                buf.putShort(8.toShort()) // size
                buf.put(0.toByte()) // res0
                buf.put(attr.dataType.toByte())
                buf.putInt(attr.data)
            }
            nodesStream.write(buf.array())
        }

        fun writeEndElement(nsIndex: Int, nameIndex: Int) {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(RES_XML_END_ELEMENT_TYPE)
            buf.putShort(16.toShort())
            buf.putInt(24)
            buf.putInt(1)
            buf.putInt(-1)
            buf.putInt(nsIndex)
            buf.putInt(nameIndex)
            nodesStream.write(buf.array())
        }

        // 1. Start android namespace
        writeStartNs()

        // 2. <manifest package="cleanPkg" android:versionCode="1" android:versionName="1.0">
        val manifestAttrs = listOf(
            AttrData(-1, 3, 20, TYPE_STRING, 20), // package="cleanPkg"
            AttrData(0, 4, -1, TYPE_INT_DEC, versionCode), // android:versionCode
            AttrData(0, 5, 21, TYPE_STRING, 21) // android:versionName
        )
        writeStartElement(-1, 2, manifestAttrs)

        // 3. <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="34" />
        val sdkAttrs = listOf(
            AttrData(0, 7, -1, TYPE_INT_DEC, minSdk),
            AttrData(0, 8, -1, TYPE_INT_DEC, targetSdk)
        )
        writeStartElement(-1, 6, sdkAttrs)
        writeEndElement(-1, 6)

        // 4. Optional <uses-permission android:name="..." />
        for (permIdx in permIndices) {
            val permAttr = listOf(AttrData(0, 12, permIdx, TYPE_STRING, permIdx))
            writeStartElement(-1, 19, permAttr)
            writeEndElement(-1, 19)
        }

        // 5. <application android:label="cleanApp">
        val appAttrs = listOf(
            AttrData(0, 10, 22, TYPE_STRING, 22)
        )
        writeStartElement(-1, 9, appAttrs)

        // 6. <activity android:name="fullActivity" android:exported="true">
        val actAttrs = listOf(
            AttrData(0, 12, 23, TYPE_STRING, 23),
            AttrData(0, 13, -1, TYPE_INT_BOOLEAN, -1) // -1 is 0xFFFFFFFF for true
        )
        writeStartElement(-1, 11, actAttrs)

        // 7. <intent-filter>
        writeStartElement(-1, 14, emptyList())

        // 8. <action android:name="android.intent.action.MAIN" />
        writeStartElement(-1, 15, listOf(AttrData(0, 12, 17, TYPE_STRING, 17)))
        writeEndElement(-1, 15)

        // 9. <category android:name="android.intent.category.LAUNCHER" />
        writeStartElement(-1, 16, listOf(AttrData(0, 12, 18, TYPE_STRING, 18)))
        writeEndElement(-1, 16)

        // Close intent-filter, activity, application, manifest, namespace
        writeEndElement(-1, 14)
        writeEndElement(-1, 11)
        writeEndElement(-1, 9)
        writeEndElement(-1, 2)
        writeEndNs()

        val nodesBytes = nodesStream.toByteArray()

        val totalSize = 8 + strPoolBytes.size + resMapBytes.size + nodesBytes.size
        val headerBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.putShort(RES_XML_TYPE)
        headerBuf.putShort(8.toShort())
        headerBuf.putInt(totalSize)

        val out = ByteArrayOutputStream(totalSize)
        out.write(headerBuf.array())
        out.write(strPoolBytes)
        out.write(resMapBytes)
        out.write(nodesBytes)

        return out.toByteArray()
    }
}
