package com.originsu.manager.data.logging

import android.app.Application
import com.originsu.manager.data.shell.KsuCliRepository
import java.io.File

class BugreportRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun create(): File = getBugreportFile(application, ksuCliRepository)
}
