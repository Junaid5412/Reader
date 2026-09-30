package com.junaid.sitemanager.monitor

data class CheckResult(
    val status: String,
    val code: Int,
    val latencyMs: Long,
    val error: String
)
