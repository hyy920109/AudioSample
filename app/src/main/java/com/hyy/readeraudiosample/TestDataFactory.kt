package com.hyy.readeraudiosample

import com.hyy.readeraudiosample.model.ChapterAudioItem

/**
 *Create by hyy on 2020/12/16
 */
object TestDataFactory {

    fun mediaItem1(): ChapterAudioItem = ChapterAudioItem(
        "Chapter One",
        "Book Hyy",
        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/01_-_Intro_-_The_Way_Of_Waking_Up_feat_Alan_Watts.mp3",
        "18888",
        90,
        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
    )

    fun mediaItem2(): ChapterAudioItem = ChapterAudioItem(
        "Chapter Two",
        "Book Hyy",
        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/02_-_Geisha.mp3",
        "18889",
        267,
        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
    )

    fun mediaItem3(): ChapterAudioItem = ChapterAudioItem(
        "Chapter Three",
        "Book Hyy",
        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/03_-_Voyage_I_-_Waterfall.mp3",
        "18890",
        264,
        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
    )

    fun mediaItem4(): ChapterAudioItem = ChapterAudioItem(
        "Chapter Four",
        "Book Hyy",
        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/04_-_The_Music_In_You.mp3",
        "18891",
        223,
        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
    )

    fun mediaItem5(): ChapterAudioItem = ChapterAudioItem(
        "Chapter Five",
        "Book Hyy",
        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/05_-_The_Calm_Before_The_Storm.mp3",
        "18892",
        229,
        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
    )

    fun mediaItem6(): ChapterAudioItem = ChapterAudioItem(
        "Chapter Six",
        "Book Hyy",
        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/06_-_No_Pain_No_Gain.mp3",
        "18893",
        304,
        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
    )

}