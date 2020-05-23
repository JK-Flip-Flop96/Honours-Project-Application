package com.honours.project.models

data class Report(
    var owner: String,
    var lat: Double,
    var long: Double,
    var imgRef: String,
    var time: String,
    var category: String,
    var description: String
)