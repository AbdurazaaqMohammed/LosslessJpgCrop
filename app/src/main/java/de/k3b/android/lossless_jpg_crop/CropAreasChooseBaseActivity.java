/*
Copyright (C) 2019-2023 by k3b

This file is part of de.k3b.android.lossless_jpg_crop (https://github.com/k3b/losslessJpgCrop/)

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see <http://www.gnu.org/licenses/>
 */
package de.k3b.android.lossless_jpg_crop;

import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.tomg.exifinterfaceextended.ExifInterfaceExtended;

import net.realify.lib.androidimagecropper.CropImageView;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.k3b.util.FileUtils;
import de.k3b.util.TempFileUtil;

/**
 * For all different workflows CropAreaXxxxActivity:
 * * Displays the cropping gui
 * * Contains Protected helpers for common functionalities
 */
abstract class CropAreasChooseBaseActivity extends BaseActivity implements DefineAspectRatioFragment.AspectRatioHandler {
    protected static final String TAG = "LLCrop";
    protected static final boolean LOAD_ASYNC = false;
    private static final boolean ENABLE_ASPECT_RATIO = true;

    // keys for persisted states
    private static final String KEY_CURRENT_CROP_AREA = "CURRENT_CROP_AREA";
    private static final String KEY_CURRENT_ASPECT_RATIO = "CURRENT_ASPECT_RATIO";
    private static final String KEY_ASPECT_RATIO_DEFINITIONS = "ASPECT_RATIO_DEFINITIONS";
    public static final int MAX_COUNT_ASPECT_RATIO_DEFINITIONS = 15;

    private static final String ASPECT_RATIO_SQUARE = "8x8";
    public static final int SIZE_MIN = 40;
    public static final int SIZE_MAX = 99999;
    public static final int SIZE_ABSOLUTE_TRESHHOLD = 100;

    private static int lastInstanceNo4Debug = 0;
    private int instanceNo4Debug = 0;

    private final int idMenuMainMethod;
    private final Map<Integer, Integer> menu2Rotation;

    protected static final String IMAGE_JPEG_MIME = "image/jpeg";

    protected CropImageView uCropView = null;
    protected TextView txtStatus = null;
    private ImageProcessor mSpectrum;

    /** Same as last selected menu item text i.e. 9x13 */
    private String currentAspectRatioString = null;

    private List<String> currentAspectRatioDefinitions = null;

    private int rotationBeforeCrop = 0;
    private int rotationAfterCrop = 0;

    // #7: workaround rotation change while picker is open causes Activity re-create without
    // uCropView recreation completed.
    private Rect mLastCropRect = null;

    protected CropAreasChooseBaseActivity(int idMenuMainMethod) {
        this.idMenuMainMethod = idMenuMainMethod;
        menu2Rotation = new HashMap<>();
        menu2Rotation.put(R.id.menu_rotate_0, 0);
        menu2Rotation.put(R.id.menu_rotate_90, 90);
        menu2Rotation.put(R.id.menu_rotate_180, 180);
        menu2Rotation.put(R.id.menu_rotate_270, 270);
    }

    protected class Content {
        protected static final int REQUEST_GET_CONTENT_PICTURE = 1;
        protected static final int REQUEST_GET_CONTENT_PICTURE_PERMISSION = 101;

        protected void pickFromGalleryForContent() {
            pickFromGallery(Content.REQUEST_GET_CONTENT_PICTURE, Content.REQUEST_GET_CONTENT_PICTURE_PERMISSION);
        }
        protected boolean returnPrivateCroppedImage() {
            Uri outUri = cropToSharedUri();

            if (outUri != null) {
                Intent result = new Intent();
                result.setDataAndType(outUri, IMAGE_JPEG_MIME);
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                setResult(Activity.RESULT_OK, result);
                finish();
                return true;
            }
            return false;
        }

    }
    protected Content content = new Content();

    protected class Edit {
        protected static final int REQUEST_GET_EDIT_PICTURE = 3;
        protected static final int REQUEST_GET_EDIT_PICTURE_PERMISSION = 103;

