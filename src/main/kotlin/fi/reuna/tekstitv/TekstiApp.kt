package fi.reuna.tekstitv

import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Executor


class CurrentThreadExecutor : Executor {
    override fun execute(command: Runnable?) {
        command?.run()
    }
}

fun main(args: Array<String>) {
    Log.debug("begin")

////    doLogTest()
////    clearConsole()
//
//    val scheduler1 = TestScheduler()
//
//    var valve = BehaviorSubject.create<Boolean>()
//
//    val openValve = valve
//            .filter { it }
//            .take(1)
//
//    openValve.subscribe { Log.debug(if (it) "kyl" else "ei") }
//
//    valve.onNext(true)
//    //valve.onNext(false)
//
//
//
//    val t0 = System.currentTimeMillis()
//    val nums = listOf(1, 2, 3, 4, 5)
//    var last = 'A'
//    var ignoreBefore = 0
//
//    Observable.fromIterable(nums)
//            //.observeOn(Schedulers.computation())
//            .concatMap { num ->
//
//
//
//                openValve.map { "num#$num-$last" }
//                        //.delay(6L-num.toLong(), TimeUnit.SECONDS)
//                        .delay(1, TimeUnit.SECONDS)
//                        .map { "$it.${System.currentTimeMillis() - t0}" }
//                        .filter { num >= ignoreBefore }
//                        .doOnNext { last = (last.toInt() + 1).toChar() }
//
////                Observable.just("num#$num")
////                        .delay(1, TimeUnit.SECONDS, scheduler1)
////                        .doOnNext { println("<$it>.${scheduler1.now(TimeUnit.SECONDS)}") }
//            }
//            .subscribe { println(it) }
//
////    scheduler1.advanceTimeBy(2, TimeUnit.SECONDS)
////    valve.onNext(false)
////    scheduler1.advanceTimeBy(2, TimeUnit.SECONDS)
////    valve.onNext(true)
////
////    scheduler1.advanceTimeBy(20, TimeUlnit.SECONDS)
//
//    Thread.sleep(1000)
//    valve.onNext(false)
//    Thread.sleep(4000)
//    valve.onNext(true)
//    ignoreBefore = 4
//
//    Thread.sleep(20000)l

    /*
    TODO
    - kun tulee back, ja on historiaa -> ignore tulevat
        - merkkaa ylös mistä nav eventistä lähtien tulee ignorata

    - tee palikka joka
        - jos on muistissa sivu palauta se suoraan
        - muutoin palauta tyhjä sivu
        - jos lataus feilaa -> palauta sivu jossa virhe ilmoitus -- nääh, heitä vain virhe
        - päivittää sivuja automaattisesti väliajoin

    - ui päivittämään nykyistä sivua N sekunnin välein

    - jlinella lue syötettä (up|dn|left|right + numerot

     */

    val replacer = Regex("\\[\\w{4}\\]")
    val provider = PageProvider()
    provider.observe()
            .observeOn(Schedulers.single())
            .subscribe {

                when (it) {
                    is PageEvent.Loaded -> {
                        Log.debug("got subpage ${it.subpage.location.page}")
                        println("----------------------- ${it.subpage.location.page}/${it.subpage.location.sub} --------------------------")
                        println(it.subpage.content.replace(replacer, " "))
                    }
                    is PageEvent.Failed -> Log.error("failed to load subpage ${it.location}: ${it.error}")
                }

            }

//    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> page->100")
//    provider.set(100)
//    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> next1")
//    provider.nextPage()
//    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> next2")
//    provider.nextPage()
//    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> next3")
//    provider.nextPage()
//    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> next4")
//    provider.nextPage()
    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> page->202")
    provider.set(202)
    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> nextPage sub")
    provider.nextSubpage()
    provider.prevSubpage()
    Log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> prev1")
    provider.prevPage()

    Thread.sleep(10000)
}

fun doLogTest() {
    (1..10).forEach { Log.debug("Hello world!") }
}

fun clearConsole() {
    print("\u001b[H\u001b[2J")
}
