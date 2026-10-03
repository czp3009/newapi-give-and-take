import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class PrintVersion : DefaultTask() {
    init {
        group = "help"
        description = "Prints the project version."
    }

    @TaskAction
    fun printVersion() {
        println(BuildConfig.VERSION)
    }
}
