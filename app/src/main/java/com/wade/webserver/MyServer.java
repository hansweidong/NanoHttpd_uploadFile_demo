package com.wade.webserver;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MyServer extends NanoHTTPD {
    private final String TAG = "imWebService";

    public static int PORT = 8899;
    public static String rootDir = Environment.getExternalStorageDirectory().getPath();
    private static Boolean serverState = false;
    private static String uploadFileHTML =
            "<form method='post' enctype='multipart/form-data' action='/u'>"+
            "    Step 1. Choose a file: <input type='file' name='upload' /><br />"+
            "    Step 2. Click Send to upload file: <input type='submit' value='Send' /><br />"+
            "</form>";
    /*
     * 在此處理從 activity 透過 startService(intent) 中的 intent 傳遞過來的訊息
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected Response getForbiddenResponse(String s) {
        Response r = newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: " + s);
        return r;
    }

    /**
     * Serves file from homeDir and its' subdirectories (only). Uses only URI,
     * ignores all headers and HTTP parameters.
     */

    private Response respondUpload(IHTTPSession session) {
        Log.d("MyServer", "session.getMethod:" + session.getMethod());
        if (session.getMethod() == Method.GET) {
            return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_HTML, uploadFileHTML);
        }
        else {
            // {remote-addr=192.168.203.251, content-length=43798720, host=192.168.220.58:8080, http-client-ip=192.168.203.251,
            // user-agent=Mozilla/4.0, pragma=no-cache, content-type=multipart/form-data; boundary=---------------------------baofengupload}
            Log.d("singleconn", "session.headers:" + session.getHeaders());
            Log.d("singleconn", "session.remote:" + session.getHeaders().get("remote-addr")); // 192.168.203.251
            Log.d("singleconn", "session.length:" + session.getHeaders().get("content-length")); // 639030
            Log.d("singleconn", "session.host:" + session.getHeaders().get("host")); // 192.168.220.58:8080
            Log.d("singleconn", "session.http-client:" + session.getHeaders().get("http-client-ip")); // 192.168.203.251
            Log.d("singleconn", "session.user-agent:" + session.getHeaders().get("user-agent")); // Mozilla/4.0
            Log.d("singleconn", "session.pragma:" + session.getHeaders().get("pragma")); // no-cache
            Log.d("singleconn", "session.content-type:" + session.getHeaders().get("content-type")); // multipart/form-data; boundary=---------------------------baofengupload
            Log.d("singleconn", "session.boundary:" + session.getHeaders().get("boundary")); // null

            final int READ_BUFFER_SIZE = 512*1024; // 0.5 MB
            BufferedInputStream is = new BufferedInputStream(session.getInputStream(), READ_BUFFER_SIZE); // 从输入流中读取内容
            Log.d("singleconn", "responding... bufferInputIsNull:" + is.equals(null));

            BufferedOutputStream fs = null;
            int x;
            boolean fileCopying = false;
            boolean endOfLine = false;
            int newLines = 0;
            final int LIMIT = 1460 * 10; // max packet size for tcp over ip,times 10
            byte[] xArr = new byte[LIMIT];
            char[] lineArr = new char[LIMIT];
            int pos = 0;
            Long length = Long.valueOf(session.getHeaders().get("content-length"));
            Log.d("MyServer", "length:" + length + " lengthToMBytes:" + length/1024/1024.0);

//            Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
//            byte[] buffer = new byte[contentLength];  // buffer太大我的哥
//            session.getInputStream().read(buffer, 0, contentLength);
//            Log.d("RequestBody: " + new String(buffer));

            String boundary = session.getHeaders().get("boundary");
            int bytesProcessed = 0;
            int bytesAdded;
            int prevInArray = 0;


            String msg = "";
            // mkdir Upload
            File upload = new File(rootDir+"/Upload");
            if (!upload.exists()) upload.mkdir();
            if (!upload.exists()) upload = new File(rootDir);
            Log.d("MyServer", "MyServer.respondUpload:" + upload + " rootDir:" + rootDir); // MyServer.respondUpload:/storage/emulated/0/Upload rootDir:/storage/emulated/0
            Map<String, String>files = new HashMap<String, String>();
            try {
                session.parseBody(files);
                Set<String> keys = files.keySet();
                for (String key:keys) {
                    String location = files.get(key);
                    File temp = new File(location); // 应分段传输，否则阻塞在内存
                    Log.d("MyServer", "key:" + key);
                    Log.d("MyServer", "temp:" + temp); // /data/user/0/com.wade.webserver/cache/NanoHTTPD--1433460156
                    Log.d("MyServer", "Session:" + session); // com.wade.webserver.NanoHTTPD$HTTPSession@bafd9c0
                    Log.d("MyServer", "Session.getParms:" + session.getParms()); // {Upload=Submit Query, Filedata=a.jpg, Filename=a.jpg}
                    String fileName = session.getParms().get("Filename");
                    Log.d("MyServer", "fileName:" + new String(fileName.getBytes("UTF-8"), "GB2312"));
                    File target = new File(upload.getPath()+"/"+ fileName);
                    Log.d("MyServer", "target:" + target); // /storage/emulated/0/Upload/a.jpg
                    msg += "upload to "+target.getPath();
                    Log.d("MyServer", "msg:" + msg); // upload to /storage/emulated/0/Upload/a.jpg
                    InputStream in = new FileInputStream(temp);
                    OutputStream out = new FileOutputStream(target);
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                        Log.d("MyServer", "");
                    }
                    in.close();
                    out.close();
                }
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }
            catch (ResponseException e1) {
                e1.printStackTrace();
            }
            return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_HTML, msg);
        }
    }

    private Response respond(Map<String, String> headers, IHTTPSession session, String uri) {
        Response r;
        Log.i(TAG, "MyServer.respond("+rootDir+","+uri+") without cors");
        if (uri.startsWith("H")) {
            return respondUpload(session);
        } else {
            return getForbiddenResponse("Won't serve ../ for security reasons.");
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Log.i(TAG, "MyServer.serve("+rootDir+","+uri+")"); // /storage/emulated/0,/

        Map<String, String> headers = session.getHeaders(); // 最重要的是 "host", "http-client-ip" = "remote-addr"

        return respond(Collections.unmodifiableMap(headers), session, uri);
    }
}
