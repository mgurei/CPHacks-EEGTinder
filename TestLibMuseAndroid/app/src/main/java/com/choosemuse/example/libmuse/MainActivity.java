
package com.choosemuse.example.libmuse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicReference;

import com.choosemuse.libmuse.Accelerometer;
import com.choosemuse.libmuse.AnnotationData;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.LibmuseVersion;
import com.choosemuse.libmuse.MessageType;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConfiguration;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseFileFactory;
import com.choosemuse.libmuse.MuseFileReader;
import com.choosemuse.libmuse.MuseFileWriter;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;
import com.choosemuse.libmuse.MuseVersion;
import com.choosemuse.libmuse.Result;
import com.choosemuse.libmuse.ResultLevel;
import com.goebl.david.Response;
import com.goebl.david.Webb;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;


import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity implements OnClickListener {


    private final String TAG = "TestLibMuseAndroid";

    private MuseManagerAndroid manager;
    private Muse muse;

    private ConnectionListener connectionListener;
    private int calibrationLength = 10; // seconds

    private DataListener dataListener;

    private double alphaCalib;
    private double betaCalib;

    private final double[] alphaBuffer = new double[6];
    private boolean alphaStale;
    private final double[] betaBuffer = new double[6];
    private boolean betaStale;


    private final Handler handler = new Handler();

    private ArrayAdapter<String> spinnerAdapter;

    private boolean dataTransmission = true;

    TextView nameText;
    TextView bioText;
    ImageView image;
    JSONArray photos;
    int imageNr = 0;

    String Id;
    String name;

    Webb webb = Webb.create();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We need to set the context on MuseManagerAndroid before we can do anything.
        // This must come before other LibMuse API calls as it also loads the library.
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        WeakReference<MainActivity> weakActivity =
                new WeakReference<MainActivity>(this);
        // Register a listener to receive connection state changes.
        connectionListener = new ConnectionListener(weakActivity);
        // Register a listener to receive data from a Muse.
        dataListener = new DataListener(weakActivity);
        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        manager.setMuseListener(new MuseL(weakActivity));

        // Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
        // simplify the connection process.  This requires access to the COARSE_LOCATION
        // or FINE_LOCATION permissions.  Make sure we have these permissions before
        // proceeding.
        ensurePermissions();

        // Load and initialize our UI.
        initUI();

        // Start our asynchronous updates of the UI.
   //     handler.post(tickUi);

        webb.setBaseUri("https://api.gotinder.com");
        webb.setDefaultHeader("User-Agent", "Tinder/4.7.1 (iPhone; iOS 9.2; Scale/2.00)");
        webb.setDefaultHeader("Content-Type","application/json");
        webb.setDefaultHeader("X-Auth-Token","e4de6e49-7124-4212-b386-b89fda4d7865");

        nameText = (TextView) findViewById(R.id.textName);
        bioText = (TextView) findViewById(R.id.bioText);
        Button acceptButton = (Button) findViewById(R.id.buttonAccept);
        Button rejectButton = (Button) findViewById(R.id.buttonReject);
        image = (ImageView) findViewById(R.id.imageView);

        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                nextPicture(imageNr);
                imageNr++;
                if (imageNr > (photos.length()-1)){
                    imageNr = 0;
                }
            }
        });

        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    accept();
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }
        });

        rejectButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                reject();
            }
        });

        //tinderAuthenticate();

        try {
            getNewPerson();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected void onPause() {
        super.onPause();
        // It is important to call stopListening when the Activity is paused
        // to avoid a resource leak from the LibMuse library.
        manager.stopListening();
    }

    public boolean isBluetoothEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.refresh) {
            // The user has pressed the "Refresh" button.
            // Start listening for nearby or paired Muse headbands. We call stopListening
            // first to make sure startListening will clear the list of headbands and start fresh.
            manager.stopListening();
            manager.startListening();

        } else if (v.getId() == R.id.connect) {

            // The user has pressed the "Connect" button to connect to
            // the headband in the spinner.

            // Listening is an expensive operation, so now that we know
            // which headband the user wants to connect to we can stop
            // listening for other headbands.
            manager.stopListening();

            List<Muse> availableMuses = manager.getMuses();
            Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
            Log.w(TAG, "HERE IS IT:" + availableMuses.toString());
            // Check that we actually have something to connect to.
            if (availableMuses.size() < 1 || musesSpinner.getAdapter().getCount() < 1) {
                Log.w(TAG, "There is nothing to connect to");
            } else {

                // Cache the Muse that the user has selected.
                muse = availableMuses.get(musesSpinner.getSelectedItemPosition());
                // Unregister all prior listeners and register our data listener to
                // receive the MuseDataPacketTypes we are interested in.  If you do
                // not register a listener for a particular data type, you will not
                // receive data packets of that type.
                muse.unregisterAllListeners();
                muse.registerConnectionListener(connectionListener);
                muse.registerDataListener(dataListener, MuseDataPacketType.EEG);
                muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_SCORE);
                muse.registerDataListener(dataListener, MuseDataPacketType.BETA_SCORE);

                muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
                muse.registerDataListener(dataListener, MuseDataPacketType.DRL_REF);
                muse.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION);

                // Initiate a connection to the headband and stream the data asynchronously.
                muse.runAsynchronously();

                setCalibrationDialog();
            }

        }
    }


    private void setCalibrationDialog() {

        DialogInterface.OnClickListener positiveButtonListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which){
                        dialog.dismiss();
                        Log.w(TAG, "Calibration starts");
                        //startCalibration();
                        Executors.newSingleThreadExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                startCalibration();
                            }
                        });
                    }
                };

        DialogInterface.OnClickListener negativeButtonListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which){
                        dialog.dismiss();
                        Log.w(TAG, "Calibration avoided");

                    }
                };


        AlertDialog introDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.start_calibration_title)
                .setMessage(R.string.start_calibration_description)
                .setPositiveButton(R.string.start_calibration_agree, positiveButtonListener)
                .setNegativeButton(R.string.discard_calibration, negativeButtonListener)
                .create();
        introDialog.show();
    }


    public void startCalibration() {
            
        double[] calibration_alpha = new double[6];
            double[] calibration_beta = new double[6];
            long startTime = System.currentTimeMillis(); //fetch starting time

            int N = 0;
            while ((System.currentTimeMillis() - startTime) < 5000) {
                N++;

                calibration_alpha[0] += alphaBuffer[0];
                calibration_alpha[1] += alphaBuffer[1];
                calibration_alpha[2] += alphaBuffer[2];
                calibration_alpha[3] += alphaBuffer[3];
                calibration_alpha[4] += alphaBuffer[4];
                calibration_alpha[5] += alphaBuffer[5];

                calibration_beta[0] += betaBuffer[0];
                calibration_beta[1] += betaBuffer[1];
                calibration_beta[2] += betaBuffer[2];
                calibration_beta[3] += betaBuffer[3];
                calibration_beta[4] += betaBuffer[4];
                calibration_beta[5] += betaBuffer[5];

            }

            int i;
            for (i = 0; i < 6; i++) {
                calibration_alpha[i] = calibration_alpha[i] / N;
                calibration_beta[i] = calibration_beta[i] / N;
                Log.w(TAG, "alpha mean" + i + " = " + calibration_alpha[i]);
                Log.w(TAG, "beta mean" + i + " = " + calibration_alpha[i]);

            }


    }

    //--------------------------------------
    // Permissions

    /**
     * The ACCESS_COARSE_LOCATION permission is required to use the
     * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
     * On an Android 6.0 device, the following code will display 2 dialogs,
     * one to provide context and the second to request the permission.
     * On an Android device running an earlier version, nothing is displayed
     * as the permission is granted from the manifest.
     *
     * If the permission is not granted, then Muse 2016 (MU-02) headbands will
     * not be discovered and a SecurityException will be thrown.
     */
    private void ensurePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
            // the user to grant us the permission.

            DialogInterface.OnClickListener buttonListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which){
                            dialog.dismiss();
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    0);
                        }
                    };

            // This is the context dialog which explains to the user the reason we are requesting
            // this permission.  When the user presses the positive (I Understand) button, the
            // standard Android permission dialog will be displayed (as defined in the button
            // listener above).
            AlertDialog introDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_dialog_title)
                    .setMessage(R.string.permission_dialog_description)
                    .setPositiveButton(R.string.permission_dialog_understand, buttonListener)
                    .create();
            introDialog.show();
        }
    }


    //--------------------------------------
    // Listeners

    /**
     * You will receive a callback to this method each time a headband is discovered.
     * In this example, we update the spinner with the MAC address of the headband.
     */
    public void museListChanged() {
        final List<Muse> list = manager.getMuses();
        spinnerAdapter.clear();
        for (Muse m : list) {
            spinnerAdapter.add(m.getName() + " - " + m.getMacAddress());
        }
    }

    /**
     * You will receive a callback to this method each time there is a change to the
     * connection state of one of the headbands.
     * @param p     A packet containing the current and prior connection states
     * @param muse  The headband whose state changed.
     */
    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = p.getPreviousConnectionState() + " -> " + current;
        Log.i(TAG, status);

        if (current == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Muse disconnected:" + muse.getName());
            // Save the data file once streaming has stopped.
            //saveFile();
            // We have disconnected from the headband, so set our cached copy to null.
            this.muse = null;
        }
    }

    /**
     * You will receive a callback to this method each time the headband sends a MuseDataPacket
     * that you have registered.  You can use different listeners for different packet types or
     * a single listener for all packet types as we have done here.
     * @param p     The data packet containing the data from the headband (eg. EEG data)
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
       // writeDataPacketToFile(p);

        // valuesSize returns the number of data values contained in the packet.
        final long n = p.valuesSize();
        switch (p.packetType()) {
//            case EEG:
//                assert(eegBuffer.length >= n);
//                getEegChannelValues(eegBuffer,p);
//                eegStale = true;
//                break;
            case ALPHA_SCORE:
                assert(alphaBuffer.length >= n);
                getEegChannelValues(alphaBuffer,p);
                alphaStale = true;
                break;
            case BETA_SCORE:
                assert(betaBuffer.length >= n);
                getEegChannelValues(betaBuffer,p);
                betaStale = true;
                break;
            case BATTERY:
            case DRL_REF:
            case QUANTIZATION:
            default:
                break;
        }
    }

    /**
     * You will receive a callback to this method each time an artifact packet is generated if you
     * have registered for the ARTIFACTS data type.  MuseArtifactPackets are generated when
     * eye blinks are detected, the jaw is clenched and when the headband is put on or removed.
     * @param p     The artifact packet with the data from the headband.
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
    }

    /**
     * Helper methods to get different packet values.  These methods simply store the
     * data in the buffers for later display in the UI.
     *
     * getEegChannelValue can be used for any EEG or EEG derived data packet type
     * such as EEG, ALPHA_ABSOLUTE, ALPHA_RELATIVE or HSI_PRECISION.  See the documentation
     * of MuseDataPacketType for all of the available values.
     * Specific packet types like ACCELEROMETER, GYRO, BATTERY and DRL_REF have their own
     * getValue methods.
     */
    private void getEegChannelValues(double[] buffer, MuseDataPacket p) {
        buffer[0] = p.getEegChannelValue(Eeg.EEG1);
        buffer[1] = p.getEegChannelValue(Eeg.EEG2);
        buffer[2] = p.getEegChannelValue(Eeg.EEG3);
        buffer[3] = p.getEegChannelValue(Eeg.EEG4);
        buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT);
        buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT);
    }


    //--------------------------------------
    // UI Specific methods

    /**
     * Initializes the UI of the example application.
     */
    private void initUI() {
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);

        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        musesSpinner.setAdapter(spinnerAdapter);
    }

    /**
     * The runnable that is used to update the UI at 60Hz.
     *
     * We update the UI from this Runnable instead of in packet handlers
     * because packets come in at high frequency -- 220Hz or more for raw EEG
     * -- and it only makes sense to update the UI at about 60fps. The update
     * functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
//    private final Runnable tickUi = new Runnable() {
//        @Override
//        public void run() {
////            if (eegStale) {
////                updateEeg();
////            }
//            if (alphaStale) {
//                updateAlpha();
//            }
//            if (betaStale) {
//                updateBeta();
//            }
//            handler.postDelayed(tickUi, 1000 / 60);
//        }
//    };


//    private void updateEeg() {
//        TextView tp9 = (TextView)findViewById(R.id.eeg_tp9);
//        TextView fp1 = (TextView)findViewById(R.id.eeg_af7);
//        TextView fp2 = (TextView)findViewById(R.id.eeg_af8);
//        TextView tp10 = (TextView)findViewById(R.id.eeg_tp10);
//        tp9.setText(String.format("%6.2f", eegBuffer[0]));
//        fp1.setText(String.format("%6.2f", eegBuffer[1]));
//        fp2.setText(String.format("%6.2f", eegBuffer[2]));
//        tp10.setText(String.format("%6.2f", eegBuffer[3]));
//    }
//

//    private void updateAlpha() {
//        TextView al1 = (TextView)findViewById(R.id.eeg_al1);
//        TextView al2 = (TextView)findViewById(R.id.eeg_al2);
//        TextView al3 = (TextView)findViewById(R.id.eeg_al3);
//        TextView al4 = (TextView)findViewById(R.id.eeg_al4);
//        al1.setText(String.format("%6.2f", alphaBuffer[0]));
//        al2.setText(String.format("%6.2f", alphaBuffer[1]));
//        al3.setText(String.format("%6.2f", alphaBuffer[2]));
//        al4.setText(String.format("%6.2f", alphaBuffer[3]));
//    }
//
//    private void updateBeta() {
//        TextView be1 = (TextView)findViewById(R.id.eeg_be1);
//        TextView be2 = (TextView)findViewById(R.id.eeg_be2);
//        TextView be3 = (TextView)findViewById(R.id.eeg_be3);
//        TextView be4 = (TextView)findViewById(R.id.eeg_be4);
//        be1.setText(String.format("%6.2f", betaBuffer[0]));
//        be2.setText(String.format("%6.2f", betaBuffer[1]));
//        be3.setText(String.format("%6.2f", betaBuffer[2]));
//        be4.setText(String.format("%6.2f", betaBuffer[3]));
//    }



    class MuseL extends MuseListener {
        final WeakReference<MainActivity> activityRef;

        MuseL(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void museListChanged() {
            activityRef.get().museListChanged();
        }
    }

    class ConnectionListener extends MuseConnectionListener {
        final WeakReference<MainActivity> activityRef;

        ConnectionListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {
            activityRef.get().receiveMuseConnectionPacket(p, muse);
        }
    }

    class DataListener extends MuseDataListener {
        final WeakReference<MainActivity> activityRef;

        DataListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
            activityRef.get().receiveMuseDataPacket(p, muse);
        }

        @Override
        public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
            activityRef.get().receiveMuseArtifactPacket(p, muse);
        }
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            Log.e("src",src);
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            Log.e("Bitmap","returned");
            return myBitmap;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Exception",e.getMessage());
            return null;
        }
    }

    private void accept() throws JSONException {
        Response<JSONObject> response = webb
                .get("/like/" + Id )
                .ensureSuccess()
                .asJsonObject();

        JSONObject apiResult = response.getBody();
        String matchResult = apiResult.getString("match");
        if(matchResult.equals("true")){
            matchDialoge();
            Log.w("MATCH", name);

        }else{
            Toast.makeText(this, "Liked", Toast.LENGTH_SHORT).show();
        }

        try {
            getNewPerson();
        } catch (JSONException e){
            e.printStackTrace();
        }


    }

    private void reject(){
        Response<JSONObject> response = webb
                .get("/pass/" + Id )
                .ensureSuccess()
                .asJsonObject();

        Toast.makeText(this, "Passed", Toast.LENGTH_SHORT).show();
        try {
            getNewPerson();
        } catch (JSONException e){
            e.printStackTrace();
        }

    }




    private void nextPicture(int i){
        try{
            JSONObject firstPhoto = photos.getJSONObject(i);
            String photoUrl = firstPhoto.getString("url");

            Log.w("url", photoUrl);

            image.setImageBitmap(getBitmapFromURL(photoUrl));
        }
        catch (JSONException e){
            e.printStackTrace();
        }



    }

    private void getNewPerson() throws JSONException {
        Response<JSONObject> response = webb
                .get("/user/recs")
                .ensureSuccess()
                .asJsonObject();

        JSONObject apiResult = response.getBody();

        JSONArray results = apiResult.getJSONArray("results");
        JSONObject firstRes = results.getJSONObject(0);

        String test = firstRes.toString();

        name = firstRes.getString("name");
        String bio = firstRes.getString("bio");
        Id = firstRes.getString("_id");


        photos = firstRes.getJSONArray("photos");
        JSONObject firstPhoto = photos.getJSONObject(0);
        String photoUrl = firstPhoto.getString("url");

        nameText.setText(name);
        bioText.setText(bio);
        image.setImageBitmap(getBitmapFromURL(photoUrl));
    }



    private void matchDialoge() {

        DialogInterface.OnClickListener positiveButtonMatchListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which){
                        dialog.dismiss();

                    }
                };



        AlertDialog introDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.match_dialoge_title)
                .setMessage(R.string.match_dialoge_desc + name)
                .setPositiveButton(R.string.start_calibration_agree, positiveButtonMatchListener)
                .create();
        introDialog.show();
    }

}
