import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    println("starting...")
    runBlocking {
        repeat(20) {
            launch {
                println("Start of launch")
                delay(1000)
                println("End of launch")
            }
        }
        println("Code outside of launch")
    }
    println("done")
}