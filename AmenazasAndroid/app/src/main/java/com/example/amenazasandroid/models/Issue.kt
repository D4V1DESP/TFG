package com.example.amenazasandroid.models

data class Issue(
    val title: String,
    val description: String,
    val section: String
)

data class AppReport(
    val high: List<Issue>,
    val warning: List<Issue>,
    val info: List<Issue>,
    val secure: List<Issue>,
    val hotspot: List<Issue>,
    val total_trackers: Int,
    val trackers: Int,
    val security_score: Int,
    val app_name: String,
    val file_name: String,
    val hash: String,
    val version_name: String,
    val version: String,
    val title: String,
    val efr01: Boolean
)

data class IssueWithCategory(
    val category: String,
    val issue: Issue
)