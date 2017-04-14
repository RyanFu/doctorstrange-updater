package com.sxc.doctorstrangeupdater;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;


public class DoctorStrangeUpdater {

    public static final String DOCTOR_SHARED_PREFERENCES = "Doctorstrange_Updater_Shared_Preferences";
    public static final String JS_VERSION = "Doctorstrange_Updater_Stored_Version";
    private final String LAST_UPDATE_TIMESTAMP = "Doctorstrange_Updater_Last_Update_Timestamp";
    private final String JS_BUNDLENAME = "doctor.jsbundle";
    private final String JS_FOLDER = "JSCode";


    private static DoctorStrangeUpdater ourInstance = new DoctorStrangeUpdater();
    private String updateMetadataUrl;
    private String metadataAssetName;
    private Context context;
    private boolean showInfo = true;//是否显示Toast

    public static DoctorStrangeUpdater getInstance(Context context) {
        ourInstance.context = context;
        return ourInstance;
    }

    private DoctorStrangeUpdater() {
    }

    public DoctorStrangeUpdater setUpdateMetadataUrl(String url) {
        this.updateMetadataUrl = url;
        return this;
    }

    public DoctorStrangeUpdater setMetadataAssetName(String metadataAssetName) {
        this.metadataAssetName = metadataAssetName;
        return this;
    }


    public DoctorStrangeUpdater showProgress(boolean progress) {
        this.showInfo = progress;
        return this;
    }


    public void checkForUpdates() {
        this.showProgressToast(R.string.auto_updater_checking);
        FetchMetadataTask task = new FetchMetadataTask();
        task.execute(this.updateMetadataUrl);
    }


    public String getLatestJSCodeLocation() {
        SharedPreferences prefs = context.getSharedPreferences(DOCTOR_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String currentVersionStr = prefs.getString(JS_VERSION, null);

        Version currentVersion;
        try {
            currentVersion = new Version(currentVersionStr);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        String jsonString = this.getStringFromAsset(this.metadataAssetName);
        if (jsonString == null) {
            return null;
        } else {
            String jsCodePath = null;
            try {
                JSONObject assetMetadata = new JSONObject(jsonString);
                String assetVersionStr = assetMetadata.getString("version");
                Version assetVersion = new Version(assetVersionStr);

                if (currentVersion.compareTo(assetVersion) > 0) {
                    File jsCodeDir = context.getDir(JS_FOLDER, Context.MODE_PRIVATE);
                    File jsCodeFile = new File(jsCodeDir, JS_BUNDLENAME);
                    jsCodePath = jsCodeFile.getAbsolutePath();
                } else {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(JS_VERSION, currentVersionStr);
                    editor.apply();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return jsCodePath;
        }
    }

    private String getStringFromAsset(String assetName) {
        String jsonString = null;
        try {
            InputStream inputStream = this.context.getAssets().open(assetName);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            jsonString = new String(buffer, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonString;
    }

//    private void verifyMetadata(JSONObject metadata) {
//        try {
//            String version = metadata.getString("version");
//            String minContainerVersion = metadata.getString("minContainerVersion");
//            if (this.shouldDownloadUpdate(version, minContainerVersion)) {
//                this.showProgressToast(R.string.auto_updater_downloading);
//                String downloadURL = metadata.getJSONObject("url").getString("url");
//                if (metadata.getJSONObject("url").getBoolean("isRelative")) {
//                    if (this.hostname == null) {
//                        this.showProgressToast(R.string.auto_updater_no_hostname);
//                        System.out.println("No hostname provided for relative downloads. Aborting");
//                    } else {
//                        downloadURL = this.hostname + downloadURL;
//                    }
//                }
//                FetchUpdateTask updateTask = new FetchUpdateTask();
//                updateTask.execute(downloadURL, version);
//            } else {
//                this.showProgressToast(R.string.auto_updater_up_to_date);
//                System.out.println("Already Up to Date");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }


    private String getContainerVersion() {
        String version = null;
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = pInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return version;
    }


    private void showProgressToast(int message) {
        if (this.showInfo) {
            if (Looper.myLooper() == null)
                Looper.prepare();
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, message, duration);
            toast.show();
//            Looper.loop();
        }
    }

    private class FetchMetadataTask extends AsyncTask<String, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            String metadataStr;
            JSONObject metadata = null;
            try {
                URL url = new URL(params[0]);

                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                StringBuilder total = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    total.append(line);
                }
                metadataStr = total.toString();
                if (!metadataStr.isEmpty()) {
                    metadata = new JSONObject(metadataStr);
                } else {
                    DoctorStrangeUpdater.this.showProgressToast(R.string.auto_updater_no_metadata);
                }
            } catch (Exception e) {
                DoctorStrangeUpdater.this.showProgressToast(R.string.auto_updater_invalid_metadata);
                e.printStackTrace();
            }
            return metadata;
        }

//        @Override
//        protected void onPostExecute(JSONObject jsonObject) {
//            DoctorStrangeUpdater.this.verifyMetadata(jsonObject);
//        }
    }

    private class FetchUpdateTask extends AsyncTask<String, Void, String> {

        private PowerManager.WakeLock mWakeLock;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            mWakeLock.acquire();
        }

        @Override
        protected String doInBackground(String... params) {
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // download the file
                input = connection.getInputStream();
                File jsCodeDir = context.getDir(JS_FOLDER, Context.MODE_PRIVATE);
                if (!jsCodeDir.exists()) {
                    jsCodeDir.mkdirs();
                }
                File jsCodeFile = new File(jsCodeDir, JS_BUNDLENAME);
                output = new FileOutputStream(jsCodeFile);

                byte data[] = new byte[4096];
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    output.write(data, 0, count);
                }

                SharedPreferences prefs = context.getSharedPreferences(DOCTOR_SHARED_PREFERENCES, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(JS_VERSION, params[1]);
                editor.putLong(LAST_UPDATE_TIMESTAMP, new Date().getTime());
                editor.apply();
            } catch (Exception e) {
                e.printStackTrace();
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();
            if (result != null) {
                DoctorStrangeUpdater.this.showProgressToast(R.string.auto_updater_downloading_error);
            } else {
                DoctorStrangeUpdater.this.showProgressToast(R.string.auto_updater_downloading_success);
            }
        }
    }

    public interface Interface {
        void updateFinished();
    }
}
