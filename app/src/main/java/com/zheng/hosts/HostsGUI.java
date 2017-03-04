package com.zheng.hosts;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.zheng.hosts.util.CloseUtil;
import com.zheng.hosts.util.DownloadUtil;
import com.stericson.RootShell.RootShell;
import com.stericson.RootTools.RootTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HostsGUI extends AppCompatActivity {
    AlertDialog mDialog;
    Toolbar mToolBar;
    Button mUpdateButton, mGoButton;
    TextView mLocalVersionText, mNetVersionText, mTipsView, mRestoreText;
    ProgressDialog mProgressDialog;
    Handler mHandler = new Handler();

    private boolean isMobileRoot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hosts_gui);

        //在主线程中请求网络操作，将会抛出此异常，“强制使用”
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //设置ToolBar字体颜色
        mToolBar = (Toolbar) findViewById(R.id.toolbar);
        mToolBar.setTitleTextColor(getResources().getColor(R.color.text_color));

        //设置导航图标要在setSupportActionBar方法之后
        setSupportActionBar(mToolBar);
        mToolBar.setNavigationIcon(R.mipmap.ic_launcher);

        mUpdateButton = (Button) findViewById(R.id.update_button);
        mGoButton = (Button) findViewById(R.id.google_button);
        mLocalVersionText = (TextView) findViewById(R.id.local_version);
        mNetVersionText = (TextView) findViewById(R.id.net_version);
        mTipsView = (TextView) findViewById(R.id.tipsView);
        mRestoreText = (TextView) findViewById(R.id.recovery_text);

        //一键更新按钮实现
        mUpdateButton.setOnClickListener( view -> {
            if (!isMobileRoot) {
                Toast.makeText(getContext(), R.string.root_fail_tip, Toast.LENGTH_LONG).show();
                return;
            }

            showProgressDialog();
            DownloadUtil.downloadHostFile(HostsGUI.this, new DownloadUtil.DownloadListener() {
                @Override
                public void success(final File file) {
                    // 需要host
                    AsyncTask.execute( () -> {
                        try {
                            RootShell.getShell(true);
                        } catch (Exception e) {
                            mHandler.post( () -> dismissDialog());
                            e.printStackTrace();
                        }

                        try {
                            RootTools.copyFile(file.getAbsolutePath(), "/system/etc/hosts", true, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mHandler.post( () -> {
                            checkLocalVersion();
                            changeButtonColor();
                            AlertDialog.Builder builder = new AlertDialog.Builder(HostsGUI.this);
                            builder.setTitle(R.string.get_last_host);
                            builder.setMessage(R.string.get_last_host_tip);
                            builder.setCancelable(false);
                            builder.setPositiveButton(R.string.ok_label, (dialog, which)-> {});
                            mDialog = builder.show();
                            dismissDialog();
                        });
                    });
                }

                @Override
                public void error() {
                    mHandler.post( () -> {
                        Toast.makeText(getContext(), R.string.network_error_tip, Toast.LENGTH_LONG).show();
                        dismissDialog();
                    });
                }
            });
        });

        //初始化‘恢复默认’hosts
        mRestoreText.setOnClickListener( view -> {
            if (!isMobileRoot) {
                Toast.makeText(getContext(), R.string.root_fail_tip, Toast.LENGTH_LONG).show();
                return;
            }

            AsyncTask.execute( () -> {
                try {
                    RootShell.getShell(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                RootTools.copyFile(getVoidHostPath(), "/system/etc/hosts", true, false);
                mHandler.post( () -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(HostsGUI.this);
                    builder.setTitle(R.string.restore_host);
                    builder.setMessage(R.string.restore_host_tip);
                    builder.setCancelable(false);
                    builder.setPositiveButton(R.string.ok_label, (dialog, which)-> {});
                    mDialog = builder.show();
                });

            });
        });

        //调用默认浏览器，打开Google网站
        mGoButton.setOnClickListener( view -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri url = Uri.parse("https://www.google.com.hk/");
            intent.setData(url);
            startActivity(intent);
            finish();
        });

        initVoidHost();
        checkMobileIsRoot();
    }

    //判断root权限
    private void checkMobileIsRoot() {
        AsyncTask.execute( () -> {
            isMobileRoot = RootShell.isRootAvailable();
            if (!isMobileRoot) {
                mHandler.post( () -> {
                    mTipsView.setText(getString(R.string.no_root));
                    mLocalVersionText.setText(getString(R.string.can_not_read));
                    mUpdateButton.setEnabled(false);
                    mRestoreText.setEnabled(false);
                });
            }
        });
    }

    //改变按钮颜色
    private void changeButtonColor(){
        if (mLocalVersionText.getText().toString().equals(mNetVersionText.getText().toString())){
            mUpdateButton.setBackgroundResource(R.drawable.update_button);
            mGoButton.setBackgroundResource(R.drawable.new_go_button);
        }else {
            mUpdateButton.setBackgroundResource(R.drawable.new_update_button);
            mGoButton.setBackgroundResource(R.drawable.go_button);
            mGoButton.setEnabled(false);
        }
    }

    //本地版本号显示
    private void checkLocalVersion() {
        try{
            BufferedReader in = new BufferedReader(new FileReader("/system/etc/hosts"));
            int lineCount = 0;
            try{
                while(in.readLine()!=null){
                    lineCount ++;
                    if(lineCount == 1){
                        mLocalVersionText.setText(in.readLine());
                        break;
                    }
                }
                in.close();
            }catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    //网络版本号显示
    private void checkNetVersion() {
        URL url;
        int responseCode;
        HttpURLConnection urlConnection;
        BufferedReader reader;
        try{
            url = new URL("https://coding.net/u/Joshua-Zheng/p/Hosts/git/raw/master/hosts");
            //打开URL
            urlConnection = (HttpURLConnection)url.openConnection();
            //获取服务器响应代码
            responseCode=urlConnection.getResponseCode();
            if(responseCode==200){
                //得到输入流，即获得了网页的内容
                int lineCount = 0;
                reader=new BufferedReader(new InputStreamReader(urlConnection.getInputStream(),"GBK"));
                while(reader.readLine()!=null){
                    lineCount ++;
                    if (lineCount == 1){
                        mNetVersionText.setText(reader.readLine());
                        break;
                    }
                }
            }
            else{
                Toast.makeText(HostsGUI.this, R.string.server_error_tip, Toast.LENGTH_LONG).show();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(getContext());
        }
        mProgressDialog.setTitle("");
        mProgressDialog.setMessage(getString(R.string.loading));
        mProgressDialog.show();
    }

    private void dismissDialog() {
        if (mProgressDialog == null) {
            return;
        }
        mProgressDialog.dismiss();
    }

    public String getRealFileDirPath() {
        File dir = getFilesDir();
        return dir.getAbsolutePath();
    }

    private String getVoidHostPath() {
        return getRealFileDirPath() + File.separator + Constants.VOID_HOST_NAME;
    }

    private Context getContext() {
        return this;
    }

    private void initVoidHost() {
        checkLocalVersion();
        checkNetVersion();
        changeButtonColor();
        File voidHostFile = new File(getVoidHostPath());
        if (voidHostFile.exists()) {
            return;
        }
        AsyncTask.execute( () -> {
            File file = new File(getRealFileDirPath() + File.separator + Constants.VOID_HOST_NAME);
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(file);
                fileWriter.write(Constants.VOID_HOST_VALUE);
                fileWriter.flush();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                CloseUtil.close(fileWriter);
            }
        });
    }

}
