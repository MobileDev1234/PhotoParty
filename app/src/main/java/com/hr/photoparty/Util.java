package com.hr.photoparty;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * Created by RabbitJang on 10/13/2018.
 */

public class Util {
    public static float VERSION = 1.0f;
    public static int MAX_FREE_UPLOAD_COUNT = 100;
    public static final int PAYMENT_MADE  = 1000;

    public static Toast toast = null;
    public static ProgressDialog progressDlg = null;

    public static void showToast(String st, Context theContext){ //"Toast toast" is declared in the class
        if (toast == null || toast.getView().getWindowVisibility() != View.VISIBLE) {
            toast = Toast.makeText(theContext, st, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    public static void showProgressDialog(String titleStr, Context context)  {
        progressDlg = new ProgressDialog(context, R.style.AppDialogTheme);
        progressDlg.setIndeterminate(false);
        progressDlg.setCancelable(false);
        progressDlg.setMessage(titleStr);
        progressDlg.show();
    }

    public static void hideProgressDialog() {
        if(progressDlg != null)
            progressDlg.dismiss();
    }

    public static Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }

        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static String getRealPathFromURI(final Context context, final Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static Bitmap getThumbnail(Uri uri, Activity activity) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), uri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String path = Util.getRealPathFromURI(activity, uri);
        try {
            ExifInterface ei = new ExifInterface(path);
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
        return bitmap;
    }


    public static void deleteFileFromMediaStore(final ContentResolver contentResolver, final File file) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (sdk >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            String canonicalPath;
            try {
                canonicalPath = file.getCanonicalPath();
            } catch (IOException e) {
                canonicalPath = file.getAbsolutePath();
            }
            final Uri uri = MediaStore.Files.getContentUri("external");
            final int result = contentResolver.delete(uri,
                    MediaStore.Files.FileColumns.DATA + "=?", new String[]{canonicalPath});
            if (result == 0) {
                final String absolutePath = file.getAbsolutePath();
                if (!absolutePath.equals(canonicalPath)) {
                    contentResolver.delete(uri,
                            MediaStore.Files.FileColumns.DATA + "=?", new String[]{absolutePath});
                }
            }
        }
    }
}
