package org.sample.httpfs;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class httpfs {
    private final String directory;
    private final StringBuilder response;
    private InputStream inputStreamReader;
    private BufferedReader bufferedReader;
    private String file;
    private java.util.Date date;

    public httpfs(String directory, boolean verbose) {
        this.directory = directory;
        this.response = new StringBuilder("");
    }

    String run(String req) throws IOException {
        this.date = new java.util.Date();
        inputStreamReader = new ByteArrayInputStream(req.getBytes(Charset.forName("UTF-8")));
        bufferedReader = new BufferedReader(new InputStreamReader(inputStreamReader));
        String line = bufferedReader.readLine();
        if (line != null) {
            if (line.contains("GET")) {
                file = line.split(" ")[1];
                return runGet(file);
            } else if (line.contains("POST")) {
                file = line.split(" ")[1];
                StringBuilder payload = new StringBuilder();
                while (bufferedReader.ready()) {
                    payload.append((char) bufferedReader.read());
                }
                return runPost(file, payload.toString());
            }
        }
        return errorResponse("400 Bad Request");
    }

    private String runGet(String fileName) throws IOException {
        File file = new File(directory, fileName);

        if (fileName.contains("..")) {
            return errorResponse("403 Forbidden");
        } else if (!file.exists()) {
            return errorResponse("404 Not Found");
        } else if (file.isDirectory()) {
            return directoryListing(file);
        } else {
            return fileResponse(file);
        }
    }

    private String runPost(String fileName, String data) throws IOException {
        File file = new File(directory, fileName);

        if (fileName.contains("..")) {
            return errorResponse("403 Forbidden");
        }

        if (!file.exists()) {
            Files.createDirectories(Paths.get(file.getParent()));
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(data);
            }
            return successResponse("201 Created", data.length(), "New file created");
        } else {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(data);
            }
            return successResponse("200 OK", data.length(), "File contents overwritten");
        }
    }

    private String directoryListing(File directory) {
        response.append("200 OK\r\n")
                .append("Content-Length: ").append(directory.listFiles().length)
                .append("\r\nContent-Type: Directory\r\nDate: ").append(date)
                .append("\r\n\r\n");

        for (File file : directory.listFiles()) {
            response.append(file.getName()).append("\n");
        }
        return response.toString();
    }

    private String fileResponse(File file) throws IOException {
        String fileType = Files.probeContentType(file.toPath());
        fileType = (fileType == null) ? "Unknown" : fileType;

        StringBuilder fileData = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileData.append(line).append("\n");
            }
        }

        return successResponse("200 OK", fileData.length(), fileData.toString(), fileType);
    }

    private String successResponse(String status, int contentLength, String message) {
        response.setLength(0);
        response.append("HTTP/1.0 ").append(status)
                .append("\r\nContent-Length: ").append(contentLength)
                .append("\r\nDate: ").append(date)
                .append("\r\n\r\n").append(message);
        return response.toString();
    }

    private String successResponse(String status, int contentLength, String message, String contentType) {
        response.setLength(0);
        response.append("HTTP/1.0 ").append(status)
                .append("\r\nContent-Length: ").append(contentLength)
                .append("\r\nContent-Type: ").append(contentType)
                .append("\r\nDate: ").append(date)
                .append("\r\n\r\n").append(message);
        return response.toString();
    }

    private String errorResponse(String status) {
        response.setLength(0);
        response.append("HTTP/1.0 ").append(status)
                .append("\r\nDate: ").append(date)
                .append("\r\n\r\n");
        return response.toString();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        String directory = args[1];
        httpfs server = new httpfs(directory, false);
        UDPServer udpServer = new UDPServer(server, port);
        udpServer.serve();
    }
}
