package com.budius.photogpstag;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.budius.photogpstag.ServiceLogger.LoggerBinder;
import com.budius.photogpstag.ServiceLogger.ServiceLoggerListener;
import com.google.android.gms.common.ConnectionResult;

public class ActivityMain extends Activity implements OnClickListener,
ServiceConnection, ServiceLoggerListener {

	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

	private TextView txtStatus;
	private TextView txtTime1;
	private TextView txtTime2;
	private Button btnStart, btnStop, btnSingleLoc;
	private Intent service;
	private LoggerBinder mLoggerBinder;
	private DateFormat dateFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.MEDIUM);
	private MenuItem menuItemSettings, menuItemSend, menuItemClear;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		btnStart = (Button) findViewById(R.id.btnStart);
		btnStop = (Button) findViewById(R.id.btnStop);
		btnSingleLoc = (Button) findViewById(R.id.btnSingleLoc);
		txtStatus = (TextView) findViewById(R.id.txtStatus);
		txtTime1 = (TextView) findViewById(R.id.txtTime1);
		txtTime2 = (TextView) findViewById(R.id.txtTime2);

		initLabels();

		btnStart.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		btnSingleLoc.setOnClickListener(this);

		service = new Intent(this, ServiceLogger.class);
		startService(service);

	}

	private void initLabels(){
		File logFile = DialogSettings.getLogFile(ActivityMain.this);

		this.txtTime1.setText("");
		this.txtTime2.setText("");

		if (!logFile.exists()){
			return;
		}

		FileHandler h = new FileHandler(logFile);
		ArrayList<Location> list = h.getLog();

		if (list.isEmpty()){
			return;
		}

		txtTime1.setText("Oldest location: " + dateFormat.format(new Date(list.get(0).getTime())));
		txtTime2.setText("Most recent location: " + dateFormat.format(new Date(list.get(list.size() - 1).getTime())));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater mi = new MenuInflater(this);
		mi.inflate(R.menu.main, menu);
		menuItemSettings = menu.findItem(R.id.menuItemSettings);
		menuItemSend = menu.findItem(R.id.menuItemSend);
		menuItemClear = menu.findItem(R.id.menuItemClear);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final File logFile = DialogSettings.getLogFile(ActivityMain.this);

		switch (item.getItemId()) {

		case R.id.menuItemSettings:
			DialogSettings d = new DialogSettings();
			d.show(getFragmentManager(), DialogSettings.TAG);
			return true;

		case R.id.menuItemSend:
			final File export = new File(
					Environment.getExternalStorageDirectory(),
					"Android/data/com.budius.photogpstag/export"
							+ Long.toString(System.currentTimeMillis())
							+ ".gpx");

			new AsyncTask<Void, Void, Integer>() {
				private String excMsg = null;

				@Override
				protected Integer doInBackground(Void... params) {
					if (!logFile.exists())
						return 1;

					ArrayList<Location> locations = new FileHandler(logFile).getLog();

					export.mkdirs();
					if (export.exists())
						export.delete();

					try {
						GpxTrackWriter.write(export, locations);
						return 0;
					} catch (IOException e) {
						excMsg = e.getMessage();
						Log.e("Budius", "IOException. " + excMsg);
						return 2;
					}
				}

				protected void onPostExecute(Integer result) {
					switch (result) {
					case 0:
						Intent intent = new Intent(Intent.ACTION_SEND);
						intent.setType("text/plain");
						intent.putExtra(Intent.EXTRA_SUBJECT, "Photo GPS Log");
						intent.putExtra(Intent.EXTRA_TEXT,
								"here is your GPS log...");
						Uri uri = Uri.fromFile(export);
						intent.putExtra(Intent.EXTRA_STREAM, uri);
						startActivity(Intent.createChooser(intent,
								"Export GPX file"));
						
						//TODO: it'd be nice if this was done synchronously after the export
						new AlertDialog.Builder(ActivityMain.this).setTitle("Clear log?").setMessage("Would you also like to clear the log?").
						setCancelable(false).setNegativeButton("No", null).setPositiveButton("Yes", new DialogInterface.OnClickListener(){
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (logFile.exists())
									logFile.delete();

								initLabels();
							}}).show();
						
						return;
						
					case 1:
						if (export.exists())
							export.delete();
						Toast.makeText(ActivityMain.this,
								"There's nothing to export", Toast.LENGTH_SHORT)
								.show();

						return;
						
					case 2:
						if (export.exists())
							export.delete();
						StringBuilder sb = new StringBuilder();
						sb.append("Failed to export!?!");
						if (excMsg != null) {
							sb.append(" I/O Exception: ");
							sb.append(excMsg);
						}
						Toast.makeText(ActivityMain.this, sb.toString(),
								Toast.LENGTH_SHORT).show();

						return;

					}
				};

			}.execute(new Void[] {});
			return true;

		case R.id.menuItemClear:
			if (!logFile.exists()){
				return true;
			} else {
				new AlertDialog.Builder(this).setTitle("Confirmation").setMessage("Are you certain that you want to clear the location log?").
				setCancelable(false).setNegativeButton("No", null).setPositiveButton("Yes", new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (logFile.exists())
							logFile.delete();

						initLabels();
					}}).show();

				return true;
			}

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		bindService(service, this, 0);
	}

	@Override
	protected void onPause() {
		super.onPause();
		unbindService(this);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case CONNECTION_FAILURE_RESOLUTION_REQUEST:
			/*
			 * If the result code is Activity.RESULT_OK, try to connect again
			 */
			switch (resultCode) {
			case Activity.RESULT_OK:
				/*
				 * Try the request again
				 */
				break;
			}
		}
	}

	private void checkLocationEnabled(){
		LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setTitle("Reminder:")
			.setMessage(
					"This app works better with GPS turned on.\n"
							+ "We've detected that yours is currently turned off.\n"
							+ "Please go to Settings -> Location Access and enable your GPS")
							.setNeutralButton("OK",
									new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									dialog.dismiss();
								}
							}).setCancelable(true).show();
		}

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnStart:
			Log.i("Budius", "Activity trying to start log");
			mLoggerBinder.startLogging();
			checkLocationEnabled();
			break;
		case R.id.btnStop:
			Log.i("Budius", "Activity trying to stop log");
			mLoggerBinder.stopLogging();
			break;
		case R.id.btnSingleLoc:
			Log.i("Budis", "Activity trying to acquire a single location");
			mLoggerBinder.doSingleLocation();
			checkLocationEnabled();
			break;
		default:
			Log.e("Budius", "onClick called with unknown button: "  + v.getId());
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		Log.i("Budius", "Activity connected to service");
		btnStart.setEnabled(true);
		btnSingleLoc.setEnabled(true);
		btnStop.setEnabled(false);
		if (menuItemSettings != null)
			menuItemSettings.setEnabled(true);
		if (menuItemSend != null)
			menuItemSend.setEnabled(true);
		if (menuItemClear != null)
			menuItemClear.setEnabled(true);
		updateTextStatus("Connected to service");
		mLoggerBinder = (LoggerBinder) service;
		mLoggerBinder.setCallGooglePlayListener(this);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.i("Budius", "Activity disconnected from service");
		updateTextStatus("Disconnected from service");
		mLoggerBinder = null;
		btnStart.setEnabled(false);
		btnStop.setEnabled(false);
		btnSingleLoc.setEnabled(false);
		if (menuItemSettings != null)
			menuItemSettings.setEnabled(true);
		if (menuItemSend != null)
			menuItemSend.setEnabled(true);
		if (menuItemClear != null)
			menuItemClear.setEnabled(true);
	}

	@Override
	public void CallGooglePlay(ConnectionResult result) {
		Log.i("Budius",
				"Activity. Service informed that it failed to connect to Google Play Services");
		if (result.hasResolution()) {
			try {

				Log.i("Budius",
						"Activity trying to download Google Play Services");

				// Start an Activity that tries to resolve the error
				result.startResolutionForResult(this,
						CONNECTION_FAILURE_RESOLUTION_REQUEST);
				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */
			} catch (IntentSender.SendIntentException e) {
				// Log the error
				Log.e("Budius", e.getMessage());
				updateTextStatus("Shit happend!");
			}
		} else {
			Log.i("Budius", "Activity can't download from Google Play Services");
			updateTextStatus("Can't connect to Google Play Services. Stuff won't work!");
			btnStart.setEnabled(false);
			btnStop.setEnabled(false);
			btnSingleLoc.setEnabled(false);
			if (menuItemSettings != null)
				menuItemSettings.setEnabled(true);
			if (menuItemSend != null)
				menuItemSend.setEnabled(true);
			if (menuItemClear != null)
				menuItemClear.setEnabled(true);
		}
	}

	@Override
	public void updateServiceStatus(int serviceStatus) {
		switch (serviceStatus) {
		case ServiceLogger.STS_CONNECTING_TO_GOOGLE_PLAY:
			btnStart.setEnabled(false);
			btnStop.setEnabled(false);
			btnSingleLoc.setEnabled(false);
			if (menuItemSettings != null)
				menuItemSettings.setEnabled(false);
			if (menuItemSend != null)
				menuItemSend.setEnabled(false);
			if (menuItemClear != null)
				menuItemClear.setEnabled(false);
			updateTextStatus("Connecting to Google Play");
			break;
		case ServiceLogger.STS_CONNECTED_TO_GOOGLE_PLAY:
			btnStart.setEnabled(false);
			btnSingleLoc.setEnabled(false);
			btnStop.setEnabled(true);
			if (menuItemSettings != null)
				menuItemSettings.setEnabled(false);
			if (menuItemSend != null)
				menuItemSend.setEnabled(false);
			if (menuItemClear != null)
				menuItemClear.setEnabled(false);
			updateTextStatus("Connected to Google PLAY");
			break;
		case ServiceLogger.STS_DISCONNECTED_FROM_GOOGLE_PLAY:
			btnStart.setEnabled(true);
			btnSingleLoc.setEnabled(true);
			btnStop.setEnabled(false);
			if (menuItemSettings != null)
				menuItemSettings.setEnabled(true);
			if (menuItemSend != null)
				menuItemSend.setEnabled(true);
			if (menuItemClear != null)
				menuItemClear.setEnabled(true);
			updateTextStatus("Disconnected from Google Play");
			break;
		case ServiceLogger.STS_FAIL_TO_CONNECT_TO_GOOGLE_PLAY:
			btnStart.setEnabled(false);
			btnStop.setEnabled(false);
			btnSingleLoc.setEnabled(false);
			if (menuItemSettings != null)
				menuItemSettings.setEnabled(true);
			if (menuItemSend != null)
				menuItemSend.setEnabled(true);
			if (menuItemClear != null)
				menuItemClear.setEnabled(true);
			updateTextStatus("Failed to connect to Google Play");
			break;
		}
	}

	@Override
	public void newLocation(Location location) {
		txtStatus.setText("Status: receiving...");

		if (txtTime1.length() == 0){
			txtTime1.setText("Oldest location: " + dateFormat.format(new Date(location.getTime())));
		}

		txtTime2.setText("Most recent location: " + dateFormat.format(new Date(location.getTime())));
	}

	private void updateTextStatus(String status) {
		Log.i("Budius", "new status: " + status);
		txtStatus.setText("Status: " + status);
	}
}