        protected static final int REQUEST_SAVE_EDIT_PICTURE_AS = 2;
        protected static final int REQUEST_SAVE_EDIT_PICTURE_PERMISSION = 102;

        protected void pickFromGalleryForEdit() {
            pickFromGallery(Edit.REQUEST_GET_EDIT_PICTURE, Edit.REQUEST_GET_EDIT_PICTURE_PERMISSION);
        }

        protected boolean saveAsPublicCroppedImage() {
//            if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                    != PackageManager.PERMISSION_GRANTED) {
//                requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                        getString(R.string.permission_write_storage_rationale),
//                        Edit.REQUEST_SAVE_EDIT_PICTURE_PERMISSION);
//            } else
                edit.openPublicOutputUriPicker(Edit.REQUEST_SAVE_EDIT_PICTURE_AS);

            return true;
        }

        protected boolean openPublicOutputUriPicker(int folderpickerCode) {
            String proposedFileName = createCropFileName();
            // String proposedOutPath = inUri.getP new Uri() replaceExtension(originalFileName, "_llcrop.jpg");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType(IMAGE_JPEG_MIME)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, proposedFileName)
                        .setFlags(FLAG_GRANT_WRITE_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    intent.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.label_save_as));
                }
                Log.d(TAG, getInstanceNo4Debug() + "openPublicOutputUriPicker '" + proposedFileName + "'");

