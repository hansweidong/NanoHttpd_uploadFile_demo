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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
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

            /**
             * 定长头217
             * 979249(11) - 979021(11) = 228
             * 117944(20) -  117707(20) = 237
             * content.length - 217 - filename.length
             * 需要增加外存空间大小满足,判断,参考FileOperationUtils.java
             * 需要处理连接超时异常 : java.net.SocketException: recvfrom failed: ETIMEDOUT (Connection timed out)
             */
            // {remote-addr=192.168.203.251, content-length=43798720, host=192.168.220.58:8080, http-client-ip=192.168.203.251,
            // user-agent=Mozilla/4.0, pragma=no-cache, content-type=multipart/form-data; boundary=---------------------------baofengupload}
            Log.d("singleconn", "session.headers:" + session.getHeaders());

            // 获取boundary
            int boundaryIndex = session.getHeaders().toString().indexOf("boundary=") + "boundary=".length();
            String boundary = session.getHeaders().toString().substring(boundaryIndex, boundaryIndex + 40); // boundary内容40
            Log.d("BOUDNARY", boundary);

            Log.d("singleconn", "session.headers.keyset:" + session.getHeaders().toString().indexOf("boundary=")); // 186
            Log.d("singleconn", "session.remote:" + session.getHeaders().get("remote-addr")); // 192.168.203.251
            Log.d("singleconn", "session.length:" + session.getHeaders().get("content-length")); // 979249
            Log.d("singleconn", "session.host:" + session.getHeaders().get("host")); // 192.168.220.58:8080
            Log.d("singleconn", "session.http-client:" + session.getHeaders().get("http-client-ip")); // 192.168.203.251
            Log.d("singleconn", "session.user-agent:" + session.getHeaders().get("user-agent")); // Mozilla/4.0
            Log.d("singleconn", "session.pragma:" + session.getHeaders().get("pragma")); // no-cache
            Log.d("singleconn", "session.content-type:" + session.getHeaders().get("content-type")); // multipart/form-data; boundary=---------------------------baofengupload
            Log.d("singleconn", "session.boundary:" + session.getHeaders().get("boundary")); // null
            Log.d("singleconn", "session.getParms:" + session.getParms()); // {}
            Log.d("singleconn", "session.getUri:" + session.getUri());// HTTP/1.1

            final int READ_BUFFER_SIZE = 512*1024; // 0.5 MB
//            BufferedInputStream is = new BufferedInputStream(session.getInputStream(), READ_BUFFER_SIZE); // 从输入流中读取内容;
//            BufferedOutputStream fs = null;
//            String fileName = "";

            int x;
            boolean fileCopying = false;
            boolean endOfLine = false;
            int newLines = 0;
            final int LIMIT = 1460 * 10; // max packet size for tcp over ip,times 10
            byte[] xArr = new byte[LIMIT];
            char[] lineArr = new char[LIMIT];
            int pos = 0;

//            Long length = Long.valueOf(session.getHeaders().get("content-length"));
//            Log.d("MyServer", "length:" + length + " lengthToMBytes:" + length/1024/1024.0);

//            Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
//            byte[] buffer = new byte[contentLength];  // buffer太大我的哥
//            session.getInputStream().read(buffer, 0, contentLength);

            int bytesProcessed = 0;
            int bytesAdded;
            int prevInArray = 0;

