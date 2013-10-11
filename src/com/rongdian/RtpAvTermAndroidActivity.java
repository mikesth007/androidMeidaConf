package com.rongdian;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;

import com.jeremyfeinstein.slidingmenu.lib.app.SlidingActivity;

public abstract class RtpAvTermAndroidActivity extends SlidingActivity
		implements OnTouchListener, Camera.PreviewCallback, Callback,
		RtpAvTermListener {
	private static final int frequency = 8000;
	private static final int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	private static final int audioRecordMinBufSize = AudioRecord
			.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
	private static final int audioTraceMinBufSize = AudioRecord
			.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
	private static final int playBufSize = audioTraceMinBufSize / 4;
	// 音频的采集和播放
	private boolean isRecording = false;
	private AudioRecord audioRecord = null;
	private AudioTrack audioTrack = null;

	// 视频的采集和播放
	private Camera mCamera = null;
	// 预览状态
	private boolean mPreviewRunning = false;
	SurfaceView mSurfaceView = null;
	private int mMakeRotationClockwise = 0;
	// 视频参数
	private long mVideoWidth = 0;
	private long mVideoHeight = 0;
	private boolean isVideoOutput = false;

	private long term = 0;

	private boolean surfaceCreated;
	private boolean surfaceChanged;

	private SurfaceHolder surfaceHolder;
	private Rect rect;// surfaceView四周的rect
	private int actWidth,actHeight;

	//
	// //-------------------------------------------------------------------------
	//
	protected abstract int getLayoutId();

	protected abstract SurfaceView getLocalPreview();

	protected abstract GL2JNIView getRomotePreview();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(getLayoutId());

		surfaceCreated = false;
		surfaceChanged = false;
		mSurfaceView = getLocalPreview();
		//mSurfaceView.setOnTouchListener(this);
		surfaceHolder = mSurfaceView.getHolder();
		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		Display display = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		actWidth = size.x;
		actHeight = size.y;
	}

	@Override
	protected void onPause() {
		super.onPause();
		// getRomotePreview().onPause();
		Log.i("RtpAvTermActivity", "onPause");
	}

	@Override
	protected void onResume() {
		super.onResume();
		// getRomotePreview().onResume();
		Log.i("RtpAvTermActivity", "onResume");
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		Log.i("RtpAvTermActivity", "surfaceChanged");
		surfaceChanged = true;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i("RtpAvTermActivity", "surfaceCreated");
		surfaceCreated = true;
		rect = new Rect(mSurfaceView.getLeft(), mSurfaceView.getRight(),
				mSurfaceView.getWidth(), mSurfaceView.getHeight());// 图片的rect
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i("RtpAvTermActivity", "surfaceDestoryed");
		surfaceCreated = false;
		surfaceChanged = false;
		if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	//
	// //-----对外公开API--------------------------------------------------------------------------------
	//

	public synchronized boolean ravtCreateTerm() {
		if (this.term != 0) {
			return (false);
		}

		this.term = RtpAvTerm.ravtCreateTerm(this);

		return (this.term != 0);
	}

	public void ravtDeleteTerm() {
		long term = 0;

		synchronized (this) {
			getRomotePreview().renderer.setOpenGLESDisplay(0);
			term = this.term;
			this.term = 0;
		}

		if (term != 0) {
			RtpAvTerm.ravtDeleteTerm(term);
		}
	}

	public synchronized boolean ravtOpenVideoSession(byte[] localIp,
			int localPort) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtOpenVideoSession(this.term, localIp, localPort);
	}

	public synchronized void ravtCloseVideoSession() {
		if (this.term == 0) {
			return;
		}

		RtpAvTerm.ravtCloseVideoSession(this.term);
	}

	public synchronized boolean ravtGetVideoSessionLocalAddr(byte[] localIp_64,
			int[] localPort_1) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtGetVideoSessionLocalAddr(this.term, localIp_64,
				localPort_1);
	}

	public synchronized boolean ravtSetVideoSessionRemoteAddr(byte[] remoteIp,
			int remotePort) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtSetVideoSessionRemoteAddr(this.term, remoteIp,
				remotePort);
	}

	public synchronized boolean ravtSetVideoSessionOutputPayloadType(
			byte payloadType) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtSetVideoSessionOutputPayloadType(this.term,
				payloadType);
	}

	public synchronized boolean ravtOpenLocalVideoPreview(long videoWidth,
			long videoHeight) {

		if (videoWidth < videoHeight) {
			videoWidth ^= videoHeight;
			videoHeight ^= videoWidth;
			videoWidth ^= videoHeight;
		}

		if (!RtpAvTerm.ravtOpenLocalVideoPreview(this.term, videoWidth,
				videoHeight)) {
			return (false);
		}

		mSurfaceView = getLocalPreview();
		surfaceHolder = mSurfaceView.getHolder();
		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		mVideoWidth = videoWidth;
		mVideoHeight = videoHeight;

		// mCamera = Camera.open(getFrontCameraId());
		if (mCamera == null) {
			if (android.os.Build.VERSION.SDK_INT >= 9) {
				mCamera = Camera.open(getFrontCameraId()); // leixiaojiang
			} else {
				mCamera = Camera.open();
			}
		}

		if (mPreviewRunning) {
			mCamera.stopPreview();
		}

		Camera.Parameters p = mCamera.getParameters();
		// 设置预览大小
		Size psize = matchCameraSuportSize(videoWidth, videoHeight, p);
		p.setPreviewSize(psize.width, psize.height);
		Log.i("wzl" + "initCamera", "previewSize,width: " + psize.width
				+ " height" + psize.height);

		// 设置采集帧率
		p.setPreviewFrameRate(findCameraSuportMaxFrameRate(p));
		Log.i("wzl" + "initCamera",
				"previewFrameRates: "
						+ String.valueOf(findCameraSuportMaxFrameRate(p)));

		// 调整预览的方向正常
		if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
			p.set("orientation", "portrait");
			mCamera.setDisplayOrientation(90);// 针对android2.2和之上的版本
			p.setRotation(90);// 去掉android2.0和之前的版本
			mMakeRotationClockwise = 90;
		} else {
			p.set("orientation", "landscape");
			mCamera.setDisplayOrientation(0);
			p.setRotation(0);
			mMakeRotationClockwise = 0;
		}

		mCamera.setParameters(p);
		mCamera.setPreviewCallback(this);

		// try {
		// // 设置预览的控件，图像会绘画在这个控件上。不需要人工去画
		// mCamera.setPreviewDisplay(surfaceHolder);
		// } catch (Exception ex) {
		// RtpAvTerm.ravtCloseLocalVideoPreview(this.term);
		// return (false);
		// }
		// // 开始预览
		// mCamera.startPreview();
		// mPreviewRunning = true;

		Timer timer = new Timer();
		TimerTask setPreviewTask = new TimerTask() {

			@Override
			public void run() {
				if (surfaceCreated) { // 只有surface准备好了才开始预览
					try {
						// 设置预览的控件，图像会绘画在这个控件上。不需要人工去画
						mCamera.setPreviewDisplay(surfaceHolder);
					} catch (Exception ex) {
						RtpAvTerm.ravtCloseLocalVideoPreview(term);
					}
					this.cancel();
				}

			}
		};
		TimerTask startPreviewTask = new TimerTask() {

			@Override
			public void run() {
				if (surfaceChanged) {
					mCamera.startPreview(); // 只有surface准备好了才开始预览
					mPreviewRunning = true;
					this.cancel();
				}

			}
		};

		timer.schedule(setPreviewTask, 0, 1000);
		timer.schedule(startPreviewTask, 0, 1000);

		return (true);
	}

	// 返回前置摄像头id如果没有返回后置摄像头
	private int getFrontCameraId() {
		int cameraId = 0;
		int numberOfCameras = Camera.getNumberOfCameras();
		CameraInfo cameraInfo = new CameraInfo();
		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
				cameraId = i;
				break;
			} else if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
				cameraId = i;
			}
		}
		return (cameraId);
	}

	private Size matchCameraSuportSize(long videoWidth, long videoHeight,
			Camera.Parameters p) {
		List<Size> previewSizes = p.getSupportedPreviewSizes();
		Size psize;
		int k = 0;
		for (int i = 0; i < previewSizes.size(); i++) {
			psize = previewSizes.get(i);
			Log.i("wzl" + "initCamera", "previewSize,width: " + psize.width
					+ " height" + psize.height);
			if ((psize.width == videoWidth) && (psize.height == videoHeight)) {
				psize.width = (int) videoWidth;
				psize.height = (int) videoHeight;
				return (psize);
			}
		}
		for (int i = 0; i < previewSizes.size(); i++) {
			psize = previewSizes.get(i);
			if (Math.pow((previewSizes.get(k).width
					* previewSizes.get(k).height - videoWidth * videoHeight), 2) > Math
					.pow((psize.width * psize.height - videoWidth * videoHeight),
							2)) {
				k = i;
			}
		}
		psize = previewSizes.get(k);
		return (psize);
	}

	private int findCameraSuportMaxFrameRate(Camera.Parameters p) {
		int k = 0;
		List<Integer> previewFrameRates = p.getSupportedPreviewFrameRates();
		for (int i = 0; i < previewFrameRates.size(); i++) {
			previewFrameRates.get(i);
			if (previewFrameRates.get(k) < previewFrameRates.get(i)) {
				k = i;
			}
		}
		return previewFrameRates.get(k).intValue();
	}

	public synchronized void ravtCloseLocalVideoPreview() {
		if (this.term == 0) {
			return;
		}

		if (mPreviewRunning && mCamera != null) {
			System.out.println("Release Preview now");
			mPreviewRunning = false;
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}

		RtpAvTerm.ravtCloseLocalVideoPreview(this.term);
	}

	public synchronized boolean ravtOpenLocalVideoOutput(short videoType,
			long videoWidth, long videoHeight, long videoBitRate,
			long videoFrameRate, long videoKeyFrameInterval,
			long videoPacketSize) {
		if (!RtpAvTerm.ravtOpenLocalVideoOutput(this.term, videoType,
				videoWidth, videoHeight, videoBitRate, videoFrameRate,
				videoKeyFrameInterval, videoPacketSize)) {
			return (false);
		}

		this.isVideoOutput = true;

		return (true);
	}

	public synchronized void ravtCloseLocalVideoOutput() {
		if (this.term == 0) {
			return;
		}

		this.isVideoOutput = false;
		RtpAvTerm.ravtCloseLocalVideoOutput(this.term);
	}

	public synchronized boolean ravtOpenRemoteVideoPreview(short videoType) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtOpenRemoteVideoPreview(this.term, videoType);
	}

	public synchronized void ravtCloseRemoteVideoPreview() {
		if (this.term == 0) {
			return;
		}

		RtpAvTerm.ravtCloseRemoteVideoPreview(this.term);
	}

	public synchronized boolean ravtOpenAudioSession(byte[] localIp,
			int localPort) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtOpenAudioSession(this.term, localIp, localPort);
	}

	public synchronized boolean ravtGetAudioSessionLocalAddr(byte[] localIp_64,
			int[] localPort_1) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtGetAudioSessionLocalAddr(this.term, localIp_64,
				localPort_1);
	}

	public synchronized boolean ravtSetAudioSessionRemoteAddr(byte[] remoteIp,
			int remotePort) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtSetAudioSessionRemoteAddr(this.term, remoteIp,
				remotePort);
	}

	public synchronized boolean ravtSetAudioSessionOutputPayloadType(
			byte payloadType) {
		if (this.term == 0) {
			return (false);
		}

		return RtpAvTerm.ravtSetAudioSessionOutputPayloadType(this.term,
				payloadType);
	}

	public synchronized void ravtCloseAudioSession() {
		if (this.term == 0) {
			return;
		}

		RtpAvTerm.ravtCloseAudioSession(this.term);
	}

	public synchronized boolean ravtOpenLocalAudio(short audioType) {
		if (!RtpAvTerm.ravtOpenLocalAudio(this.term, audioType, 8000)) {
			return (false);
		}

		if (!isRecording) {
			isRecording = true;
			new Thread(new Runnable() {
				@Override
				public void run() {
					audioCapture();
				}
			}).start();
		}

		return (true);
	}

	public synchronized void ravtCloseLocalAudio() {
		if (this.term == 0) {
			return;
		}

		isRecording = false;
		RtpAvTerm.ravtCloseLocalAudio(this.term);
	}

	// public Object remoteAudio_lock = new Object();

	public synchronized boolean ravtOpenRemoteAudio(short audioType) {
		if (this.term == 0) {
			return (false);
		}

		// 创建pcm音频播放器并开启
		if (!RtpAvTerm.ravtOpenRemoteAudio(this.term, audioType, 8000)) {
			return (false);
		}

		if (audioTrack == null) {
			audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
					frequency, audioEncoding, audioEncoding,
					audioTraceMinBufSize * 3, AudioTrack.MODE_STREAM);
			audioTrack.play();
		}

		return (true);
	}

	public synchronized void ravtCloseRemoteAudio() {
		if (this.term == 0) {
			return;
		}

		if (audioTrack != null) {
			audioTrack.flush();
			audioTrack.stop();
			audioTrack.release();
			audioTrack = null;
		}

		RtpAvTerm.ravtCloseRemoteAudio(this.term);
	}

	//
	// //-----RtpAvTermListener接口中的方法供底层回调--------------------------------------------------------------------------------
	//

	@Override
	public synchronized void onRecvAudio(long term, byte[] pcmData, int size) {
		if (this.term == 0) {
			return;
		}

		if (audioTrack != null) {
			if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
				audioTrack.write(pcmData, 0, size);
			}
		}
	}

	@Override
	public synchronized void onRecvVideo(long term) {
		if (this.term == 0) {
			return;
		}
		// Log.v("RtpAvTerm","Receive video");
		getRomotePreview().renderer.setOpenGLESDisplay(this.term);
		// 刷新界面
		getRomotePreview().requestRender();
	}

	@Override
	public synchronized void onRequestKeyFrame(long term) {
		
	}

	//
	// //-----视音频数据采集--------------------------------------------------------------------------------
	//

	// camera回调。此会自动采集一帧yuv420sp数据
	public synchronized void onPreviewFrame(byte[] data, Camera camera) {
		if (this.term == 0) {
			return;
		}

		if (isVideoOutput) {
			RtpAvTerm.ravtPutLocalVideoOutput(this.term, data, mVideoWidth,
					mVideoHeight, RtpAvTerm.RAVT_COLOR_YUV420SP_VU,
					mMakeRotationClockwise);
		}
	}

	private void audioCapture() {
		if (audioRecord != null) {
			return;
		}

		audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency,
				channelConfiguration, audioEncoding, audioRecordMinBufSize);
		audioRecord.startRecording();

		byte[] buffer = new byte[playBufSize];

		while (true) {
			synchronized (this) {
				if (!isRecording) {
					break;
				}
			}

			audioRecord.read(buffer, 0, playBufSize);

			synchronized (this) {
				if (this.term != 0) {
					RtpAvTerm.ravtPutLocalAudio(this.term, buffer);
				}
			}
		}

		audioRecord.stop();
		audioRecord.release();
		audioRecord = null;
	}

	//
	// //-----拖动、缩放--------------------------------------------------------------------------------
	//

	// 倍率
	private float rate = 1;
	// 记录上次的位置
	private float oldRate = 1;
	private Point point = new Point();// 点击点
	private boolean canDrag = false;// 判断是否点击在图片上，否则拖动无效
	private boolean doDrag = false;
	private boolean isFirst = false;
	private int offsetX = 0, offsetY = 0;// 点击点离图片左上角的距离
	// 记录第一次触屏时线段的距离
	private float oldLineDistance;

	private void draw() {
		Canvas canvas = surfaceHolder.lockCanvas();// 获取画布
		canvas.drawColor(Color.BLACK);// 清屏
		if (1 <= rate && rate <= 3) {
			canvas.scale(rate, rate, mSurfaceView.getWidth() / 2 + rect.left,
					mSurfaceView.getHeight() / 2 + rect.top);
		} else {
			rate = 1;
		}
		mSurfaceView.postInvalidate();
		if (canvas != null) {
			surfaceHolder.unlockCanvasAndPost(canvas);// 解锁画布，提交画好的图像
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			point.x = (int) event.getX();// 获取点击点坐标
			point.y = (int) event.getY();
			Log.i("point position", point.x + "  " + point.y);
			if (rect.contains(point.x, point.y)) {// 判断点击点是否在图片区域
				canDrag = true;
				doDrag = true;
				isFirst = true;
				offsetX = point.x - rect.left;
				offsetY = point.y - rect.top;
			}
			break;

		case MotionEvent.ACTION_MOVE:
			// 拖动算法
			if (canDrag) {
				rect.left = (int) event.getX() - offsetX;
				rect.top = (int) event.getY() - offsetY;
				rect.right = rect.left + mSurfaceView.getWidth();
				rect.bottom = rect.top + mSurfaceView.getHeight();
				if (rect.left < 0) {
					rect.left = 0;
					rect.right = rect.left + mSurfaceView.getWidth();
				}
				if (rect.right > actWidth) {
					rect.right = actWidth;
					rect.left = rect.right - mSurfaceView.getWidth();
				}
				if (rect.top < 0) {
					rect.top = 0;
					rect.bottom = rect.top + mSurfaceView.getHeight();
				}
				if (rect.bottom > actHeight) {
					rect.bottom = actHeight;
					rect.top = rect.bottom - mSurfaceView.getHeight();
				}
				System.out.println("--------------拖动操作执行--------------");
			}
			// 计算缩放比例
			if (doDrag) {
				if (event.getPointerCount() == 2) {
					if (isFirst) {
						// 得到第一次触屏时线段的长度
						oldLineDistance = (float) Math.sqrt(Math.pow(
								event.getX(1) - event.getX(0), 2)
								+ Math.pow(event.getY(1) - event.getY(0), 2));
						isFirst = false;
					} else {
						// 得到触屏时线段的长度
						float newLineDistance = (float) Math.sqrt(Math.pow(
								event.getX(1) - event.getX(0), 2)
								+ Math.pow(event.getY(1) - event.getY(0), 2));
						// 获取本次的缩放比例
						rate = oldRate * newLineDistance / oldLineDistance;
						System.out.println("--------------缩放操作执行--------------");
					}
				}
			}
			draw();
			break;
		case MotionEvent.ACTION_UP:
			canDrag = false;
			doDrag = false;
			break;

		default:
			break;
		}
		return true;
	}
}