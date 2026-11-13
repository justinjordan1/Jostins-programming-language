package src.Web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

public class WorkerMain {
    @JSFunctor
    public interface Handler extends JSObject {
        void handle(String body);
    }

    @JSBody(params = "handler", script = "self.onmessage = function (e) { handler(e.data); };")
    private static native void listen(Handler handler);

    @JSBody(params = "text", script = "self.postMessage(text);")
    private static native void post(String text);

    public static void main(String[] args) {
        listen(body -> WebMain.run(body, WorkerMain::post));
        post("@@ready");
    }
}
