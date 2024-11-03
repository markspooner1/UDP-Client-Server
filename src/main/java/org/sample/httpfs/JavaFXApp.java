package org.sample.httpfs;
import javafx.scene.control.TextArea;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;

public class JavaFXApp extends Application {
    private   ComboBox<Double> dropRateField;
    private ComboBox<Number> maxDelayField;
    private ComboBox<String> methodBox;
    private TextArea dataField;
    private TextField urlField;
    public static TextField clientPortField;
    public static TextField serverPortField;
    public static TextField routerPortField;
    private TextField directoryField;
    private static TextArea logArea;
    @Override
    public void start(Stage primaryStage) {
    	logArea = new TextArea();
    	logArea.setEditable(false);
        Label dropRateLabel = new Label("Drop Rate:");
        Label maxDelayLabel = new Label("Max Delay (ms):");
        Label urlLabel = new Label("URL:");
        Label methodLabel = new Label("Method:");
        Label dataLabel = new Label("Data:");
        Label clientPortLabel = new Label("Client Port:");
        Label serverPortLabel = new Label("Server Port:");
        Label routerPortLabel = new Label("Router Port:");
        Label directoryLabel = new Label("Directory:");
        Label logLabel = new Label("Log:");

        dropRateField = new ComboBox<>();
        dropRateField.getItems().addAll(0.0, 0.1, 0.2, 0.3, 0.4, 0.5);
        dropRateField.setValue(0.0);

        maxDelayField = new ComboBox<>();
        maxDelayField.getItems().addAll(0, 10, 20, 30, 40, 50);
        maxDelayField.setValue(0);

        urlField = new TextField("http://localhost/");
        urlField.setTooltip(new Tooltip("Enter the URL to send the request to"));

        methodBox = new ComboBox<>();
        methodBox.getItems().addAll("GET", "POST");
        methodBox.setValue("POST");

        dataField = new TextArea();
        dataField.setVisible(true);
        
        clientPortField = new TextField("8007");
        clientPortField.setPromptText("Client Port");

        serverPortField = new TextField("8008");
        serverPortField.setPromptText("Server Port");

        routerPortField = new TextField("3000");
        routerPortField.setPromptText("Router Port");

        directoryField = new TextField(System.getProperty("user.dir"));

        Button sendRequest = new Button("Send Request");

        methodBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if ("POST".equals(newValue)) {
                    dataField.setVisible(true);
                    dataLabel.setVisible(true);
                } else {
                    dataField.setVisible(false);
                    dataLabel.setVisible(false);
                }
            }
        });

        sendRequest.setOnAction(e -> {
            ExecutorService executor = Executors.newFixedThreadPool(3);
            executor.submit(this::Router);
            executor.submit(this::Server);
            executor.submit(this::Client);
            executor.shutdown();
        });

        GridPane root = new GridPane();
        root.setPadding(new Insets(10));
        root.setHgap(10);
        root.setVgap(10);

        root.add(dropRateLabel, 0, 0);
        root.add(dropRateField, 1, 0);
        root.add(maxDelayLabel, 0, 1);
        root.add(maxDelayField, 1, 1);
        root.add(urlLabel, 0, 2);
        root.add(urlField, 1, 2);
        root.add(methodLabel, 0, 3);
        root.add(methodBox, 1, 3);
        root.add(dataLabel, 0, 4);
        root.add(dataField, 1, 4);
        root.add(sendRequest, 1, 5);
        root.add(clientPortLabel, 0, 6);
        root.add(clientPortField, 1, 6);
        root.add(serverPortLabel, 0, 7);
        root.add(serverPortField, 1, 7);
        root.add(routerPortLabel, 0, 8);
        root.add(routerPortField, 1, 8);
        root.add(logLabel, 0, 10);
        root.add(logArea, 0, 11, 2, 1);  
        root.add(directoryLabel, 0, 9);
        root.add(directoryField, 1, 9);
        for (int i = 0; i < 2; i++) {
            root.getColumnConstraints().add(new ColumnConstraints(100, 150, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true));
        }
        root.getRowConstraints().addAll(new RowConstraints(30), new RowConstraints(30), new RowConstraints(30),
                new RowConstraints(30), new RowConstraints(100), new RowConstraints(30),
                new RowConstraints(30), new RowConstraints(30), new RowConstraints(30),
                new RowConstraints(30));

        Scene scene = new Scene(root, 800, 700);
        scene.getStylesheets().add("styles.css");

        primaryStage.setTitle("JavaFX App");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    public static void updateLog(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

	public static void showResponse(String response) {
		Platform.runLater(() -> {
			logArea.appendText(response);
			logArea.setScrollTop(Double.MAX_VALUE);
			
		});
	}
    private void Router() {
        String[] arguments = {dropRateField.getValue().toString(), maxDelayField.getValue().toString(), routerPortField.getText()};
        Router.main(arguments, this);
    }

    private void Server() {
        String[] arguments = {serverPortField.getText(), directoryField.getText()};
        try {
            httpfs.main(arguments);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void Client() {
        String[] arguments = {urlField.getText(), methodBox.getValue(), dataField.getText(), clientPortField.getText()};
        httpc.main(arguments, this);
    }

    public static void main(String[] args) {
    	launch(args);
    }
}
