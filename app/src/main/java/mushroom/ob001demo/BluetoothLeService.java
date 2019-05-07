package mushroom.ob001demo;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.List;
import java.util.UUID;

public class BluetoothLeService extends Service {
	private final static String TAG = BluetoothLeService.class.getSimpleName();

	public final static String ACTION_GATT_CONNECTED = "ob001demo.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "ob001demo.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_HEART_RATE_SERVICES_DISCOVERED = "ob001demo.ACTION_HEART_RATE_SERVICES_DISCOVERED";
	public final static String ACTION_HEART_RATE_DATA_AVAILABLE = "ob001demo.ACTION_HEART_RATE_DATA_AVAILABLE";
	public final static String EXTRA_DATA = "ob001demo.EXTRA_DATA";

	public final static UUID UUID_HEART_RATE_MEASUREMENT =
			UUID.fromString(GattAttributes.HEART_RATE_MEASUREMENT);

	private final IBinder binder = new LocalBinder();
	private BluetoothManager bluetoothManager;
	private BluetoothAdapter bluetoothAdapter;
	private String bluetoothDeviceAddress;
	private BluetoothGatt bluetoothGatt;


	private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = ACTION_GATT_CONNECTED;
				broadcastUpdate(intentAction, null);
				bluetoothGatt.discoverServices();

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				broadcastUpdate(intentAction, null);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				List<BluetoothGattService> gattServices = bluetoothGatt.getServices();
				for (BluetoothGattService gattService : gattServices) {
					List<BluetoothGattCharacteristic> gattCharacteristics =
							gattService.getCharacteristics();
					for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
						UUID uuid = gattCharacteristic.getUuid();
						if (UUID_HEART_RATE_MEASUREMENT.equals(uuid)) {
							broadcastUpdate(ACTION_HEART_RATE_SERVICES_DISCOVERED, null);
							setCharacteristicNotification(gattCharacteristic, true);
						}
					}
				}

			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
											BluetoothGattCharacteristic characteristic) {
			broadcastUpdate(ACTION_HEART_RATE_DATA_AVAILABLE, characteristic);
		}
	};

	private void broadcastUpdate(final String action,
								 final BluetoothGattCharacteristic characteristic) {
		final Intent intent = new Intent(action);
		if (characteristic != null) {
			if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
				int heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
				intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
			}
		}
		sendBroadcast(intent);
	}

	public class LocalBinder extends Binder {
		BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		close();
		return super.onUnbind(intent);
	}


	public boolean initialize() {
		bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		if (bluetoothManager == null) {
			return false;
		}

		bluetoothAdapter = bluetoothManager.getAdapter();
		if (bluetoothAdapter == null) {
			return false;
		}

		return true;
	}

	public boolean connect(final String address) {
		if (bluetoothAdapter == null || address == null) {
			return false;
		}

		if (address.equals(bluetoothDeviceAddress)
				&& bluetoothGatt != null) {
			return bluetoothGatt.connect();
		}

		final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			return false;
		}
		bluetoothGatt = device.connectGatt(this, false, gattCallback);
		bluetoothDeviceAddress = address;
		return true;
	}


	public void close() {
		if (bluetoothGatt != null) {
			bluetoothGatt.close();
			bluetoothGatt = null;
		}
	}

	public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
											  boolean enabled) {
		if (bluetoothAdapter == null || bluetoothGatt == null) {
			return;
		}
		bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

		if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
					UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			bluetoothGatt.writeDescriptor(descriptor);
		}
	}
}
