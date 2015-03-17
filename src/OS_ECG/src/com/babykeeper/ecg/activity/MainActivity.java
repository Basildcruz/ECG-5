package com.babykeeper.ecg.activity;

import java.util.Timer;
import java.util.TimerTask;

import cn.fly2think.btlib.BluetoothService;

import com.babykeeper.ecg.R;
import com.babykeeper.ecg.bean.MyApp;
import com.babykeeper.ecg.util.ToastUtils;
import com.babykeeper.ecg.view.ECGSurfaceView;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener {

	private final int count = 10;// 每次画图推的数目 ？
	private final int time = 10;// time随着时间增大而增大则比较好 ？

	// Intent 请求类型
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// 连接的蓝牙设备名
	private String mConnectedDeviceName = null;

	// 本地蓝牙管理器
	private BluetoothAdapter mBluetoothAdapter = null;
	// 蓝牙连接服务
	private BluetoothService mBTService = null;

	private Button btn_connect;
	private ECGSurfaceView ecg_view;

	private int[] dataCount1;// 没用到
	private int[] dataCount;// 存储要推的数据，一次10个
	private int counterAll = 0; // 统计屏幕画了多少个数据了
	private int counterAllPre = 0;// 移动部分左边一共有多少个点
	private int indexTemp = 0;
	private int maxnum = 512; // 屏幕可以最多画多少个数据
	private int flag = 0;//

	private boolean isConnect = false;// 蓝牙设否断开的标志位
	private boolean isDraw = false;

	private MyTimerTask task = null;
	private Timer timer = null;

	// 蓝牙连接服务通过Handle传递的数据
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case BluetoothService.MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
				// 连接成功
				case BluetoothService.STATE_CONNECTED:

					isConnect = true;

					btn_connect.setText(R.string.disconnect);
					btn_connect.setEnabled(true);
					// 连接成功后开始绘制心电图
					startPaintTimer();
					break;
				// 连接中
				case BluetoothService.STATE_CONNECTING:
					btn_connect.setEnabled(false);
					btn_connect.setText("连接中...");
					break;
				// 未连接
				case BluetoothService.STATE_LISTEN:
				case BluetoothService.STATE_NONE:
					btn_connect.setEnabled(true);
					btn_connect.setText(R.string.connect);
					break;
				}
				break;
			case BluetoothService.MESSAGE_WRITE:

				break;
			// 读取到的蓝牙数据
			case BluetoothService.MESSAGE_READ:

				isDraw = true;

				byte[] readBuf = (byte[]) msg.obj;
				// 转换为整数
				int value = ((0x0FF & readBuf[1]) << 8) + (0x0FF & readBuf[2]);
				MyApp.getInstance().addValue(value);
				break;
			case BluetoothService.MESSAGE_DEVICE_NAME:
				mConnectedDeviceName = msg.getData().getString(
						BluetoothService.DEVICE_NAME);

				ToastUtils.showToast(MainActivity.this, "连接到"
						+ mConnectedDeviceName);
				break;
			case BluetoothService.MESSAGE_TOAST:

				switch (msg.arg1) {

				// 连接失败
				case BluetoothService.MESSAGE_FAILED_CONNECTION:

					ToastUtils.showToast(getApplicationContext(),
							R.string.unable_connect);

					btn_connect.setText(R.string.connect);
					btn_connect.setEnabled(true);

					break;

				// 连接断开
				case BluetoothService.MESSAGE_LOST_CONNECTION:

					btn_connect.setText(R.string.connect);

					isConnect = false;
					MyApp.getInstance().clearValue();
					stopPaintTimer();
					ToastUtils.showToast(getApplicationContext(),
							R.string.lost_connection);

					break;

				default:
					break;
				}

				break;

			case BluetoothService.MESSAGE_NOT_CONNECT:

				ToastUtils.showToast(getApplicationContext(),
						R.string.not_connected);

				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);

		// 获取蓝牙连接管理器
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// 不支持蓝牙则退出App
		if (mBluetoothAdapter == null) {
			ToastUtils.showToast(this, R.string.not_support);
			finish();
			return;
		}

		btn_connect = (Button) findViewById(R.id.btn_connect);
		btn_connect.setOnClickListener(this);

		ecg_view = (ECGSurfaceView) findViewById(R.id.ecg_view);

		dataCount = new int[count];
	}

	@Override
	public void onStart() {
		super.onStart();
		// 打开蓝牙开关
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		} else {
			if (mBTService == null) {
				// 初始化蓝牙连接服务
				mBTService = new BluetoothService(this, mHandler);
			}
		}

	}

	@Override
	public synchronized void onResume() {
		super.onResume();

		if (mBTService != null) {
			if (mBTService.getState() == BluetoothService.STATE_NONE) {
				// 开始蓝牙连接服务
				mBTService.start();
			}
		}

	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		maxnum = ecg_view.getWidth() / ECGSurfaceView.POINT_DIS;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// 停止蓝牙连接服务
		if (mBTService != null)
			mBTService.stop();

		MyApp.getInstance().clearValue();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_connect:

			// 点击连接按钮事件
			if (mBTService.getState() == BluetoothService.STATE_NONE
					|| mBTService.getState() == BluetoothService.STATE_LISTEN) {

				Intent serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);

			} else {

				if (mBTService != null)
					mBTService.stop();
				btn_connect.setText(R.string.connect);
			}

			break;

		default:
			break;
		}
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// 获取需要连接的蓝牙设备
			if (resultCode == Activity.RESULT_OK) {
				// 获取蓝牙设备的MAC地址
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// 获取蓝牙设备对象
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// 开始连接
				mBTService.connect(device);
			}
			break;
		case REQUEST_ENABLE_BT:

			if (resultCode == Activity.RESULT_OK) {
				// 打开蓝牙开关后，初始化蓝牙连接服务
				mBTService = new BluetoothService(this, mHandler);
			} else {

				ToastUtils.showToast(this, R.string.not_connected);
				finish();
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		// 按返回键提示是否退出程序
		if (keyCode == KeyEvent.KEYCODE_BACK) {

			AlertDialog dialog = new Builder(this)
					.setTitle(R.string.exit_title)
					.setMessage(R.string.exit_message)
					.setPositiveButton(R.string.exit_ok,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									finish();
								}
							}).setNegativeButton(R.string.exit_no, null)
					.create();
			dialog.setCanceledOnTouchOutside(false);
			dialog.show();

			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	/**
	 * 注意事项：每次放定时任务前，确保之前任务已从定时器队列中移除 每次放任务都要新建一个对象，否则出现一下错误：
	 * ERROR/AndroidRuntime(11761): java.lang.IllegalStateException: TimerTask
	 * is scheduled already 所以同一个定时器任务只能被放置一次
	 */
	// 如果list的长度大于count则执行的操作
	private void OperationMoreTcount() {

		if (flag == 1) {
			dataCount1 = new int[maxnum - counterAllPre];
			// 取出maxnum个中剩下的
			for (int i = 0; i < (maxnum - counterAllPre); i++) {
				try {
					dataCount1[i] = MyApp.getInstance().getValue();
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("运行异常");
					return;
				}
			}
			counterAll += (maxnum - counterAllPre);
			// 画图
			Log.v("draw", "PaintThread 1:" + indexTemp + ";"
					+ dataCount1.length + ";" + counterAll + ";"
					+ counterAllPre);

			ecg_view.realTimeDraw(indexTemp, dataCount, counterAll,
					counterAllPre);

			indexTemp = 0;
			counterAll = 0;
			counterAllPre = 0;
			flag = 0;
		} else {
			// 把list的count个数据取出来,dataCount为一个大小为count的数组
			for (int i = 0; i < count; i++) {
				try {
					dataCount[i] = MyApp.getInstance().getValue();
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			}
			// 统计总数
			counterAll += dataCount.length;

			ecg_view.realTimeDraw(indexTemp, dataCount, counterAll,
					counterAllPre);
			// 取得最后一个元素
			indexTemp = dataCount[(dataCount.length - 1)];

			if (counterAll == maxnum) {
				counterAll = 0;
				counterAllPre = 0;
				flag = 0;
			} else {
				counterAllPre += dataCount.length;
				if (counterAllPre == maxnum) {
					flag = 0;
				} else if ((counterAllPre + count > maxnum)
						&& (counterAllPre < maxnum)) {
					flag = 1;
				}
			}
		}

	}

	/*------------------------------------------------------定时器操作---------------------------------------------------*/
	class MyTimerTask extends TimerTask {
		@Override
		public void run() {
			while (task != null) {
				if (!MyApp.getInstance().isEmpty()) {
					if (!isConnect) {
						if (MyApp.getInstance().getSize() >= count)
							OperationMoreTcount();
						else {
							stopPaintTimer();
							MyApp.getInstance().clearValue();
							isConnect = true;
						}

					}

					if (isDraw && isConnect) {
						if (MyApp.getInstance().getSize() >= count)
							OperationMoreTcount();
					}

				}
				try {
					Thread.sleep(time);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// 开启定时器
	public void startPaintTimer() {
		if (task != null) {
			task.cancel(); // 将原任务从队列中移除
		}

		reset();

		task = new MyTimerTask();
		timer = new Timer();
		// 周期执行task,从0时开始，每隔100ms进行一次
		try {
			// timer.schedule(task, 0, time);
			timer.schedule(task, time);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	// 停止定时器
	private void stopPaintTimer() {

		if (timer != null) {
			timer.cancel();
			timer = null;
		}

		if (task != null) {
			task.cancel();
			task = null;
		}

	}

	// 重置绘制心电图相关变量
	private void reset() {

		counterAll = 0;
		counterAllPre = 0;
		indexTemp = 0;
		flag = 0;

	}
}
