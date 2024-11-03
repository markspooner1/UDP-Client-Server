package org.sample.httpfs;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class UILogHandler extends Handler {
    private JavaFXApp uiApp;

    public UILogHandler(JavaFXApp app) {
        this.uiApp = app;
    }

    @Override
    public void publish(LogRecord record) {
        if (uiApp != null) {
            uiApp.updateLog(record.getMessage());
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
}
