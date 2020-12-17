package com.hyy.readeraudiosample.model

import android.os.Parcel
import android.os.Parcelable

/**
 *Create by hyy on 2020/12/14
 */
data class ChapterAudioItem(
    val chapterName: String,
    val bookName: String,
    val source: String,
    val id: String,
    val duration: Long,
    val img: String
): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(chapterName)
        parcel.writeString(bookName)
        parcel.writeString(source)
        parcel.writeString(id)
        parcel.writeLong(duration)
        parcel.writeString(img)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ChapterAudioItem> {
        override fun createFromParcel(parcel: Parcel): ChapterAudioItem {
            return ChapterAudioItem(parcel)
        }

        override fun newArray(size: Int): Array<ChapterAudioItem?> {
            return arrayOfNulls(size)
        }
    }

}