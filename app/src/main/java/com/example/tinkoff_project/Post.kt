package com.example.tinkoff_project

data class Post(
    val id: Int,
    val description: String,
    val votes: Int,
    val author: String,
    val date: String,
    var gifURL: String?,
    val gifSize: Int,
    val previewURL: String,
    val videoURL: String
)