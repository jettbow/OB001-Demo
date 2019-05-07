package mushroom.ob001demo;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceScanActivity extends ListActivity {
	private static final int REQUEST_ENABLE_BT = 1;
	private static final long SCAN_PERIOD = 10000;

	private LeDeviceListAdapter leDeviceListAdapter;
	private BluetoothAdapter bluetoothAdapter;
	private boolean scanning;
	private Handler handler;

	private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					leDeviceListAdapter.addDevice(device);
					leDeviceListAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBar().setTitle(R.string.app_name);
		handler = new Handler();

		BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		bluetoothAdapter = bluetoothManager.getAdapter();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.device_scan_menu, menu);
		if (!scanning) {
			menu.findItem(R.id.menu_stop).setVisible(false);
			menu.findItem(R.id.menu_scan).setVisible(true);
			menu.findItem(R.id.menu_refresh).setActionView(null);
		} else {
			menu.findItem(R.id.menu_stop).setVisible(true);
			menu.findItem(R.id.menu_scan).setVisible(false);
			menu.findItem(R.id.menu_refresh).setActionView(
					R.layout.actionbar_indeterminate_progress);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_scan:
				leDeviceListAdapter.clear();
				scanLeDevice(true);
				break;
			case R.id.menu_stop:
				scanLeDevice(false);
				break;
		}
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
			}
		}

		leDeviceListAdapter = new LeDeviceListAdapter();
		setListAdapter(leDeviceListAdapter);
		scanLeDevice(true);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		scanLeDevice(false);
		leDeviceListAdapter.clear();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final BluetoothDevice device = leDeviceListAdapter.getDevice(position);
		if (device == null) return;
		final Intent intent = new Intent(this, HeartRateActivity.class);
		intent.putExtra(HeartRateActivity.EXTRAS_DEVICE_NAME, device.getName());
		intent.putExtra(HeartRateActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
		if (scanning) {
			bluetoothAdapter.stopLeScan(leScanCallback);
			scanning = false;
		}
		startActivity(intent);
	}

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					scanning = false;
					bluetoothAdapter.stopLeScan(leScanCallback);
					invalidateOptionsMenu();
				}
			}, SCAN_PERIOD);

			scanning = true;
			bluetoothAdapter.startLeScan(leScanCallback);
		} else {
			scanning = false;
			bluetoothAdapter.stopLeScan(leScanCallback);
		}
		invalidateOptionsMenu();
	}

	private class LeDeviceListAdapter extends BaseAdapter {
		private ArrayList<BluetoothDevice> leDevices;
		private LayoutInflater inflater;

		public LeDeviceListAdapter() {
			super();
			leDevices = new ArrayList<>();
			inflater = DeviceScanActivity.this.getLayoutInflater();
		}

		public void addDevice(BluetoothDevice device) {
			if (!leDevices.contains(device)) {
				leDevices.add(device);
			}
		}

		public BluetoothDevice getDevice(int position) {
			return leDevices.get(position);
		}

		public void clear() {
			leDevices.clear();
		}

		@Override
		public int getCount() {
			return leDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return leDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			if (view == null) {
				view = inflater.inflate(R.layout.device_scan_activity, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceAddress = view.findViewById(R.id.device_address);
				viewHolder.deviceName = view.findViewById(R.id.device_name);
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			BluetoothDevice device = leDevices.get(i);
			final String deviceName = device.getName();
			if (deviceName != null && deviceName.length() > 0)
				viewHolder.deviceName.setText(deviceName);
			else
				viewHolder.deviceName.setText(R.string.unknown_device);
			viewHolder.deviceAddress.setText(device.getAddress());

			return view;
		}
	}


	static class ViewHolder {
		TextView deviceName;
		TextView deviceAddress;
	}
}