package drexelairlab.banddata;

//main references
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


//Band References
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandIOException;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;


//native java references
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {


    private BandClient client = null;
    private Button btnStart, btnConnect, btnDisconnect, timestamp;
    private TextView txtStatus, staticBPM, vQuality, errorReport, gsrText, skinTemp;
    public String data, gsr, skin, timestamper="", connected="Connected to Server.", disconnected="Disconnected from Server.";
    private volatile boolean shouldConnect = false;
    private Handler mHandler;
    final WeakReference<Activity> reference = new WeakReference<Activity>(this);
    HeartRateConsentTask consent = new HeartRateConsentTask();

    //this thread controls the sending of data to the PC Server (this can't run on the main thread)
    final Thread tthread = new Thread(new Runnable(){
        @Override
        public void run() {
            while(true)
            {
                //always asking if we should try to connect to the PC server
                if(shouldConnect)
                    Connect();
            }
        }});


    private BandHeartRateEventListener myHeartListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if(event != null)
            {
                //Update the screen
                Integer rate = event.getHeartRate();
                String bpm = rate.toString() + "bpm";
                String quality = event.getQuality().toString();
                data = (event.getHeartRate() + "," + event.getQuality());

                heartToUI(bpm, quality);
            }

        }
    };

    private BandGsrEventListener mGsrEventListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(final BandGsrEvent event) {
            if (event != null) {
                Integer rs = event.getResistance();
                gsr = rs.toString();
                gsrToUI("GSR: " + gsr + "KΩ");
            }
        }
    };

    private BandSkinTemperatureEventListener mSkinEventListener = new BandSkinTemperatureEventListener() {
        @Override
        public void onBandSkinTemperatureChanged(BandSkinTemperatureEvent event) {
            if(event != null){
                Float temp = event.getTemperature();
                skin = temp.toString();

                tempToUI("Skin Temp: " + skin + "°C");

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //set heart rate
        txtStatus = (TextView) findViewById(R.id.txtStatus);
        //staticBPM = (TextView) findViewById(R.id.staticBPM);
        vQuality = (TextView) findViewById(R.id.vQuality);
        errorReport = (TextView) findViewById(R.id.errorReport);
        gsrText = (TextView) findViewById(R.id.gsrText);
        skinTemp = (TextView) findViewById(R.id.skinTemp);
        //set start heart rate
        btnStart = (Button) findViewById(R.id.btnStart);
        // connect to pc server
        btnConnect = (Button) findViewById(R.id.btnConnect);
        // stop sending dat to pc server; disconnect
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
        btnDisconnect.setVisibility(View.INVISIBLE);
        timestamp = (Button) findViewById(R.id.timestamp);
        timestamp.setVisibility(View.INVISIBLE);
        //start the connection thread
        tthread.start();

        //get the user's consent
        consent.execute(reference);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorReport.setText("");
                new HeartRateSubscriptionTask().execute();
                new GsrSubscriptionTask().execute();
                new SkinTempSubscriptionTask().execute();
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shouldConnect = true;
                btnConnect.setVisibility(View.INVISIBLE);
                btnDisconnect.setVisibility(View.VISIBLE);
                timestamp.setVisibility(View.VISIBLE);
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shouldConnect = false;
                btnConnect.setVisibility(View.VISIBLE);
                btnDisconnect.setVisibility(View.INVISIBLE);
                timestamp.setVisibility(View.INVISIBLE);
            }
        });

        timestamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    timestamper = ",TIMESTAMP HERE";
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //we clear the text when the app is resumed from a pause
        txtStatus.setText("");
        vQuality.setText("");
        gsrText.setText("");
        skinTemp.setText("");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (client != null) {
            try {
                //Whent the app is paused, we have to unregiister the listeners to avoid breaking the app when it is resumed
                client.getSensorManager().unregisterHeartRateEventListener(myHeartListener);
                client.getSensorManager().unregisterGsrEventListener(mGsrEventListener);
                client.getSensorManager().unregisterSkinTemperatureEventListener(mSkinEventListener);
            } catch (BandIOException e) {
                errorToUI(e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (client != null) {
            try {
                client.disconnect().await();
            } catch (InterruptedException e) {
                // Do nothing as this is happening during destroy
            } catch (BandException e) {
                // Do nothing as this is happening during destroy
            }
        }
        super.onDestroy();
    }

    //Kick off the heart rate reading
    private class HeartRateSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    if (client.getSensorManager().getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                        client.getSensorManager().registerHeartRateEventListener(myHeartListener);
                    } else {
                        errorToUI("You have not given this application consent to access heart rate data yet."
                                + " Please press the Heart Rate Consent button.\n");
                    }
                } else {
                    errorToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                errorToUI(exceptionMessage);

            } catch (Exception e) {
                errorToUI(e.getMessage());
            }
            return null;
        }
    }


    //Need to get user's consent
    private class HeartRateConsentTask extends AsyncTask<WeakReference<Activity>, Void, Void> {
        @Override
        protected Void doInBackground(WeakReference<Activity>... params) {
            try {
                if (getConnectedBandClient()) {

                    if (params[0].get() != null) {
                        client.getSensorManager().requestHeartRateConsent(params[0].get(), new HeartRateConsentListener() {
                            @Override
                            public void userAccepted(boolean consentGiven) {
                            }
                        });
                    }
                } else {
                    errorToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                errorToUI(exceptionMessage);

            } catch (Exception e) {
                errorToUI(e.getMessage());
            }
            return null;
        }
    }

    private class GsrSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        client.getSensorManager().registerGsrEventListener(mGsrEventListener);
                    } else {
                        errorToUI("The Gsr sensor is not supported with your Band version. Microsoft Band 2 is required.\n");
                    }
                } else {
                    errorToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                errorToUI(exceptionMessage);

            } catch (Exception e) {
                errorToUI(e.getMessage());
            }
            return null;
        }
    }

    private class SkinTempSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        client.getSensorManager().registerSkinTemperatureEventListener(mSkinEventListener);
                    } else {
                        errorToUI("The Skin temperature sensor is not supported with your Band version. Microsoft Band 2 is required.\n");
                    }
                } else {
                    errorToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                errorToUI(exceptionMessage);

            } catch (Exception e) {
                errorToUI(e.getMessage());
            }
            return null;
        }
    }


    //Get connection to band
    private boolean getConnectedBandClient() throws InterruptedException, BandException {

        if (client == null) {
            //Find paired bands
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();

            if (devices.length == 0) {
                //No bands found; notify user
                return false;
            }

            //Need to set the client if there are devices
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }

        //Need to return connected status.
        return ConnectionState.CONNECTED == client.connect().await();

    }

    private void heartToUI(final String bpm, final String quality) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                txtStatus.setText(bpm);
                //staticBPM.setText("bpm");
                vQuality.setText(quality);
            }
        });
    }

    private void errorToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                errorReport.setText(string);
            }
        });
    }

    private void gsrToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                gsrText.setText(string);
            }
        });
    }

    private void tempToUI(final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                skinTemp.setText(string);
            }
        });
    }


    public void Connect() {
        try
        {
            //This is the code that connects to the PC server. This relies on the global IP address of the PC which should be edited below.
            //Keep in mind that this is a temporary implementation that works; a better client/server connection can be established.
                try{tthread.sleep(1000);}
                catch(InterruptedException e){e.printStackTrace();}

                Socket client = new Socket("144.118.240.219", 2323); //connect to server. The port can be anything as long as it matches the listening port in the server program
                if(client.isConnected())
                {
                    if(shouldConnect)
                        errorToUI(connected);
                    else if(!shouldConnect)
                        errorToUI(disconnected);
                }
            else if(!client.isConnected())
                {
                    errorToUI("Not connected to Server.");
                }
                DataOutputStream DOS = new DataOutputStream(client.getOutputStream());
                DOS.writeUTF(data + "," + gsr + "," + skin + timestamper);
                timestamper = "";
                // close the connection
                client.close();
        }
        catch (IOException e) {e.printStackTrace();}
    }
}


