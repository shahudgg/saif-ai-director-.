package com.example.compiler

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry

/**
 * High-performance, 4-byte aligned ZIP packager for Android APKs.
 * Ensures `resources.arsc` and `classes.dex` are STORED uncompressed and aligned
 * to 4-byte boundaries so Android OS PackageParser never fails with "App not installed".
 */
object AlignedZipWriter {

    data class ZipItem(
        val name: String,
        val data: ByteArray,
        val isStored: Boolean
    )

    fun write(items: List<ZipItem>, outputFile: File) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            val lfhOffsets = ArrayList<Long>()
            val compressedDataList = ArrayList<ByteArray>()
            val crcList = ArrayList<Long>()
            val extraFieldsList = ArrayList<ByteArray>()

            var currentOffset = 0L

            for (item in items) {
                val nameBytes = item.name.toByteArray(Charsets.UTF_8)
                val crc = CRC32()
                crc.update(item.data)
                val crcVal = crc.value
                crcList.add(crcVal)

                val method = if (item.isStored) ZipEntry.STORED else ZipEntry.DEFLATED
                val dataToWrite: ByteArray
                if (item.isStored) {
                    dataToWrite = item.data
                } else {
                    val baos = ByteArrayOutputStream()
                    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
                    DeflaterOutputStream(baos, deflater).use { defos ->
                        defos.write(item.data)
                        defos.finish()
                    }
                    dataToWrite = baos.toByteArray()
                }
                compressedDataList.add(dataToWrite)

                // 4-byte alignment calculation for STORED entries
                val extra: ByteArray
                if (item.isStored) {
                    val unalignedDataOffset = currentOffset + 30 + nameBytes.size
                    val pad = ((4 - (unalignedDataOffset % 4)) % 4).toInt()
                    extra = if (pad > 0) ByteArray(pad) else ByteArray(0)
                } else {
                    extra = ByteArray(0)
                }
                extraFieldsList.add(extra)

                // Local File Header (30 bytes)
                val lfh = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
                lfh.putInt(0x04034b50) // LFH signature
                lfh.putShort(20.toShort()) // Min version 2.0
                lfh.putShort(0.toShort()) // General purpose flags
                lfh.putShort(method.toShort()) // Compression method
                lfh.putShort(0.toShort()) // Last mod time
                lfh.putShort(0.toShort()) // Last mod date
                lfh.putInt(crcVal.toInt()) // CRC-32
                lfh.putInt(dataToWrite.size) // Compressed size
                lfh.putInt(item.data.size) // Uncompressed size
                lfh.putShort(nameBytes.size.toShort()) // Name length
                lfh.putShort(extra.size.toShort()) // Extra field length

                lfhOffsets.add(currentOffset)
                fos.write(lfh.array())
                fos.write(nameBytes)
                if (extra.isNotEmpty()) fos.write(extra)
                fos.write(dataToWrite)

                currentOffset += 30 + nameBytes.size + extra.size + dataToWrite.size
            }

            val cdOffset = currentOffset
            // Central Directory records
            for (i in items.indices) {
                val item = items[i]
                val nameBytes = item.name.toByteArray(Charsets.UTF_8)
                val compData = compressedDataList[i]
                val crcVal = crcList[i]
                val lfhOff = lfhOffsets[i]
                val method = if (item.isStored) ZipEntry.STORED else ZipEntry.DEFLATED

                val cdh = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
                cdh.putInt(0x02014b50) // CD signature
                cdh.putShort(20.toShort()) // Version made by
                cdh.putShort(20.toShort()) // Version needed
                cdh.putShort(0.toShort()) // Flags
                cdh.putShort(method.toShort()) // Method
                cdh.putShort(0.toShort()) // Mod time
                cdh.putShort(0.toShort()) // Mod date
                cdh.putInt(crcVal.toInt()) // CRC-32
                cdh.putInt(compData.size) // Compressed size
                cdh.putInt(item.data.size) // Uncompressed size
                cdh.putShort(nameBytes.size.toShort()) // File name length
                cdh.putShort(0.toShort()) // Extra field length in CD
                cdh.putShort(0.toShort()) // File comment length
                cdh.putShort(0.toShort()) // Disk start
                cdh.putShort(0.toShort()) // Internal file attributes
                cdh.putInt(0) // External file attributes
                cdh.putInt(lfhOff.toInt()) // Relative offset of local header

                fos.write(cdh.array())
                fos.write(nameBytes)
                currentOffset += 46 + nameBytes.size
            }

            val cdSize = currentOffset - cdOffset
            // End of Central Directory (22 bytes)
            val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            eocd.putInt(0x06054b50) // EOCD signature
            eocd.putShort(0.toShort()) // Disk number
            eocd.putShort(0.toShort()) // Start disk
            eocd.putShort(items.size.toShort()) // Entries on disk
            eocd.putShort(items.size.toShort()) // Total entries
            eocd.putInt(cdSize.toInt()) // CD size
            eocd.putInt(cdOffset.toInt()) // CD offset
            eocd.putShort(0.toShort()) // Comment length

            fos.write(eocd.array())
        }
    }
}
