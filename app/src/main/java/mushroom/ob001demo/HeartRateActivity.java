package mushroom.ob001demo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.Timer;
import java.util.TimerTask;

public class HeartRateActivity extends Activity {
	private final static String TAG = HeartRateActivity.class.getSimpleName();

	private LineChart chart;
	private float heartRateData;

	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	private TextView connectionState;
	private String deviceName;
	private String deviceAddress;
	private Timer timer;
	private float currentTime;
	private BluetoothLeService bluetoothLeService;


	private final ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			bluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (!bluetoothLeService.initialize()) {
				finish();
			}
			bluetoothLeService.connect(deviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			bluetoothLeService = null;
		}
	};

	private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				updateConnectionState(R.string.connected);
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				updateConnectionState(R.string.disconnected);
			} else if (BluetoothLeService.ACTION_HEART_RATE_SERVICES_DISCOVERED.equals(action)) {
				updateConnectionState(R.string.found_heart_rate);
			} else if (BluetoothLeService.ACTION_HEART_RATE_DATA_AVAILABLE.equals(action)) {
				updateHeartRateData(intent);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.heart_rate_activity);

		setTitle("Heart Rate Measurement");

		final Intent intent = getIntent();
		deviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

		getActionBar().setTitle(deviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);

		connectionState = findViewById(R.id.connection_state);

		chart = findViewById(R.id.chart1);
		initializeChart();

	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(gattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
		bluetoothLeService = null;
		if (timer != null) {
			timer.purge();
			timer = null;
		}
	}

	private void initializeChart() {
		chart.setTouchEnabled(true);

		chart.setDragEnabled(true);
		chart.setScaleEnabled(true);
		chart.setDrawGridBackground(false);
		chart.setPinchZoom(true);
		chart.setBackgroundColor(Color.BLACK);

		LineData data = new LineData();
		data.setValueTextColor(Color.WHITE);
		chart.setData(data);

		Legend l = chart.getLegend();

		l.setTextColor(Color.WHITE);

		XAxis xl = chart.getXAxis();
		xl.setTextColor(Color.WHITE);
		xl.setDrawGridLines(false);
		xl.setAvoidFirstLastClipping(true);
		xl.setEnabled(true);
		xl.setGranularity(5);
		xl.setGranularityEnabled(true);

		YAxis leftAxis = chart.getAxisLeft();
		leftAxis.setTextColor(Color.WHITE);
		leftAxis.setAxisMaximum(140);
		leftAxis.setAxisMinimum(20f);
		leftAxis.setDrawGridLines(true);

		YAxis rightAxis = chart.getAxisRight();
		rightAxis.setEnabled(false);
	}

	private void addChartEntry(float time, float heartRate) {

		LineData data = chart.getData();

		if (data != null) {

			ILineDataSet set = data.getDataSetByIndex(0);

			if (set == null) {
				set = createChartSet();
				data.addDataSet(set);
			}

			data.addEntry(new Entry(time, heartRate), 0);
			data.notifyDataChanged();

			chart.notifyDataSetChanged();

			chart.setVisibleXRangeMaximum(30);
			chart.setVisibleXRangeMinimum(30);

			chart.moveViewToX(data.getEntryCount());
		}
	}

	private LineDataSet createChartSet() {

		LineDataSet set = new LineDataSet(null, "Heart Rate");
		set.setAxisDependency(YAxis.AxisDependency.LEFT);
		set.setColor(ColorTemplate.getHoloBlue());
		set.setCircleColor(ColorTemplate.getHoloBlue());
		set.setLineWidth(2f);
		set.setCircleRadius(1f);
		set.setFillAlpha(65);
		set.setFillColor(ColorTemplate.getHoloBlue());
		set.setHighLightColor(Color.rgb(244, 117, 117));
		set.setValueTextColor(Color.WHITE);
		set.setValueTextSize(9f);
		set.setDrawValues(false);
		return set;
	}


	private void updateHeartRateData(Intent intent) {
		heartRateData = Float.valueOf(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
		startTimer();
	}

	private void startTimer() {
		if (timer == null) {
			currentTime = 0f;
			timer = new Timer();
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							addChartEntry(currentTime, heartRateData);
							currentTime += 0.05;
						}
					});
				}
			}, 50, 50);
		}
	}

	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				connectionState.setText(resourceId);
			}
		});
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_HEART_RATE_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_HEART_RATE_DATA_AVAILABLE);
		return intentFilter;
	}
}
