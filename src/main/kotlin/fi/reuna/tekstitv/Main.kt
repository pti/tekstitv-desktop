package fi.reuna.tekstitv

import javafx.application.Application
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.stage.Stage
import kotlin.concurrent.thread

class TekstiTvApp: Application() {

    private var controller: Controller? = null

    override fun start(primaryStage: Stage?) {
        Log.debug("start")

        val text = Text()
        text.fill = Color.WHITE
        text.font = Font.font("unscii", 20.0)

        val root = VBox()
        root.children.add(text)
        root.padding = Insets(10.0)

        val scene = Scene(root, 500.0, 600.0, Color.BLACK)

        primaryStage?.scene = scene
        primaryStage?.x = 0.0
        primaryStage?.y = 0.0

        Log.debug("stage initialized")
        primaryStage?.show()
        Log.debug("did show")

        // Run separately so that the state gets shown asap. Otherwise TODO what
        // TODO coroutine instead?
        thread { controller = Controller(text) }
        Log.debug("done")
    }

    override fun stop() {
        super.stop()
        controller?.stop()
    }
}

fun main(args: Array<String>) {
    System.setProperty("prism.lcdtext", "false");
    Application.launch(TekstiTvApp::class.java, *args)
}
