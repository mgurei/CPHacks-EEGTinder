package com.copenhacks2017.eegtinder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;


import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private final String TAG = "TinderMuseAndroid";

    private boolean dataTransmission = true;

    private MuseManagerAndroid manager;
    private MuseConnectionListener connectionListener;

    private Muse muse;
    private DataListener dataListener;

    private final double[] alphaScore = new double[6];
    private boolean alpha_status;
    private final double[] betaScore = new double[6];
    private boolean beta_status;
    private final double[] accelBuffer = new double[3];

    public final Handler handler = new Handler();

    // Add here the necessary private buffers

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        WeakReference<MainActivity> weakActivity = new WeakReference<MainActivity>(this);
        // check connection state
        connectionListener = new ConnectionListener(weakActivity);
        // register listener to get data from
        dataListener = new DataListener(weakActivity);
        // listener to receive notification abour available Muse


        ensurePermissions();

        initUI();

    }

    protected void onPause() {
        super.onPause();
        manager.stopListening();
    }

    //@Override
    public void onClick(View v) {

        if (v.getId() == R.id.connect) {

            // The user has pressed the "Connect" button to connect to
            // the headband in the spinner.

            // Listening is an expensive operation, so now that we know
            // which headband the user wants to connect to we can stop
            // listening for other headbands.
            manager.stopListening();

            List<Muse> availableMuses = manager.getMuses();

            // Check that we actually have something to connect to.
            if (availableMuses.size() < 1) {
                Log.w(TAG, "There is nothing to connect to");
            } else {

                // Unregister all prior listeners and register our data listener to
                // receive the MuseDataPacketTypes we are interested in.  If you do
                // not register a listener for a particular data type, you will not
                // receive data packets of that type.
                muse.unregisterAllListeners();
                muse.registerConnectionListener(connectionListener);
                muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_SCORE);
                muse.registerDataListener(dataListener, MuseDataPacketType.BETA_SCORE);

                // Initiate a connection to the headband and stream the data asynchronously.
                muse.runAsynchronously();
            }

            if (muse != null) {
                dataTransmission = !dataTransmission;
                muse.enableDataTransmission(dataTransmission);
            }
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

    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = R.string.status + current.toString();
        Log.i(TAG, status);

        // Update the UI with the change in connection state.
        handler.post(new Runnable() {
            @Override
            public void run() {

                final TextView statusText = (TextView) findViewById(R.id.con_status);
                statusText.setText(status);
            }
        });
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


    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
        // writeDataPacketToFile(p);

        // valuesSize returns the number of data values contained in the packet.
        final long n = p.valuesSize();
        switch (p.packetType()) {
            case ALPHA_SCORE:
                assert(alphaScore.length >= n);
                getEegChannelValues(alphaScore,p);
                alpha_status = true;
                break;
            case BETA_SCORE:
                assert (betaScore.length >= n);
                getEegChannelValues(alphaScore,p);
                beta_status = true;
            case BATTERY:
            case DRL_REF:
            case QUANTIZATION:
            default:
                break;
        }
    }

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

    private void updateEeg() {
        TextView tx1 = (TextView)findViewById(R.id.alpha1);
        TextView tx2 = (TextView)findViewById(R.id.alpha2);
        TextView tx3 = (TextView)findViewById(R.id.alpha3);
        TextView tx4 = (TextView)findViewById(R.id.alpha4);
        tx1.setText(String.format("%6.2f", alphaScore[0]));
        tx2.setText(String.format("%6.2f", alphaScore[1]));
        tx3.setText(String.format("%6.2f", alphaScore[2]));
        tx4.setText(String.format("%6.2f", alphaScore[3]));
    }




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


    private void initUI() {
        setContentView(R.layout.activity_main);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
    }


}

