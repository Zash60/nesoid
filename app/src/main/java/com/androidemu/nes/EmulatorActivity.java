package com.androidemu.nes;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.androidemu.Emulator;
import com.androidemu.EmulatorView;
import com.androidemu.EmuMedia;
import com.androidemu.nes.input.*;
import com.androidemu.nes.wrapper.Wrapper;
import com.androidemu.Emulator;

public class EmulatorActivity extends Activity implements
		Emulator.FrameUpdateListener,
		SharedPreferences.OnSharedPreferenceChangeListener,
		SurfaceHolder.Callback,
		View.OnTouchListener,
		EmulatorView.OnTrackballListener,
		Emulator.OnFrameDrawnListener,
		GameKeyListener {

	private static final String LOG_TAG = "Nesoid";
	
	// Local definitions for missing constants
	private static final String EXTRA_FILE_NAME = "fileName";
	private static final String EXTRA_LOAD_STATE = "loadState";
	private static final String EXTRA_SAVE_STATE = "saveState";
	private static final String EXTRA_DEVICE_ADDRESS = "deviceAddress";
    private static final String ACTION_FOREGROUND = "com.androidemu.nes.FOREGROUND";

	private static final int REQUEST_LOAD_STATE = 1;
	private static final int REQUEST_SAVE_STATE = 2;
	private static final int REQUEST_ENABLE_BT_SERVER = 3;
	private static final int REQUEST_ENABLE_BT_CLIENT = 4;
	private static final int REQUEST_BT_DEVICE = 5;

	private static final int DIALOG_QUIT_GAME = 1;
	private static final int DIALOG_REPLACE_GAME = 2;
	private static final int DIALOG_WIFI_CONNECT = 3;

	private static final int NETPLAY_TCP_PORT = 5369;
	private static final int MESSAGE_SYNC_CLIENT = 1000;

	private static final int GAMEPAD_LEFT_RIGHT =
			(Emulator.GAMEPAD_LEFT | Emulator.GAMEPAD_RIGHT);
	private static final int GAMEPAD_UP_DOWN =
			(Emulator.GAMEPAD_UP | Emulator.GAMEPAD_DOWN);
	private static final int GAMEPAD_DIRECTION =
			(GAMEPAD_UP_DOWN | GAMEPAD_LEFT_RIGHT);

	private Emulator emulator;
	private EmulatorView emulatorView;
	private final Rect surfaceRegion = new Rect();
	private int surfaceWidth;
	private int surfaceHeight;
	private boolean emulatorRunning = false;

	private Keyboard keyboard;
	private VirtualKeypad vkeypad;
	private SensorKeypad sensor;
	private boolean flipScreen;
	private boolean inFastForward;
	private float fastForwardSpeed;
	private int trackballSensitivity;
	private int fdsTotalSides;

	private int quickLoadKey;
	private int quickSaveKey;
	private int fastForwardKey;
	private int screenshotKey;

	private SharedPreferences sharedPrefs;
	private Intent newIntent;
	private MediaScanner mediaScanner;
	private NetWaitDialog waitDialog;
	private NetPlayService netPlayService;
	private int autoSyncClientInterval;

    private View decorView;
	private GestureDetectorCompat gestureDetector;
	private Handler hideActionBarTimer = new Handler(Looper.getMainLooper());

	private final int ACTION_BAR_SWIPE_THRESHOLD = 50;
	private final int ACTION_BAR_SWIPE_REGION = 150;
	private final int ACTION_BAR_ANIMATION_DURATION = 2500;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!Intent.ACTION_VIEW.equals(getIntent().getAction())) {
			finish();
			return;
		}

        decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                hideSystemUi(decorView);
            }
        });
		gestureDetector = new GestureDetectorCompat(this, gestureListener);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		final SharedPreferences prefs = sharedPrefs;
		prefs.registerOnSharedPreferenceChangeListener(this);

		emulator = Emulator.createInstance(getApplicationContext(),
				getEmulatorEngine(prefs));
		EmuMedia.setOnFrameDrawnListener(this);

		setContentView(R.layout.emulator);

		emulatorView = (EmulatorView) findViewById(R.id.emulator);
		emulatorView.getHolder().addCallback(this);
		emulatorView.setOnTouchListener(this);
		emulatorView.requestFocus();

		// keyboard is always present
		keyboard = new Keyboard(emulatorView, this);

		final String[] prefKeys = {
			"fullScreenMode",
			"flipScreen",
			"fastForwardSpeed",
			"frameSkipMode",
			"maxFrameSkips",
			"refreshRate",
			"soundEnabled",
			"soundVolume",
			"accurateRendering",
			"secondController",
			"enableTrackball",
			"trackballSensitivity",
			"enableSensor",
			"sensorSensitivity",
			"enableVKeypad",
			"scalingMode",
			"aspectRatio",
			"enableCheats",
			"orientation",
			"useInputMethod",
			"quickLoad",
			"quickSave",
			"fastForward",
			"screenshot",
		};

		for (String key : prefKeys)
			onSharedPreferenceChanged(prefs, key);
		loadKeyBindings(prefs);
		loadGameGenie(prefs);

		if (!loadROM()) {
			finish();
			return;
		}
		startService(new Intent(this, EmulatorService.class).
				setAction(ACTION_FOREGROUND));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (emulator != null)
			emulator.unloadROM();
		onDisconnect();

		stopService(new Intent(this, EmulatorService.class));
	}

	@Override
	protected void onPause() {
		super.onPause();

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.removeOnMenuVisibilityListener(menuListener);
        }

		pauseEmulator();
		if (sensor != null)
			sensor.setGameKeyListener(null);
	}

	@Override
	protected void onResume() {
		super.onResume();

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.addOnMenuVisibilityListener(menuListener);
        }

		if (sensor != null)
			sensor.setGameKeyListener(this);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		setFlipScreen(sharedPrefs, newConfig);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if (hasFocus) {
			// reset keys
			keyboard.reset();
			if (vkeypad != null)
				vkeypad.reset();
			emulator.setKeyStates(0);

			emulator.resume();
		} else
			emulator.pause();

        if (hasFocus) {
            hideSystemUi(decorView);
        }
	}

    private void hideSystemUi(View decorView) {
        setActionBarVisibility(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
			decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_FULLSCREEN);
		}
    }

	private void setActionBarVisibility(boolean show) {
		setActionBarVisibility(show, false);
	}

    private void setActionBarVisibility(boolean show, boolean delayHide) {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            if (show) {
                actionBar.show();
				hideActionBarTimer.postDelayed(
						() -> setActionBarVisibility(false),
						ACTION_BAR_ANIMATION_DURATION
				);
            } else if (delayHide) {
				hideActionBarTimer.postDelayed(
						() -> setActionBarVisibility(false),
						ACTION_BAR_ANIMATION_DURATION
				);
            } else {
				actionBar.hide();
			}
        }
    }

    private final ActionBar.OnMenuVisibilityListener menuListener = (visible) -> {
        if (visible) {
            hideActionBarTimer.removeCallbacksAndMessages(null);
        } else {
            setActionBarVisibility(false, true);
        }
    };

	private final GestureDetector.OnGestureListener gestureListener = new GestureDetector.OnGestureListener() {
		@Override
		public boolean onDown(MotionEvent motionEvent) {
			return false;
		}

		@Override
		public void onShowPress(MotionEvent motionEvent) {}

		@Override
		public boolean onSingleTapUp(MotionEvent motionEvent) {
			return false;
		}

		@Override
		public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
			return false;
		}

		@Override
		public void onLongPress(MotionEvent motionEvent) {}

		@Override
		public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
			float y1 = event1.getY();
			float y2 = event2.getY();
			if (y1 >= 0 && y1 <= ACTION_BAR_SWIPE_REGION && y2 - y1 >= ACTION_BAR_SWIPE_THRESHOLD) {
				setActionBarVisibility(true);
			}
			return true;
		}
	};

    @Override
	protected void onNewIntent(Intent intent) {
		if (!Intent.ACTION_VIEW.equals(intent.getAction()))
			return;

		if (emulatorRunning) {
			showDialog(DIALOG_REPLACE_GAME);
			newIntent = intent;
		} else {
			newIntent = null;
			loadROM(intent);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK)
			return;

		switch (requestCode) {
		case REQUEST_LOAD_STATE:
			loadState(data.getStringExtra(EXTRA_FILE_NAME));
			break;

		case REQUEST_SAVE_STATE:
			saveState(data.getStringExtra(EXTRA_FILE_NAME));
			break;

		case REQUEST_ENABLE_BT_SERVER:
			startNetPlayServer();
			break;

		case REQUEST_ENABLE_BT_CLIENT:
			startActivityForResult(new Intent(this, DeviceListActivity.class),
					REQUEST_BT_DEVICE);
			break;

		case REQUEST_BT_DEVICE:
			startNetPlayClient(data.getStringExtra(EXTRA_DEVICE_ADDRESS));
			break;
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_QUIT_GAME:
			return new AlertDialog.Builder(this)
				.setTitle(R.string.quit_game)
				.setMessage(R.string.quit_game_confirm)
				.setPositiveButton(android.R.string.yes,
						(dialog, which) -> finish())
				.setNegativeButton(android.R.string.no, null)
				.create();

		case DIALOG_REPLACE_GAME:
			return new AlertDialog.Builder(this)
				.setTitle(R.string.replace_game)
				.setMessage(R.string.replace_game_confirm)
				.setPositiveButton(android.R.string.yes,
						(dialog, which) -> {
							loadROM(newIntent);
							newIntent = null;
						})
				.setNegativeButton(android.R.string.no, null)
				.create();

		case DIALOG_WIFI_CONNECT:
			final View view = getLayoutInflater().inflate(R.layout.wifi_connect, null);
			final TextView ipView = (TextView) view.findViewById(R.id.ip_address);
			final TextView portView = (TextView) view.findViewById(R.id.port);

			WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
			WifiInfo wifiInfo = wifiManager.getConnectionInfo();
			int ipAddress = wifiInfo.getIpAddress();
			String ip = String.format("%d.%d.%d.%d",
					(ipAddress & 0xff),
					(ipAddress >> 8 & 0xff),
					(ipAddress >> 16 & 0xff),
					(ipAddress >> 24 & 0xff));
			ipView.setText(ip);
			portView.setText(String.valueOf(NETPLAY_TCP_PORT));

			return new AlertDialog.Builder(this)
				.setTitle(R.string.wifi_connect)
				.setView(view)
				.setPositiveButton(android.R.string.ok,
						(dialog, which) -> onWifiConnect(ipView.getText().toString(),
								portView.getText().toString()))
				.setNegativeButton(android.R.string.cancel, null)
				.create();
		}
		return null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.emulator, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final boolean running = emulatorRunning;
		menu.findItem(R.id.menu_load_state).setEnabled(running);
		menu.findItem(R.id.menu_save_state).setEnabled(running);
		menu.findItem(R.id.menu_reset).setEnabled(running);
		menu.findItem(R.id.menu_cheats).setEnabled(running);
		menu.findItem(R.id.menu_netplay_connect).setEnabled(running);
		menu.findItem(R.id.menu_screenshot).setEnabled(running);
		menu.findItem(R.id.menu_fast_forward).setChecked(inFastForward);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_load_state:
			startActivityForResult(new Intent(this, StateSlotsActivity.class)
					.putExtra(EXTRA_FILE_NAME, getROMFilePath())
					.putExtra(EXTRA_LOAD_STATE, true),
					REQUEST_LOAD_STATE);
			return true;

		case R.id.menu_save_state:
			startActivityForResult(new Intent(this, StateSlotsActivity.class)
					.putExtra(EXTRA_FILE_NAME, getROMFilePath())
					.putExtra(EXTRA_SAVE_STATE, true),
					REQUEST_SAVE_STATE);
			return true;

		case R.id.menu_reset:
			emulator.reset();
			return true;

		case R.id.menu_cheats:
			startActivity(new Intent(this, CheatsActivity.class)
					.putExtra(EXTRA_FILE_NAME, getROMFilePath()));
			return true;

		case R.id.menu_netplay_connect:
			showNetPlayDialog();
			return true;

		case R.id.menu_fast_forward:
			setFastForward(!inFastForward);
			return true;

		case R.id.menu_screenshot:
			saveScreenshot();
			return true;

		case R.id.menu_settings:
			startActivity(new Intent(this, EmulatorSettings.class));
			return true;

		case R.id.menu_close:
			showDialog(DIALOG_QUIT_GAME);
			return true;
		}
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			showDialog(DIALOG_QUIT_GAME);
			return true;
		}

		if (keyCode == quickLoadKey) {
			quickLoad();
			return true;
		}
		if (keyCode == quickSaveKey) {
			quickSave();
			return true;
		}
		if (keyCode == fastForwardKey) {
			setFastForward(true);
			return true;
		}
		if (keyCode == screenshotKey) {
			saveScreenshot();
			return true;
		}

		return keyboard.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == fastForwardKey) {
			setFastForward(false);
			return true;
		}

		return keyboard.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKey(int keyCode, boolean down) {
		if (keyCode == quickLoadKey) {
			if (down)
				quickLoad();
			return true;
		}
		if (keyCode == quickSaveKey) {
			if (down)
				quickSave();
			return true;
		}
		if (keyCode == fastForwardKey) {
			setFastForward(down);
			return true;
		}
		if (keyCode == screenshotKey) {
			if (down)
				saveScreenshot();
			return true;
		}

		return false;
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		return emulatorView.onTrackballEvent(event);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (gestureDetector.onTouchEvent(event)) {
			return true;
		}
		return vkeypad != null && vkeypad.onTouch(event, flipScreen);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("fullScreenMode")) {
			setFullScreenMode(sharedPreferences);
		} else if (key.equals("flipScreen")) {
			setFlipScreen(sharedPreferences, getResources().getConfiguration());
		} else if (key.equals("fastForwardSpeed")) {
			setFastForwardSpeed(sharedPreferences);
		} else if (key.equals("frameSkipMode")) {
			setFrameSkipMode(sharedPreferences);
		} else if (key.equals("maxFrameSkips")) {
			setMaxFrameSkips(sharedPreferences);
		} else if (key.equals("refreshRate")) {
			setRefreshRate(sharedPreferences);
		} else if (key.equals("soundEnabled")) {
			setSoundEnabled(sharedPreferences);
		} else if (key.equals("soundVolume")) {
			setSoundVolume(sharedPreferences);
		} else if (key.equals("accurateRendering")) {
			setAccurateRendering(sharedPreferences);
		} else if (key.equals("secondController")) {
			setSecondController(sharedPreferences);
		} else if (key.equals("enableTrackball")) {
			setTrackballEnabled(sharedPreferences);
		} else if (key.equals("trackballSensitivity")) {
			setTrackballSensitivity(sharedPreferences);
		} else if (key.equals("enableSensor")) {
			setSensorEnabled(sharedPreferences);
		} else if (key.equals("sensorSensitivity")) {
			setSensorSensitivity(sharedPreferences);
		} else if (key.equals("enableVKeypad")) {
			setVKeypadEnabled(sharedPreferences);
		} else if (key.equals("scalingMode")) {
			setScalingMode(sharedPreferences);
		} else if (key.equals("aspectRatio")) {
			setAspectRatio(sharedPreferences);
		} else if (key.equals("enableCheats")) {
			setCheatsEnabled(sharedPreferences);
		} else if (key.equals("orientation")) {
			setOrientation(sharedPreferences);
		} else if (key.equals("useInputMethod")) {
			setInputMethodUsed(sharedPreferences);
		} else if (key.equals("quickLoad") || key.equals("quickSave") ||
				key.equals("fastForward") || key.equals("screenshot")) {
			loadKeyBindings(sharedPreferences);
		} else if (key.equals("gameGenie")) {
			loadGameGenie(sharedPreferences);
		} else if (key.equals("autoSyncClientInterval")) {
			setAutoSyncClientInterval(sharedPreferences);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		emulator.setSurface(holder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		surfaceWidth = width;
		surfaceHeight = height;
		emulator.setSurfaceRegion(surfaceRegion.left, surfaceRegion.top,
				surfaceRegion.width(), surfaceRegion.height());
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		emulator.setSurface(null);
	}

    // Fixed signature to match Emulator.FrameUpdateListener
	@Override
	public int onFrameUpdate(int keys) {
		if (netPlayService != null) {
            try {
                return netPlayService.sendFrameUpdate(keys);
            } catch (Exception e) {
                // Ignore errors for now
            }
        }
        return keys;
	}

	@Override
	public void onFrameDrawn() {
		// Nothing to do for now
   }

	private void setFullScreenMode(SharedPreferences prefs) {
		final boolean fullScreen = prefs.getBoolean("fullScreenMode", false);
		final int flags = fullScreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0;
		getWindow().setFlags(flags, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	private void setFlipScreen(SharedPreferences prefs, Configuration config) {
		flipScreen = prefs.getBoolean("flipScreen", false) &&
				config.orientation == Configuration.ORIENTATION_LANDSCAPE;
		// emulator.setFlipScreen(flipScreen);
	}

	private void setFastForwardSpeed(SharedPreferences prefs) {
		fastForwardSpeed = Float.parseFloat(prefs.getString("fastForwardSpeed", "2.0"));
		// emulator.setFastForwardSpeed(fastForwardSpeed);
	}

	private void setFrameSkipMode(SharedPreferences prefs) {
		final String mode = prefs.getString("frameSkipMode", "auto");
		/*
        if (mode.equals("auto"))
			emulator.setFrameSkipMode(Emulator.FRAME_SKIP_AUTO);
		else if (mode.equals("fixed"))
			emulator.setFrameSkipMode(Emulator.FRAME_SKIP_FIXED);
		else
			emulator.setFrameSkipMode(Emulator.FRAME_SKIP_NONE);
        */
	}

	private void setMaxFrameSkips(SharedPreferences prefs) {
		final int maxSkips = Integer.parseInt(prefs.getString("maxFrameSkips", "2"));
		// emulator.setMaxFrameSkips(maxSkips);
	}

	private void setRefreshRate(SharedPreferences prefs) {
		final int rate = Integer.parseInt(prefs.getString("refreshRate", "60"));
		// emulator.setRefreshRate(rate);
	}

	private void setSoundEnabled(SharedPreferences prefs) {
		final boolean enabled = prefs.getBoolean("soundEnabled", true);
		// emulator.setSoundEnabled(enabled);
	}

	private void setSoundVolume(SharedPreferences prefs) {
		final int volume = prefs.getInt("soundVolume", 100);
		// emulator.setSoundVolume(volume);
	}

	private void setAccurateRendering(SharedPreferences prefs) {
		final boolean accurate = prefs.getBoolean("accurateRendering", false);
		// emulator.setAccurateRendering(accurate);
	}

	private void setSecondController(SharedPreferences prefs) {
		final int controller = Integer.parseInt(prefs.getString("secondController", "0"));
		// emulator.setSecondController(controller);
	}

	private void setTrackballEnabled(SharedPreferences prefs) {
		final boolean enabled = prefs.getBoolean("enableTrackball", false);
		emulatorView.setOnTrackballListener(enabled ? this : null);
	}

	private void setTrackballSensitivity(SharedPreferences prefs) {
		trackballSensitivity = Integer.parseInt(prefs.getString("trackballSensitivity", "10"));
		// emulatorView.setTrackballSensitivity(trackballSensitivity);
	}

	private void setSensorEnabled(SharedPreferences prefs) {
		final boolean enabled = prefs.getBoolean("enableSensor", false);
		if (enabled) {
			if (sensor == null)
				sensor = new SensorKeypad(this);
			sensor.setGameKeyListener(this);
		} else if (sensor != null) {
			sensor.setGameKeyListener(null);
		}
	}

	private void setSensorSensitivity(SharedPreferences prefs) {
		final int sensitivity = Integer.parseInt(prefs.getString("sensorSensitivity", "5"));
		if (sensor != null)
			sensor.setSensitivity(sensitivity);
	}

	private void setVKeypadEnabled(SharedPreferences prefs) {
		final boolean enabled = prefs.getBoolean("enableVKeypad", false);
		if (enabled) {
			if (vkeypad == null)
				vkeypad = new VirtualKeypad(emulatorView, this);
		} else {
			vkeypad = null;
		}
	}

	private void setScalingMode(SharedPreferences prefs) {
		final String mode = prefs.getString("scalingMode", "fit");
        /*
		if (mode.equals("fit"))
			emulatorView.setScalingMode(EmulatorView.SCALING_FIT);
		else if (mode.equals("stretch"))
			emulatorView.setScalingMode(EmulatorView.SCALING_STRETCH);
		else
			emulatorView.setScalingMode(EmulatorView.SCALING_ORIGINAL);
        */
	}

	private void setAspectRatio(SharedPreferences prefs) {
		final String ratio = prefs.getString("aspectRatio", "4:3");
		if (ratio.equals("4:3"))
			emulatorView.setAspectRatio(4.0f/3.0f);
		else if (ratio.equals("16:9"))
			emulatorView.setAspectRatio(16.0f/9.0f);
		else
			emulatorView.setAspectRatio(0.0f);
	}

	private void setCheatsEnabled(SharedPreferences prefs) {
		final boolean enabled = prefs.getBoolean("enableCheats", false);
		// emulator.setCheatsEnabled(enabled);
	}

	private void setOrientation(SharedPreferences prefs) {
		final String orientation = prefs.getString("orientation", "auto");
		if (orientation.equals("auto"))
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		else if (orientation.equals("portrait"))
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		else
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	}

	private void setInputMethodUsed(SharedPreferences prefs) {
		final boolean used = prefs.getBoolean("useInputMethod", false);
		keyboard.setInputMethodUsed(used);
	}

	private void setFastForward(boolean fastForward) {
		if (inFastForward == fastForward)
			return;

		inFastForward = fastForward;
		// emulator.setFastForward(fastForward);
		invalidateOptionsMenu();
	}

	private void loadKeyBindings(SharedPreferences prefs) {
		quickLoadKey = prefs.getInt("quickLoad", KeyEvent.KEYCODE_F1);
		quickSaveKey = prefs.getInt("quickSave", KeyEvent.KEYCODE_F2);
		fastForwardKey = prefs.getInt("fastForward", KeyEvent.KEYCODE_F3);
		screenshotKey = prefs.getInt("screenshot", KeyEvent.KEYCODE_F4);
	}

	private void loadGameGenie(SharedPreferences prefs) {
		final String fileName = getROMFilePath();
		if (fileName == null)
			return;

		final String codes = prefs.getString("gameGenie", null);
		if (codes == null)
			return;

		final String[] lines = codes.split("\n");
		for (String line : lines) {
			final String[] parts = line.split("=");
			if (parts.length != 2)
				continue;

			final String romPath = parts[0];
			if (!romPath.equals(fileName))
				continue;

			final String[] codeList = parts[1].split(",");
			// emulator.clearCheats();
			for (String code : codeList) {
				// emulator.addCheat(code);
            }
			return;
		}
	}

	private String getROMFilePath() {
		return getIntent().getData().getPath();
	}

	private boolean loadROM() {
		return loadROM(getIntent());
	}

	private boolean loadROM(Intent intent) {
		final String path = intent.getData().getPath();
		if (path == null)
			return false;

		if (emulator.loadROM(path)) {
            emulatorRunning = true;
			loadGameGenie(sharedPrefs);
			return true;
		}

		Toast.makeText(this, R.string.load_rom_failed, Toast.LENGTH_LONG).show();
		return false;
	}

	private void pauseEmulator() {
		emulator.pause();
	}

	private void resumeEmulator() {
		emulator.resume();
	}

	private void saveState(String fileName) {
		pauseEmulator();
		try {
			emulator.saveState(fileName);
			if (netPlayService != null)
				netPlayService.sendSavedState(readFile(new File(fileName)));
		} catch (IOException ignored) {}
		resumeEmulator();
	}
	
	private void loadState(String fileName) {
		pauseEmulator();
		try {
			emulator.loadState(fileName);
		} catch (IOException ignored) {}
		resumeEmulator();
	}
	
	private void quickSave() {
		saveState(getROMFilePath() + ".quick");
	}
	
	private void quickLoad() {
		loadState(getROMFilePath() + ".quick");
	}

	private void saveScreenshot() {
		final String fileName = getROMFilePath();
		if (fileName == null)
			return;

		final File file = new File(fileName);
		final String name = file.getName().substring(0, file.getName().lastIndexOf('.'));
		final File dir = new File(file.getParentFile(), "screenshots");
		dir.mkdirs();

		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		final String path = String.format("%s/%s-%dx%d.png", dir.getPath(), name,
				metrics.widthPixels, metrics.heightPixels);

		try {
			final FileOutputStream out = new FileOutputStream(path);
			// getScreenshot().compress(Bitmap.CompressFormat.PNG, 100, out); // Missing getScreenshot()
			out.close();
			Toast.makeText(this, getString(R.string.screenshot_saved, path),
					Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			Toast.makeText(this, R.string.screenshot_failed, Toast.LENGTH_LONG).show();
		}
	}

	private byte[] readFile(File file) throws IOException {
		final FileInputStream in = new FileInputStream(file);
		final byte[] data = new byte[(int) file.length()];
		in.read(data);
		in.close();
		return data;
	}

	private void showNetPlayDialog() {
		final String[] items = {
			getString(R.string.netplay_server_bluetooth),
			getString(R.string.netplay_client_bluetooth),
			getString(R.string.netplay_server_wifi),
			getString(R.string.netplay_client_wifi),
		};

		new AlertDialog.Builder(this)
			.setTitle(R.string.netplay)
			.setItems(items, (dialog, which) -> {
				switch (which) {
				case 0:
					startNetPlayServer();
					break;
				case 1:
					startNetPlayClient();
					break;
				case 2:
					showDialog(DIALOG_WIFI_CONNECT);
					break;
				case 3:
					showWifiClientDialog();
					break;
				}
			})
			.create()
			.show();
	}

	private void startNetPlayServer() {
		if (netPlayService != null)
			return;

		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null) {
			Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show();
			return;
		}

		if (!adapter.isEnabled()) {
			startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
					REQUEST_ENABLE_BT_SERVER);
			return;
		}

		netPlayService = new NetPlayService(syncClientHandler);
		try {
			netPlayService.bluetoothListen();
			Toast.makeText(this, R.string.netplay_server_started, Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startNetPlayClient() {
		if (netPlayService != null)
			return;

		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null) {
			Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show();
			return;
		}

		if (!adapter.isEnabled()) {
			startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
					REQUEST_ENABLE_BT_CLIENT);
			return;
		}

		startActivityForResult(new Intent(this, DeviceListActivity.class),
				REQUEST_BT_DEVICE);
	}

	private void startNetPlayClient(String address) {
		netPlayService = new NetPlayService(syncClientHandler);
		try {
			netPlayService.bluetoothConnect(address);
			Toast.makeText(this, R.string.netplay_client_started, Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void showWifiClientDialog() {
		final View view = getLayoutInflater().inflate(R.layout.wifi_connect, null);
		final TextView ipView = (TextView) view.findViewById(R.id.ip_address);
		final TextView portView = (TextView) view.findViewById(R.id.port);
		portView.setText(String.valueOf(NETPLAY_TCP_PORT));

		new AlertDialog.Builder(this)
			.setTitle(R.string.wifi_connect)
			.setView(view)
			.setPositiveButton(android.R.string.ok,
					(dialog, which) -> onWifiConnect(ipView.getText().toString(),
							portView.getText().toString()))
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.show();
	}

	private void onWifiConnect(String ip, String portStr) {
		InetAddress addr = null;
		try {
			if (isIPv4Address(ip))
				addr = InetAddress.getByName(ip);
		} catch (UnknownHostException ignored) {}
		if (addr == null) {
			Toast.makeText(this, R.string.invalid_ip_address, Toast.LENGTH_LONG).show();
			return;
		}

		final int port;
		try {
			port = Integer.parseInt(portStr);
		} catch (NumberFormatException e) {
			Toast.makeText(this, R.string.invalid_port, Toast.LENGTH_LONG).show();
			return;
		}

		netPlayService = new NetPlayService(syncClientHandler);
		netPlayService.tcpConnect(addr, port);
		Toast.makeText(this, R.string.netplay_client_started, Toast.LENGTH_LONG).show();
	}

	private void onDisconnect() {
		if (netPlayService != null) {
			netPlayService.disconnect();
			netPlayService = null;
		}
	}

	private String getEmulatorEngine(SharedPreferences prefs) {
		return prefs.getString("emulatorEngine", "nes");
	}

	private void setAutoSyncClientInterval(SharedPreferences prefs) {
		autoSyncClientInterval = Integer.parseInt(prefs.getString("autoSyncClientInterval", "10"));
	}

	private void sendSyncClientMessage() {
		if (netPlayService != null && !netPlayService.isServer()) {
			netPlayService.sendSyncClientMessage();
			syncClientHandler.sendEmptyMessageDelayed(MESSAGE_SYNC_CLIENT,
					autoSyncClientInterval * 1000);
		}
	}

	private final Handler syncClientHandler = new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MESSAGE_SYNC_CLIENT)
				sendSyncClientMessage();
		}
	};

	private void showWaitDialog(String message, DialogInterface.OnClickListener listener) {
		if (waitDialog != null)
			waitDialog.dismiss();

		waitDialog = new NetWaitDialog();
		waitDialog.setMessage(message);
		waitDialog.setOnClickListener(listener);
		waitDialog.show();
	}

	private void dismissWaitDialog() {
		if (waitDialog != null) {
			waitDialog.dismiss();
			waitDialog = null;
		}
	}

	private class NetWaitDialog extends ProgressDialog implements
			DialogInterface.OnCancelListener {

		private OnClickListener onClickListener;

		public NetWaitDialog() {
			super(EmulatorActivity.this);

			setIndeterminate(true);
			setCancelable(true);
			setOnCancelListener(this);
		}

		public void setOnClickListener(OnClickListener l) {
			onClickListener = l;
		}

		@Override
		public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
			if (onClickListener != null &&
					event.getAction() == MotionEvent.ACTION_UP) {
				onClickListener.onClick(this, BUTTON_POSITIVE);
				return true;
			}
			return super.dispatchTouchEvent(event);
		}

		public void onCancel(DialogInterface dialog) {
			waitDialog = null;
			netPlayService.disconnect();
			netPlayService = null;
		}
	}

	/**
	 * Checks whether the parameter is a valid IPv4 address
	 * 
	 * @param input the string to validate
	 * @return true if the string is an IPv4 address, false otherwise
	 */
	private static boolean isIPv4Address(final String input) {
		if (input == null || input.isEmpty()) {
			return false;
		}
		final String[] parts = input.split("\\.");
		if (parts.length != 4) {
			return false;
		}
		for (final String part : parts) {
			try {
				final int value = Integer.parseInt(part);
				if (value < 0 || value > 255) {
					return false;
				}
			} catch (final NumberFormatException ex) {
				return false;
			}
		}
		return true;
	}
}
