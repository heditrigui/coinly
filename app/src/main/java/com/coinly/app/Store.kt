package com.coinly.app

import android.content.Context
import com.coinly.app.data.FinanceRepository
import java.io.File

/** One repository per process, shared by the app, the home screen widget and the reminder worker. */
object Store {
    @Volatile private var repo: FinanceRepository? = null

    fun repo(context: Context): FinanceRepository = repo ?: synchronized(this) {
        repo ?: run {
            val app = context.applicationContext
            FinanceRepository(File(app.filesDir, "coinly.json")) { CoinlyWidget.refresh(app, it) }.also { repo = it }
        }
    }
}
