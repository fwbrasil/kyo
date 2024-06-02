package kyo.scheduler.top

import scala.annotation.nowarn

@nowarn
object Console extends App {

    delayedInit {
        Client.run(args.toList) { status =>
            val clear = "\u001b[2J\u001b[H"
            println(clear + "\n" + Printer(status))
        }
    }
}