//            while (bytesProcessed < length) {
//                try {
//                    bytesAdded = is.read(xArr, prevInArray, xArr.length - prevInArray);
//                    int validInArray =  prevInArray + bytesAdded;
//                    bytesProcessed += bytesAdded;
//                } catch (IOException e) {
//                    Log.d("singleconn", "l-w:" + (xArr.length - prevInArray) + " w:" + prevInArray);
//                    e.printStackTrace();
//                }
//
//            }


            String msg = "";
            // mkdir Upload
            File upload = new File(rootDir+"/Upload");
            if (!upload.exists()) upload.mkdir();
            if (!upload.exists()) upload = new File(rootDir);
            Log.d("MyServer", "MyServer.respondUpload:" + upload + " rootDir:" + rootDir); // MyServer.respondUpload:/storage/emulated/0/Upload rootDir:/storage/emulated/0
            Map<String, String>files = new HashMap<String, String>();
            try {
                session.parseBody(files); // Adds the files in the request body to the files map. 阻塞cache!!!!实际下载进度在此,绕不过
                Map<String, String> parms = session.getParms();
                Log.d("PARMS", "2parms:" + parms);
                Set<String> keys = files.keySet();
                for (String str: keys) {
                    Log.d("MyServer", "SetKey:" + str); //Filedata
                }
                for (String key:keys) {
                    String location = files.get(key);
                    File temp = new File(location);
                    String afileName = session.getParms().get("Filename");
                    Log.d("FINEMLENGTH", "afileName.length:" + afileName.length());
//                    Log.d("MyServer", "1key:" + key); // Filedata
//                    Log.d("MyServer", "1temp:" + temp); // /data/user/0/com.wade.webserver/cache/NanoHTTPD--1433460156
//                    Log.d("MyServer", "1Session:" + session); // com.wade.webserver.NanoHTTPD$HTTPSession@bafd9c0
//                    Log.d("MyServer", "1Session.getParms:" + session.getParms()); // {Upload=Submit Query, Filedata=a.jpg, Filename=a.jpg}
                    File target = new File(upload.getPath()+"/"+ afileName);
//                    Log.d("MyServer", "1target:" + target); // /storage/emulated/0/Upload/a.jpg
                    msg += "upload to "+target.getPath();
//                    Log.d("MyServer", "1msg:" + msg); // upload to /storage/emulated/0/Upload/a.jpg


                    // 依然不能处理2G以上文件,文件通道按块读取
//                    FileChannel fcin = new FileInputStream(temp).getChannel();
//                    FileChannel fcout = new FileOutputStream(target).getChannel();
//                    final long blockSize = Math.min(Long.MAX_VALUE, fcin.size());
//                    long position = 0;
//                    while (fcout.transferFrom(fcin, position, blockSize) > 0) {
//                        position += blockSize;
//                    }
//                    System.out.println("文件大小:"+fcin.size());
////                  //fcin.transferTo(0, fcin.size(), fcout);
////                    ByteBuffer bb = ByteBuffer.allocate(1024);
////                    while (fcin.read(bb)!=-1){
////                        bb.flip();
////                        fcout.write(bb);
////                        bb.clear();//prepare for reading;清空缓冲
////                    }
//                    fcin.close();
//                    fcout.close();


                    // master 部分读写文件代码,测试失败,可能有其他???
//                    InputStream in = new FileInputStream(temp);
//                    OutputStream out = new FileOutputStream(target);
//                    BufferedInputStream inb = new BufferedInputStream(in);
//                    BufferedOutputStream outb = new BufferedOutputStream(out);
//                    byte[] read = new byte[8192];
//                    int len;
//                    while ((len = in.read(read)) != -1) {
//                        outb.write(read, 0, len);
//                        Log.d("MyServer", "len:" + len);
//                    }
//                    in.close();
//                    inb.close();
//                    out.close();
//                    outb.close();


//                    // 尝试分段读写,失败
//                    InputStream in = new FileInputStream(temp);
//                    OutputStream out = new FileOutputStream(target);
//                    byte[] buf = new byte[8192];
//                    int splitByte = 0;
//                    int rlen = 0;
//                    {
//                        int read = in.read(buf, 0, 8192);
//                        while (read > 0) {
//                            rlen += read;
//                            splitByte = findHeadrEnd(buf, rlen);
//                            out.write(buf, 0, read);
//                            if (splitByte > 0)
//                                break;
//                            read = in.read(buf, rlen, 8192-rlen);
//                        }
//                    }
//                    in.close();
//                    out.close();


                    // 不支持大文件传输 parseBody
                    //: java.io.IOException: mmap failed: ENOMEM (Out of memory)
                    //   at java.nio.MemoryBlock.mmap(MemoryBlock.java:125)
                    //   at java.nio.FileChannelImpl.map(FileChannelImpl.java:257)

                    InputStream in = new FileInputStream(temp);
                    OutputStream out = new FileOutputStream(target);
//
//                    /**
//                     * 需要支持2G以上文件传输
//                     */
//
                    // XF部分代码
                    byte[] buf = new byte[1024 * 8];
                    int len;
//                    int count;
//                    try {
//                        while ((count = in.read(buf)) != -1) {
//                            out.write(buf, 0, count);
//                        }
//                        out.flush();
//                    } finally {
//                        in.close();
//                        out.close();
//                    }

//                    BufferedInputStream inb = new BufferedInputStream(in);
                    BufferedOutputStream outb = new BufferedOutputStream(out);
//                    while ((len = in.read(read)) != -1) {
//                        outb.write(read, 0, len);

                    //原始
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
//                        Log.d("MyServer", "len:" + len + " out.length:" + out.toString().length());  // 8192, 32
                    }
                    in.close();
//                    inb.close();
                    out.close();
                    outb.close();
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

    /**
     * 分段方式
     * @param buf
     * @param rlen
     * @return
     */
    private int findHeadrEnd(byte[] buf, int rlen) {
        int splitbyte = 0;
        while (splitbyte + 3 < rlen) {
            if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
                return splitbyte + 4;
            }
            splitbyte++;
        }
        return 0;
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
