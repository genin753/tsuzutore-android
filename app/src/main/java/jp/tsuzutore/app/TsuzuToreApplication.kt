package jp.tsuzutore.app

import android.app.Application
import jp.tsuzutore.app.data.AppRepository
import jp.tsuzutore.app.platform.CuePlayer
import jp.tsuzutore.app.platform.WorkoutController

class TsuzuToreApplication : Application() {
    lateinit var repository: AppRepository
        private set
    lateinit var controller: WorkoutController
        private set
    lateinit var cues: CuePlayer
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        cues = CuePlayer(this)
        controller = WorkoutController(this, repository)
    }
}
