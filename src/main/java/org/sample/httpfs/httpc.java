package org.sample.httpfs;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class httpc {

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String HTTP_VERSION = "HTTP/1.0";
    private static final String CRLF = "\r\n";


    
    private String requestType;
    
    private ArrayList<String> headers = new ArrayList<>();
    
    
    
    private String data;
    
    private String url;

    private String request = "";
    
    
    
    private URI uri;

	public httpc(String requestType,String data, String url) {
		this.requestType = requestType;
		this.data = data;
		this.url = url;
	}
	

   
    public void sendRequest() {
        try {
           uri = new URI(url);
            if (requestType.equalsIgnoreCase("get")) {
                sendGetRequest(uri);
            } else if (requestType.equalsIgnoreCase("post")) {
              
                sendPostRequest(uri);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendGetRequest(URI uri) throws IOException {
        buildGetRequest(uri);
        sendUdpRequest();
    }

    private void sendPostRequest(URI uri) throws IOException {
        buildPostRequest(uri);
        sendUdpRequest();
    }

    private void buildGetRequest(URI uri) {
        request = "GET " + uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery()) + " " + HTTP_VERSION + CRLF;
        addDefaultHeaders(uri);
    }

    private void buildPostRequest(URI uri) {
        request = "POST " + uri.getPath() + " " + HTTP_VERSION + CRLF;
        if (data != null) {
            headers.add("Content-length: " + data.length());
        }
        addDefaultHeaders(uri);
        request += CRLF + data;
    }

    private void addDefaultHeaders(URI uri) {
        headers.add("User-Agent: " + USER_AGENT);
        headers.add("Host: " + uri.getHost());
        appendHeaders();
        request += CRLF;
    }

    private void appendHeaders() {
        for (String header : headers) {
            request += header + CRLF;
        }
    }

    private void sendUdpRequest() throws IOException {
        InetSocketAddress SERVER = new InetSocketAddress("localhost", Integer.parseInt(JavaFXApp.serverPortField.getText()));
        InetSocketAddress ROUTER = new InetSocketAddress("localhost", Integer.parseInt(JavaFXApp.routerPortField.getText()));
        InetSocketAddress CLIENT = new InetSocketAddress("localhost", Integer.parseInt(JavaFXApp.clientPortField.getText()));

        UDPClient client = new UDPClient(SERVER, ROUTER, CLIENT);
        client.startClient(request.getBytes());
        
        
    }

 
    public static void main(String[] args, JavaFXApp app) {
    	String url = args[0];
    	String requestType = args[1];
    	String data = args[2];
    	httpc httpc = new httpc(requestType, data, url);
    	httpc.sendRequest();
    	
    	
    }

}
