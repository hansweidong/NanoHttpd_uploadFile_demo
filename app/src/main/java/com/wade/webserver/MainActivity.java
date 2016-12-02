package com.wade.webserver;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextView = (TextView)findViewById(R.id.msg_view);
        mTextView.setText("Serve at http://" + Utils.getIPAddress(true) + ":" + MyServer.PORT + "\n" +
                "Root Dir: " + MyServer.rootDir + "\n");
        Intent intent = new Intent(MainActivity.this, MyServer.class);
        startService(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        mTextView = (TextView)findViewById(R.id.msg_view);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