                startActivityForResult(intent, folderpickerCode);
            } else {
                File parent = new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES);
                parent.mkdir();
                File file = new File(parent, proposedFileName);
                try(OutputStream os = new FileOutputStream(file)) {
                    edit.saveEditPictureToOutputStream(os);
                    Toast.makeText(getBaseContext(),
                            getString(R.string.toast_saved_as, Environment.DIRECTORY_PICTURES + '/' + file.getName()),
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            return true;
        }
        protected void onGetEditPictureResult(int resultCode, Intent data) {
            if (resultCode == RESULT_OK) {
                final Uri selectedUri = (data == null) ? null : (data.getData());
                if (selectedUri != null) {
                    Log.d(TAG, getInstanceNo4Debug() + "Restarting with uri '" + selectedUri + "'");

                    Intent intent = new Intent(Intent.ACTION_EDIT, selectedUri, getBaseContext(), CropAreasEditActivity.class);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivity(intent);
                    finishIfMainMethod(R.id.menu_save);
                    return;
                }
            }
            Log.d(TAG, getInstanceNo4Debug() +  getString(R.string.toast_cannot_retrieve_selected_image));
            Toast.makeText(getBaseContext(), R.string.toast_cannot_retrieve_selected_image, Toast.LENGTH_SHORT).show();
            finishIfMainMethod(R.id.menu_save);
        }

        protected void saveEditPictureToOutputStream(OutputStream outStream) {

            // use to provoke an error to test error handling
            // Uri outUri = Uri.parse(outUri + "-err");

            final Uri inUri = getSourceImageUri(getIntent());

            if (inUri != null) {
                Rect rect = getCropRect();
//                final String context_message = getInstanceNo4Debug() + "Cropping '" + inUri + "'(" + rect + ") => '"
//                        + outUri + "' ('" + asString(outUri) + "')";
//                Log.i(TAG, context_message);
                try (InputStream inStream = getContentResolver().openInputStream(inUri)) {
                    if(PreferenceManager
                            .getDefaultSharedPreferences(getApplicationContext())
                            .getBoolean("remove-exif", false)) {
                        File tempImg = new File(getCacheDir(), "t.jpg");
                        try(OutputStream temp = new FileOutputStream(tempImg)) {
                            crop(inStream, temp, rect);
                            try(InputStream is = FileUtils.getInputStream(tempImg)) {
                                new ExifInterfaceExtended(tempImg).saveExclusive(is, outStream, true);
                            }
                            tempImg.delete();
                        }
                    } else crop(inStream, outStream, rect);

//                    String message = getString(R.string.toast_saved_as,
//                            asString(outUri));
//                    Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();

                    finishIfMainMethod(R.id.menu_save);
                } catch (Exception e) {
                    close(outStream, outStream);

                    // Log.e(TAG, "Error " + context_message + e.getMessage(), e);

//                    Log.e(TAG, "Error " + context_message + "(" + outUri +") => " + e.getMessage(), e);
                    Toast.makeText(getBaseContext(),
                            //getString(R.string.toast_saved_error, asString(outUri),
                            e.getMessage()
                            // )
                            ,
                            Toast.LENGTH_LONG).show();
                } finally {
                    close(outStream, outStream);
                }
            } else {
                // outUri==null or error
                Log.i(TAG, getInstanceNo4Debug() + "onOpenPublicOutputUriPickerResult(null): No output url, not saved.");
            }
        }

        protected void onSaveEditPictureAsOutputUriPickerResult(Uri outUri) {

            // use to provoke an error to test error handling
            // Uri outUri = Uri.parse(outUri + "-err");

            final Uri inUri = getSourceImageUri(getIntent());

            if (inUri != null) {
//                final String context_message = getInstanceNo4Debug() + "Cropping '" + inUri + "'(" + rect + ") => '"
//                        + outUri + "' ('" + asString(outUri) + "')";
//                Log.i(TAG, context_message);

                try (OutputStream outStream = getContentResolver().openOutputStream(outUri)) {
                    saveEditPictureToOutputStream(outStream);
//                    String message = getString(R.string.toast_saved_as,
//                            asString(outUri));
//                    Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();

                    finishIfMainMethod(R.id.menu_save);
                } catch (Exception e) {
                   // Log.e(TAG, "Error " + context_message + e.getMessage(), e);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) try {
                        // #14: delete affected file as it is useless
                        DocumentsContract.deleteDocument(getContentResolver(), outUri);
                    } catch (Exception exDelete) {
                        // ignore if useless file cannot be deleted
                    }
//                    Log.e(TAG, "Error " + context_message + "(" + outUri +") => " + e.getMessage(), e);
                    Toast.makeText(getBaseContext(),
                            //getString(R.string.toast_saved_error, asString(outUri),
                                    e.getMessage()
                           // )
                            ,
                            Toast.LENGTH_LONG).show();

                }
            } else {
                // outUri==null or error
                Log.i(TAG, getInstanceNo4Debug() + "onOpenPublicOutputUriPickerResult(null): No output url, not saved.");
            }
        }
    }
    protected Edit edit = new Edit();

    protected class Send {
        protected void onGetSendImage(Uri imageUri, Bundle savedInstanceState) {
            SetImageUriAndLastCropArea(imageUri, savedInstanceState);
        }

        protected boolean sendPrivateCroppedImage() {
            Uri outUri = cropToSharedUri();

            if (outUri != null) {
                boolean isSend = true;//isSendAction(); ???

                Intent childSend = new Intent();

                if (isSend) {
                    childSend
                            .setAction(Intent.ACTION_SEND)
                            .putExtra(Intent.EXTRA_STREAM, outUri)
                            .setType(IMAGE_JPEG_MIME);
                } else {
                    childSend
                            .setAction(Intent.ACTION_SENDTO)
                            .setDataAndType(outUri, IMAGE_JPEG_MIME);
                }

                childSend.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                copyExtra(childSend, getIntent().getExtras(),
                        Intent.EXTRA_EMAIL, Intent.EXTRA_CC, Intent.EXTRA_BCC, Intent.EXTRA_SUBJECT,
                        Intent.EXTRA_TEXT);


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = ClipData.newUri(getContentResolver(), outUri.toString(), outUri);
                    childSend.setClipData(clip);
                }

                final Intent execIntent = Intent.createChooser(childSend, getText(R.string.label_send));

                startActivity(execIntent);

                finishIfMainMethod(R.id.menu_send);
                return true;
            }
            return false;
        }

        private boolean isSendAction() {
            Intent i = getIntent();
            String action = (i != null) ? i.getAction() : null;
            return Intent.ACTION_SEND.equalsIgnoreCase(action);
        }

        private void copyExtra(Intent outIntent, Bundle extras, String... extraIds) {
            if (extras != null) {
                for (String id : extraIds) {
                    String value = extras.getString(id, null);
                    if (value != null) outIntent.putExtra(id, value);
                }
            }
        }
    }
    protected Send send = new Send();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instanceNo4Debug = ++lastInstanceNo4Debug;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);
        uCropView = findViewById(R.id.ucrop);
        txtStatus = findViewById(R.id.status);

        if (currentAspectRatioDefinitions == null) {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(this.getApplicationContext());

            String aspectRatioDefinitions = prefs.getString(KEY_ASPECT_RATIO_DEFINITIONS, null);
            if (aspectRatioDefinitions != null) {
                currentAspectRatioDefinitions = new ArrayList<>(Arrays.asList(aspectRatioDefinitions.split(";")));
            }
        }

        if (savedInstanceState != null) {
            currentAspectRatioString = savedInstanceState.getString(KEY_CURRENT_ASPECT_RATIO, currentAspectRatioString);
        }
        mSpectrum = new ImageProcessor();

        uCropView.setOnSetCropOverlayMovedListener(rect -> onUpdateCropping());

        setAspectRatio(currentAspectRatioString);
    }

    private void onUpdateCropping() {
        Rect cropRect = getCropRect();
        if (cropRect != null) {
            String cropInfo = toString(cropRect);
            txtStatus.setText(cropInfo);
            Log.d(TAG, getInstanceNo4Debug() + "onUpdateCropping(crop=" + cropInfo + ")");
        }
    }

    public String toString(Rect r) {
        StringBuilder sb = new StringBuilder();

                sb
                .append('(').append(r.left).append(',').append(r.top).append(") .. (")
                .append(r.right).append(',').append(r.bottom).append(") => [")
                        .append(r.width()).append('x').append(r.height()).append(']');

                if (this.currentAspectRatioString != null) {
                    sb.append("   {").append(currentAspectRatioString).append("}");
                }
        return sb.toString();
    }

    protected void finishIfMainMethod(int idMenuMainMethod) {
        if (this.idMenuMainMethod == idMenuMainMethod) {
            finish();
        }
    }

    protected void SetImageUriAndLastCropArea(Uri imageUri, Bundle savedInstanceState) {
        final Rect crop = (savedInstanceState == null)
                ? null
                : savedInstanceState.getParcelable(KEY_CURRENT_CROP_AREA);

        SetImageUriAndLastCropArea(imageUri, crop);
    }

    protected void SetImageUriAndLastCropArea(Uri imageUri, Rect crop) {
        try {
            if (LOAD_ASYNC) {
                uCropView.setImageUriAsync(imageUri);
            } else {
                InputStream stream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                ExifInterface exif = getExif(this, imageUri);
                uCropView.setImageBitmap(bitmap, exif);
                if (exif != null) {
                    setRotationBeforeCrop(exif.getRotationDegrees());
                }
            }
            setCropRect(crop);
            

        } catch (Exception e) {
            final String msg = getInstanceNo4Debug() + "SetImageUriAndLastCropArea '" + imageUri + "' ";
            Log.e(TAG, msg, e);
            Toast.makeText(this, msg + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static ExifInterface getExif(Context context, Uri imageUri) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(imageUri);
            if (is != null) {
                ExifInterface ei = new ExifInterface(is);
                is.close();
                return ei;
            }
        } catch (Exception ignored) {
        } finally {
            saveClose(is);
        }
        return null;
    }

    private static void saveClose(Closeable is) {
        if (is != null) {
            try {
                is.close();
            } catch (Exception ignore) {
            }
        }
    }

    protected Uri getSourceImageUri(Intent intent) {
        if (intent == null) return null;

        return intent.getData();
    }

    private void setCropRect(final Rect crop) {
        if (crop != null) {
            // #7: workaround rotation change while picker is open causes Activity re-create without
            // uCropView recreation completed.
            mLastCropRect = crop;

            uCropView.setCropRect(crop);

            if (LOAD_ASYNC) {
                uCropView.setOnSetImageUriCompleteListener((view, imageUri, error) -> {
                    // called when uCropView recreation is completed.
                    uCropView.setCropRect(crop);
                    Rect newCrop = getCropRect();
                    Log.d(TAG, getInstanceNo4Debug() + "delayed onCreate(): crop=" + crop + "/" + newCrop);
                    uCropView.setOnSetImageUriCompleteListener(null);

                    setRotationBeforeCrop(uCropView.getRotatedDegrees());
                });
            }
        }
    }

    protected Rect getCropRect() {
        if (uCropView == null) {
            Log.e(TAG, getInstanceNo4Debug() + "ups: no cropView");
            return null;
        }
        final Rect cropRect = uCropView.getCropRect();

        // #7: workaround rotation change while picker is open causes Activity re-create without
        // uCropView recreation completed.
        return (cropRect != null) ? cropRect : mLastCropRect;
    }

    protected String getInstanceNo4Debug() {
        return "#" + instanceNo4Debug + ":";
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Rect crop = getCropRect();
        Log.d(TAG, getInstanceNo4Debug() + "onSaveInstanceState : crop=" + crop);
        outState.putParcelable(KEY_CURRENT_CROP_AREA, crop);
        outState.putString(KEY_CURRENT_ASPECT_RATIO, currentAspectRatioString);
    }

    private void pickFromGallery(int requestId, int requestPermissionId) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
//                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//            requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE,
//                    getString(R.string.permission_read_storage_rationale),
//                    requestPermissionId);
//        }
//        else
        {
            //Log.d(TAG, getInstanceNo4Debug() + "Opening Image Picker");
            Intent intent = new Intent(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT ?
                    Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT)
                    .setType(IMAGE_JPEG_MIME)
                    .putExtra(Intent.EXTRA_TITLE, getString(R.string.label_select_picture))
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    ;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                intent.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.label_select_picture));
            }

            startActivityForResult(Intent.createChooser(intent, getString(R.string.label_select_picture)), requestId);
        }
    }

    protected String asString(Uri outUri) {
        if (outUri == null) return "";
        // may crash with "IllegalCharsetNameException" in https://github.com/k3b/LosslessJpgCrop/issues/7
        try {
            return URLDecoder.decode(outUri.toString(), Charset.forName("UTF-8").toString());
        } catch (Exception e) {
            // UnsupportedEncodingException, IllegalCharsetNameException
            Log.e(TAG, getInstanceNo4Debug() + "err cannot convert imageUri to string('" + outUri + "').", e);
            return outUri.toString();
        }
    }

    protected void close(Closeable stream, Object source) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                Log.w(TAG, getInstanceNo4Debug() + "Error closing " + source, e);
            }
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case Content.REQUEST_GET_CONTENT_PICTURE_PERMISSION:
                    content.pickFromGalleryForContent();
                    return;
                case Edit.REQUEST_GET_EDIT_PICTURE_PERMISSION:
                    edit.pickFromGalleryForEdit();
                    return;
                case Edit.REQUEST_SAVE_EDIT_PICTURE_PERMISSION:
                    edit.saveAsPublicCroppedImage();
                    return;
            }
        } else {
            Log.i(TAG, this.getClass().getSimpleName()
                    + ": " + getText(R.string.permission_error));
            Toast.makeText(this, R.string.permission_error, Toast.LENGTH_LONG).show();
            setResult(Activity.RESULT_CANCELED, null);
            finish();

        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Edit.REQUEST_GET_EDIT_PICTURE) {
            edit.onGetEditPictureResult(resultCode, data);
            return;
        }

        if (requestCode == Edit.REQUEST_SAVE_EDIT_PICTURE_AS) {
            if (resultCode == RESULT_OK) {
                final Uri outUri = (data == null) ? null : data.getData();
                edit.onSaveEditPictureAsOutputUriPickerResult(outUri);
            } else finishIfMainMethod(R.id.menu_save);
            return;
        }

        if (requestCode == Content.REQUEST_GET_CONTENT_PICTURE) {
            final Uri inUri = (data == null) ? null : data.getData();
            if ((resultCode == RESULT_OK) && (inUri != null)) {
                SetImageUriAndLastCropArea(inUri, getCropRect());
            } else {
                finishIfMainMethod(R.id.menu_get_content);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void crop(InputStream inStream, OutputStream outStream, Rect rect) throws IOException {
        int relativeRotation = (360 + getRotationAfterCrop() - getRotationBeforeCrop()) % 360;
        this.mSpectrum.crop(inStream, outStream, rect, relativeRotation);
    }

    protected File getSharedDir() {
        File sharedDir = new File(this.getFilesDir(), "shared");
        sharedDir.mkdirs();

        // #11: remove unused temporary crops from send/get_content after some time.
        TempFileUtil.removeOldTempFiles(sharedDir, System.currentTimeMillis());
        return sharedDir;
    }

    protected String createCropFileName() {
        Uri inUri = getSourceImageUri(getIntent());
        String originalFileName = (inUri == null) ? "" : inUri.getLastPathSegment();
        originalFileName = TempFileUtil.getLastPath(originalFileName);
        return replaceExtension(originalFileName, TempFileUtil.TEMP_FILE_SUFFIX);
    }

    /** replaceExtension("/path/to/image.jpg", ".xmp") becomes "/path/to/image.xmp" */
    private static String replaceExtension(String path, String extension) {
        if (path == null) return null;
        int ext = path.lastIndexOf(".");
        return ((ext >= 0) ? path.substring(0, ext) : path) + extension;
    }

    /** crops current jpg to new temp file and return a FileProvider-shareUri for it or null if error. */
    protected Uri cropToSharedUri() {
        Uri outUri = null;

        final Uri inUri = getSourceImageUri(getIntent());

        File outFile = new File(getSharedDir(), createCropFileName());

        if (inUri != null) {
            Rect rect = getCropRect();

            final String context_message = getInstanceNo4Debug() + "Cropping '" + inUri + "'(" + rect + ") => '"
                    + outFile.getName() + " ";
            Log.i(TAG, context_message);

            try (InputStream inStream = getContentResolver().openInputStream(inUri);
                 OutputStream outStream = FileUtils.getOutputStream(outFile)) {
                crop(inStream, outStream, rect);
                if(PreferenceManager
                        .getDefaultSharedPreferences(this.getApplicationContext())
                        .getBoolean("remove-exif", false)) {
                    File img;
                    try(InputStream is = FileUtils.getInputStream(img = outFile);
                        OutputStream os = FileUtils.getOutputStream(outFile = new File(getCacheDir(), "s.jpg"))) {
                        new ExifInterfaceExtended(img).saveExclusive(is, os, true);
                    }
                }
                outUri = FileProvider.getUriForFile(this, "de.k3b.LLCrop", outFile);
            } catch (Exception e) {
                // #14: delete affected file as it is useless
                outFile.delete();
                Log.e(TAG, "Error " + context_message + "(" + outUri +") => " + e.getMessage(), e);
                Toast.makeText(this,
                       // getString(R.string.toast_saved_error, outFile.getAbsolutePath(),
                                e.getMessage()
                       // )
                        ,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, getInstanceNo4Debug() + "Error cropToSharedUri(): Missing input imageUri.");
        }
        return outUri;
    }

    public void setRotationBeforeCrop(int rotationBeforeCrop) {
        this.rotationBeforeCrop = rotationBeforeCrop % 360;
        setRotationAfterCrop(this.rotationBeforeCrop);
    }

    public int getRotationBeforeCrop() {
        return rotationBeforeCrop;
    }

    public int getRotationAfterCrop() {
        return rotationAfterCrop;
    }

    public void setRotationAfterCrop(int rotationAfterCrop) {
        if (rotationAfterCrop != getRotationAfterCrop()) {
            this.rotationAfterCrop = rotationAfterCrop;
            invalidateOptionsMenu();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_rotate, menu);
        if (ENABLE_ASPECT_RATIO) {
            getMenuInflater().inflate(R.menu.menu_aspect_ratio, menu);
        }

/*
    Format 3:4:
            9x11,10x13,11x15,13x17,20x27,30x40
    Format 2:3:
            9x13,10x15,11x17,13x18,20x30,30x45
    Format 9:16:
            9x15,10x18
*/
        getMenuInflater().inflate(R.menu.menu_exif, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        for(Integer key : menu2Rotation.keySet()) {
            if (key != null) menu.findItem(key).setChecked(getRotationAfterCrop() == menu2Rotation.get(key));
        }
        if (ENABLE_ASPECT_RATIO) onPrepareMenuAspectRatio(menu);

        menu.findItem(R.id.menu_exif).setTitle(getString(R.string.remove_exif, new StringBuilder().append('(').append(PreferenceManager
                .getDefaultSharedPreferences(this.getApplicationContext())
                .getBoolean("remove-exif", false)).append(')')));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuItemId = item.getItemId();
        Integer rotation = menu2Rotation.get(menuItemId);
        if (rotation != null) {
            this.setRotationAfterCrop(rotation);
            uCropView.setRotatedDegrees(this.getRotationAfterCrop());
            return true;
        }

        if (menuItemId == R.id.menu_save) {
            return edit.saveAsPublicCroppedImage();
        } else if (menuItemId == R.id.menu_send) {
            return send.sendPrivateCroppedImage();
        } else if (menuItemId == R.id.menu_get_content) {
            return content.returnPrivateCroppedImage();
        } else if (isAspectRatio(item)) {
            // Menu.NONE with ratio in title
            setAspectRatio(item.getTitle().toString());
            return true;
        } else if (menuItemId == R.id.menu_ratio_square) {
            setAspectRatio(ASPECT_RATIO_SQUARE);
            return true;
        } else if (menuItemId == R.id.menu_ratio_free) {
            setAspectRatio(null);
            return true;
        } else if (menuItemId == R.id.menu_ratio_userdefined) {
            return onAspectRatioUserdefined((currentAspectRatioString == null) ? null : currentAspectRatioString.split("x"));
        } else if (menuItemId == R.id.menu_exif) {
            SharedPreferences sharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(this.getApplicationContext());
            sharedPreferences
                    .edit()
                    .putBoolean("remove-exif", !sharedPreferences.getBoolean("remove-exif", false))
                    .apply();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    //----------------------

    private void onPrepareMenuAspectRatio(Menu menu) {
        SubMenu menuAspectRatio = getSubMenuAspectRatio(menu);
        if (menuAspectRatio != null) {
            if (currentAspectRatioDefinitions == null) {
                currentAspectRatioDefinitions = getRatios(menuAspectRatio);
            } else {
                redefineMenuRatio(menuAspectRatio, currentAspectRatioDefinitions);
            }

            checkCurrentRatio(menuAspectRatio);
        }
    }

    private void redefineMenuRatio(SubMenu menuAspectRatio, List<String> currentAspectRatioDefinitions) {
        menuAspectRatio.removeGroup(R.id.menu_group_ratio_userdefined);
        for (String title : currentAspectRatioDefinitions) {
            menuAspectRatio.add(R.id.menu_group_ratio_userdefined, Menu.NONE, 100, title);
        }
    }

    private void checkCurrentRatio(SubMenu menuAspectRatio) {
        for (int i = menuAspectRatio.size() - 1; i >= 0; i--) {
            MenuItem item = menuAspectRatio.getItem(i);
            item.setCheckable(true);
            item.setChecked(isCurrentAspectRatio(item));
        }
    }


    @NonNull
    private List<String> getRatios(SubMenu menuAspectRatio) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < menuAspectRatio.size() ; i++) {
            MenuItem item = menuAspectRatio.getItem(i);
            if (isAspectRatio(item)) {
                items.add(item.getTitle().toString());
            }
        }
        return items;
    }

    private SubMenu getSubMenuAspectRatio(Menu menu) {
        MenuItem menuAspectRatio = menu.findItem(R.id.menu_aspect_ratio);
        if (menuAspectRatio != null) {
            return menuAspectRatio.getSubMenu();
        }
        return null;
    }

    /**
     * @return true if Menu.NONE with ratio in title
     */
    private boolean isAspectRatio(MenuItem item) {
        return item.getItemId() == Menu.NONE
                && item.getTitle() != null;
    }

    /**
     * @return true if item is the current selected ratio
     */
    private boolean isCurrentAspectRatio(MenuItem item) {
        if (currentAspectRatioString == null) {
            return item.getItemId() == R.id.menu_ratio_free;
        } else if (currentAspectRatioString.equals(ASPECT_RATIO_SQUARE)) {
            return item.getItemId() == R.id.menu_ratio_square;
        } else {
            return isAspectRatio(item)
                    && currentAspectRatioString.equalsIgnoreCase(item.getTitle().toString());
        }
    }

    private boolean onAspectRatioUserdefined(String[] paramXY) {
        String x,y;
        if (paramXY != null && paramXY.length > 1) {
            x = paramXY[0];
            y = paramXY[1];
        } else {
            Rect rect = uCropView.getCropRect();
            x = "" + rect.width();
            y = "" + rect.height();
        }

        DefineAspectRatioFragment fragment = DefineAspectRatioFragment.newInstance(x, y);

        fragment.show(getFragmentManager(),"onRatioUserdefined");
        return true;

    }

    public void onDefineAspectRatio(String width, String height) {
        setAspectRatio(width + "x" + height);
    }

    private void setAspectRatio(String aspectRatio) {
        // allow "no aspect ratio" and "any size"
        uCropView.setAspectRatio(SIZE_MIN, SIZE_MIN);
        uCropView.setMaxCropResultSize(SIZE_MAX, SIZE_MAX);
        uCropView.clearAspectRatio();

        this.currentAspectRatioString = aspectRatio;
        String[] xy = (aspectRatio == null) ? null : aspectRatio.split("x");

        if (xy != null && xy.length >= 2) {
            try {
                int x = Integer.parseInt(xy[0]);
                int y = Integer.parseInt(xy[1]);
                if (x >= SIZE_ABSOLUTE_TRESHHOLD && y >= SIZE_ABSOLUTE_TRESHHOLD) {
                    Rect cropRect = getCropRect();
                    if (cropRect.width() != x || cropRect.height() != y) {
                        mLastCropRect = new Rect(0, 0, x, y);

                        uCropView.setCropRect(mLastCropRect);
                    }

                    // fixed AspectRatio, fixed size
                    uCropView.setAspectRatio(x, y);
                    uCropView.setMinCropResultSize(x,y);
                    uCropView.setMaxCropResultSize(x,y);

                } else {
                    // fixed AspectRatio, any size
                    uCropView.setAspectRatio(x, y);
                    uCropView.setMinCropResultSize(SIZE_MIN,SIZE_MIN);
                    uCropView.setMaxCropResultSize(SIZE_MAX, SIZE_MAX);
                }

                // uCropView.on
            } catch (Exception ex) {
                String message = "setAspectRatio('" + aspectRatio + "') . Valid example '7x13'";
                Log.e(TAG, getInstanceNo4Debug() + message);
            }
        }
        onUpdateCropping();
        uCropView.invalidate();

        redefineAspectMenu(currentAspectRatioDefinitions, aspectRatio);
    }

    private void redefineAspectMenu(List<String> currentAspectRatioDefinitions, String aspectRatio) {
        if (this.currentAspectRatioDefinitions != null
                && aspectRatio != null
                && !ASPECT_RATIO_SQUARE.equals(aspectRatio)) {
            int found = this.currentAspectRatioDefinitions.indexOf(aspectRatio);
            if (found >= 0) this.currentAspectRatioDefinitions.remove(found);

            while (this.currentAspectRatioDefinitions.size() > MAX_COUNT_ASPECT_RATIO_DEFINITIONS) {
                this.currentAspectRatioDefinitions.remove(this.currentAspectRatioDefinitions.size() - 1);
            }
            this.currentAspectRatioDefinitions.add(0, aspectRatio);
        }
        if (currentAspectRatioDefinitions != null) {
           PreferenceManager
                   .getDefaultSharedPreferences(this.getApplicationContext())
                   .edit()
                   .putString(KEY_ASPECT_RATIO_DEFINITIONS, String.join(";", currentAspectRatioDefinitions))
                   .apply();
        }

    }
}
