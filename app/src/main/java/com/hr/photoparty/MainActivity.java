package com.hr.photoparty;

import android.Manifest;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.users.FullAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.hr.photoparty.Util.MAX_FREE_UPLOAD_COUNT;
import static com.hr.photoparty.Util.PAYMENT_MADE;
import static com.hr.photoparty.Util.VERSION;

public class MainActivity extends AppCompatActivity {
    public static final int GALLERY_IMAGE  = 21;
    public static final int CAMERA_IMAGE   = 22;
    public static final int MY_PERMISSIONS_REQUEST_READ_STORAGE = 23;
    public static final int MY_PERMISSIONS_REQUEST_READ_STOREAGE_AND_CAMERA = 24;

    public static Handler handler;
    private Button submitPhotoBt;
    private ImageView photoIv;

    private Bitmap selectedBmp;
    DbxClientV2 client;
    FullAccount account;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initValue();
        configureDesign();

        handler = new Handler() {
            public void handleMessage(Message message) {
                if (message.what == PAYMENT_MADE) {
                    updateTitle();
                }
            }
        };
    }

    private void initValue() {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
        client = new DbxClientV2(config, SharedData.getInstance().accessToken);
        new Thread() {
            @Override
            public void run() {
                try {
                    account = client.users().getCurrentAccount();
                } catch (DbxException e) {
                    e.printStackTrace();
                }
                System.out.println(account.getName().getDisplayName());
            }
        }.start();
    }

    private void configureDesign() {
        updateTitle();

        Button selectPhotoBt = findViewById(R.id.button1);
        submitPhotoBt = findViewById(R.id.button2);
        submitPhotoBt.setBackgroundResource(R.drawable.bt_disable_back);

        photoIv = findViewById(R.id.imageView);
        selectPhotoBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(SharedData.getInstance().paid == 0 && SharedData.getInstance().uploadedCount >= MAX_FREE_UPLOAD_COUNT) {
                    Util.showToast("Please purchase for unlimited photo upload", MainActivity.this);
                    return;
                }
                showPhotoSelectDialog();
            }
        });

        submitPhotoBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(SharedData.getInstance().paid == 0 && SharedData.getInstance().uploadedCount >= MAX_FREE_UPLOAD_COUNT) {
                    Util.showToast("Please purchase for unlimited photo upload", MainActivity.this);
                    return;
                }

                if(selectedBmp == null) {
                    Util.showToast("Please select image", MainActivity.this);
                    return;
                }

                final String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                Date date = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
                final String dateStr = dateFormat.format(date);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                selectedBmp.compress(Bitmap.CompressFormat.JPEG, 100/*ignored for PNG*/, bos);
                byte[] bitmapdata = bos.toByteArray();
                final ByteArrayInputStream bs = new ByteArrayInputStream(bitmapdata);
                Util.showProgressDialog("Uploading..", MainActivity.this);
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            client.files().uploadBuilder(String.format("/%s-%s.jpeg", deviceId, dateStr)).uploadAndFinish(bs);
                            MainActivity.this.runOnUiThread(new Runnable() {
                                public void run() {
                                    Util.hideProgressDialog();
                                    Util.showToast("Photo is uploaded successfully!", MainActivity.this);
                                    updateUploadedPhotoCount();
                                }
                            });
                        } catch (DbxException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        });
    }

    private void updateTitle() {
        String title;
        if(SharedData.getInstance().paid == 0) {
            if(SharedData.getInstance().uploadedCount == 1) {
                title = String.format("Photo Party (%d/%d PHOTO)", SharedData.getInstance().uploadedCount, MAX_FREE_UPLOAD_COUNT);
            }
            else {
                title = String.format("Photo Party (%d/%d PHOTOS)", SharedData.getInstance().uploadedCount, MAX_FREE_UPLOAD_COUNT);
            }
        }
        else {
            if(SharedData.getInstance().uploadedCount == 1) {
                title = (String.format("Photo Party (%d PHOTO)", SharedData.getInstance().uploadedCount));
            }
            else {
                title = (String.format("Photo Party (%d PHOTOS)", SharedData.getInstance().uploadedCount));
            }
        }
        getSupportActionBar().setTitle(Html.fromHtml(String.format("<small>%s</small>", title)));
    }

    private void updateUploadedPhotoCount() {
        APIManager.getInstance().setCallback(new APIManagerCallback() {
            @Override
            public void APICallback(JSONObject objAPIResult) {
                Util.hideProgressDialog();
                if(objAPIResult == null) {
                    return;
                }
                try {
                    if(objAPIResult.getBoolean("Success")) {
                        SharedData.getInstance().uploadedCount = objAPIResult.getInt("UploadedCount");
                        updateTitle();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        JSONObject object = new JSONObject();
        try {
            object.accumulate("UploadedCount", SharedData.getInstance().uploadedCount);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        APIManager.getInstance().updateUploadedCount(object);
    }

    private void showPhotoSelectDialog() {
        boolean permission1 = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            permission1 = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        boolean permission2 = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            permission2 = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }

        if(permission1 && permission2) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + "image.jpg");
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
            intent.putExtra("return-data", true);
            startActivityForResult(intent, CAMERA_IMAGE);
        }
        else {
            Util.showToast("You need to set permissions", MainActivity.this);
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_READ_STOREAGE_AND_CAMERA);
        }
    }

    @Override
    public void onBackPressed() {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == -1) {
            submitPhotoBt.setEnabled(true);
            submitPhotoBt.setBackgroundResource(R.drawable.bt_back);
            if(requestCode == GALLERY_IMAGE) {
                Uri picUri = data.getData();
                selectedBmp = Util.getThumbnail(picUri, MainActivity.this);
                photoIv.setImageBitmap(selectedBmp);
            }
            else {
                File file = new File(Environment.getExternalStorageDirectory()+File.separator + "image.jpg");
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
                try {
                    ExifInterface ei = new ExifInterface(file.getAbsolutePath());
                    bitmap = Util.getResizedBitmap(bitmap, 640);
                    int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            bitmap = Util.RotateBitmap(bitmap, 90);
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            bitmap = Util.RotateBitmap(bitmap, 180);
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            bitmap = Util.RotateBitmap(bitmap, 270);
                            break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                file.delete();
                Util.deleteFileFromMediaStore(getContentResolver(), file);
                selectedBmp = bitmap;
                photoIv.setImageBitmap(selectedBmp);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_setting, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.setting:
                Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
