package com.example.asr_tts_demo;

import android.app.Activity;
import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.sinovoice.sdk.HciSdk;
import com.sinovoice.sdk.HciSdkConfig;
import com.sinovoice.sdk.LogLevel;
import com.sinovoice.sdk.SessionState;
import com.sinovoice.sdk.android.AudioPlayer;
import com.sinovoice.sdk.android.AudioRecorder;
import com.sinovoice.sdk.android.HciAudioManager;
import com.sinovoice.sdk.android.IAudioPlayerHandler;
import com.sinovoice.sdk.android.IAudioRecorderHandler;
import com.sinovoice.sdk.asr.CloudAsrConfig;
import com.sinovoice.sdk.asr.FreetalkConfig;
import com.sinovoice.sdk.asr.FreetalkEvent;
import com.sinovoice.sdk.asr.FreetalkResult;
import com.sinovoice.sdk.asr.FreetalkStream;
import com.sinovoice.sdk.asr.IFreetalkHandler;
import com.sinovoice.sdk.asr.Warning;
import com.sinovoice.sdk.audio.HciAudioMetrics;
import com.sinovoice.sdk.audio.HciAudioSink;
import com.sinovoice.sdk.audio.IAudioCB;
import com.sinovoice.sdk.tts.CloudTtsConfig;
import com.sinovoice.sdk.tts.ISynthHandler;
import com.sinovoice.sdk.tts.SynthConfig;
import com.sinovoice.sdk.tts.SynthStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;

public class MainActivity extends Activity implements IAudioRecorderHandler, ILogger,
                                                      IAudioPlayerHandler, IFreetalkHandler,
                                                      ISynthHandler {
  // 日志窗体最大记录的行数，避免溢出问题
  private static final int MAX_LOG_LINES = 5 * 1024;
  private Thread mUiThread;

  static private HciSdk createSdk(Context context) {
    String path = Environment.getExternalStorageDirectory().getAbsolutePath();
    path = path + File.separator + context.getPackageName();

    File file = new File(path);
    if (!file.exists()) {
      file.mkdirs();
    }

    HciSdkConfig cfg = new HciSdkConfig();
    HciSdk sdk = new HciSdk();
    // 平台为应用分配的 appkey
    cfg.setAppkey("aicp_app");
    // 应用对象的密钥 (敏感信息，请勿公开)
    cfg.setSecret("QWxhZGRpbjpvcGVuIHNlc2FtZQ");
    cfg.setSysUrl("https://10.1.18.103:22801/");
    cfg.setCapUrl("http://10.1.18.103:22800/");
    cfg.setDataPath(path);
    cfg.setVerifySSL(false);

    sdk.setLogLevel(LogLevel.D); // 日志级别
    Log.w("sdk-config", cfg.toString());

    sdk.init(cfg, context);
    return sdk;
  }

  static private FreetalkConfig freetalkConfig() {
    FreetalkConfig config = new FreetalkConfig();
    config.setProperty("cn_16k_common");
    config.setAudioFormat("pcm_s16le_16k");
    config.setMode(FreetalkConfig.CONTINUE_STREAM_MODE);
    config.setAddPunc(true); // 是否打标点
    config.setSlice(200);
    config.setDps(1200);
    config.setTppContextRange(0);
    config.setTimeout(10000);
    Log.w("config", config.toString());
    return config;
  }

  static private SynthConfig synthConfig(int sample_rate) {
    SynthConfig config = new SynthConfig();
    config.setProperty("cn_roumeijuan_common");
    config.setFormat("pcm");
    config.setSampleRate(sample_rate);
    Log.w("config", config.toString());
    return config;
  }

  private HciSdk sdk;
  private FreetalkStream ft_stream;
  private SynthStream synth_stream;
  private AudioRecorder recorder;
  private AudioPlayer player;
  private TextView tv_logview;
  private HciAudioManager am;
  private boolean recording, playing;
  private final Queue<String> texts = new ArrayDeque<String>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    mUiThread = Thread.currentThread();
    sdk = createSdk(this);
    ft_stream = new FreetalkStream(sdk, new CloudAsrConfig());
    synth_stream = new SynthStream(sdk, new CloudTtsConfig());
    am = HciAudioManager
             .builder(this)
             // 这里应设置录音机和播放器采样率中最大值
             .setSampleRate(16000)
             // 不使用硬件 AEC，某些手机硬件 AEC 效果很差
             .setUseHardwareAcousticEchoCanceler(false)
             // 不使用硬件 NS，某些手机硬件 AEC 效果较差
             .setUseHardwareNoiseSuppressor(false)
             .create();
    recorder = new AudioRecorder(am, "pcm_s16le_16k", 1000);
    player = new AudioPlayer(am, "pcm_s16le_16k", 1000);
    tv_logview = (TextView) findViewById(R.id.tv_logview);
    initEvents();
    if (true) {
      // 持续播放静音
      new BackgroundSilence(am, this).start();
    } else {
      // 持续播放合成音频
      new BackgroundSpeech(sdk, am, this)
          .start("盼望着，盼望着，东风来了，春天的脚步近了。" //
              + "一切都像刚睡醒的样子，欣欣然张开了眼。" //
              + "山朗润起来了，水长起来了，太阳的脸红起来了。");
    }
  }

  private void initEvents() {
    final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    // 切换扬声器
    ((ToggleButton) findViewById(R.id.toggle_speaker))
        .setOnCheckedChangeListener(new OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
            audioManager.setSpeakerphoneOn(isChecked);
          }
        });
    // 静音按钮
    ((ToggleButton) findViewById(R.id.toggle_mute))
        .setOnCheckedChangeListener(new OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
            am.setSpeakerMute(isChecked);
          }
        });
    // 开始按钮
    findViewById(R.id.btn_rec).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (ft_stream.state() == SessionState.IDLE && !recording) {
          printLog("FreetalkStream 启动中...");
          ft_stream.start(freetalkConfig(), recorder.audioSource(), MainActivity.this, true);
        } else if (ft_stream.state() != SessionState.IDLE) {
          if (recording) {
            recorder.stop(false); // 会传递至 ft_stream
          } else {
            ft_stream.stop(false);
          }
        }
      }
    });
    // 清屏按钮
    findViewById(R.id.btn_clear).setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(final View v) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            tv_logview.setText("");
          }
        });
      }
    });
  }

  private final Runnable scrollLog = new Runnable() {
    @Override
    public void run() {
      ((ScrollView) tv_logview.getParent()).fullScroll(ScrollView.FOCUS_DOWN);
    }
  };

  private void _printLog(String detail) {
    // 日志输出同时记录到日志文件中
    if (tv_logview == null) {
      return;
    }

    // 如日志行数大于上限，则清空日志内容
    if (tv_logview.getLineCount() > MAX_LOG_LINES) {
      tv_logview.setText("");
    }

    // 在当前基础上追加日志
    tv_logview.append(detail + "\n");

    // 二次刷新确保父控件向下滚动能到达底部,解决一次出现多行日志时滚动不到底部的问题
    tv_logview.post(scrollLog);
  }

  private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS");

  @Override
  public void printLog(String detail) {
    final String message = fmt.format(new Date()) + " " + detail;
    if (mUiThread == Thread.currentThread()) {
      _printLog(message);
      return;
    }
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        _printLog(message);
      }
    });
  }

  private void addSynthText(String text) {
    if (!playing) {
      HciAudioSink sink = player.audioSink();
      HciAudioMetrics m = sink.defaultMetrics().clone();
      int ret = sink.startWrite(m);
      if (ret != 0) {
        printLog("HciAudioSink.startWrite failed: " + ret);
        recorder.stop(true);
        return;
      }
      playing = player.start(0, this);
      if (!playing) {
        sink.endWrite(true);
        recorder.stop(true);
        return;
      }
    }
    texts.add(text);
    nextSynth();
  }

  private void nextSynth() {
    if (texts.isEmpty() && ft_stream.state() == SessionState.IDLE) {
      ((Button) findViewById(R.id.btn_rec)).setText(R.string.start);
      return;
    }
    if (texts.isEmpty() || synth_stream.state() != SessionState.IDLE || !playing) {
      return;
    }
    synth_stream.start(texts.poll(), synthConfig(16000), this, true);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_settings) {
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onStart(AudioRecorder recorder) {
    printLog("录音机已启动");
  }

  @Override
  public void onStartFail(AudioRecorder recorder, String message) {
    printLog("录音机启动失败: " + message);
  }

  @Override
  public void onStop(AudioRecorder recorder) {
    printLog("录音机已停止");
    recording = false;
  }

  @Override
  public void onAudio(AudioRecorder recorder, ByteBuffer audio) {}

  @Override
  public void onBufferFull(AudioRecorder recorder) {
    printLog("录音机缓冲区满");
    recorder.stop(false);
  }

  @Override
  public void onError(AudioRecorder recorder, String message) {
    printLog("录音机发生错误: " + message);
  }

  @Override
  public void onSourceEnded(AudioRecorder recorder) {
    printLog("录音机音频源已结束读取");
    recorder.stop(false);
  }

  @Override
  public void onAudio(AudioPlayer player, ByteBuffer audio, long timestamp) {}

  @Override
  public void onBufferEmpty(AudioPlayer player) {
    printLog("播放缓冲区空");
  }

  @Override
  public void onError(AudioPlayer player, String message) {
    printLog("播放器发生错误: " + message);
  }

  @Override
  public void onSinkEnded(AudioPlayer player, boolean cancel) {
    printLog("播放器音频槽结束写入: cancel = " + cancel);
  }

  @Override
  public void onStart(AudioPlayer player) {
    printLog("播放器已启动");
  }

  @Override
  public void onStartFail(AudioPlayer player, String message) {
    printLog("播放器启动失败: " + message);
  }

  @Override
  public void onStop(AudioPlayer player) {
    printLog("播放器已停止");
    playing = false;
    nextSynth();
  }

  @Override
  public void onStart(FreetalkStream s, final int code, Warning[] warnings) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (code == 0) {
          printLog("语音识别已启动");
          recording =
              recorder.start(AudioRecorder.ENABLE_AEC | AudioRecorder.ENABLE_NS, MainActivity.this);
          if (recording) {
            ((Button) findViewById(R.id.btn_rec)).setText(R.string.stop);
          } else {
            ft_stream.stop(true);
          }
        } else {
          printLog("语音识别启动失败: code = " + code);
        }
      }
    });
  }

  @Override
  public void onEnd(FreetalkStream s, int reason) {
    printLog("语音识别已结束: reason = " + reason);
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (playing) {
          nextSynth();
        }
      }
    });
  }

  @Override
  public void onError(FreetalkStream s, int code) {
    printLog("语音识别发生错误: code = " + code);
  }

  static String eventTypeString(int type) {
    switch (type) {
      case FreetalkEvent.VOICE_START:
        return "VOICE_START";
      case FreetalkEvent.VOICE_END:
        return "VOICE_END";
      case FreetalkEvent.NOISE:
        return "NOISE";
      case FreetalkEvent.EXCEEDED_SILENCE:
        return "EXCEEDED_SILENCE";
      case FreetalkEvent.EXCEEDED_END_SILENCE:
        return "EXCEEDED_END_SILENCE";
      case FreetalkEvent.EXCEEDED_AUDIO:
        return "EXCEEDED_AUDIO";
      default:
        return "UNKNOWN";
    }
  }

  @Override
  public void onEvent(FreetalkStream s, FreetalkEvent event) {
    printLog("语音事件: " + eventTypeString(event.type()));
  }

  @Override
  public void onResult(FreetalkStream s, FreetalkResult sentence) {
    // sentence 仅可在本回调内使用，如果需要缓存 sentence，请调用 sentence.clone()
    final String text = sentence.textResult().text();
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        printLog("识别结果: " + text);
        addSynthText(text);
      }
    });
  }

  @Override
  public void onStart(SynthStream s, final int code, com.sinovoice.sdk.tts.Warning[] warnings) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (code == 0) {
          printLog("语音合成已启动");
        } else {
          printLog("语音合成启动失败: code = " + code);
          recorder.stop(true);
        }
      }
    });
  }

  @Override
  public void onEnd(SynthStream s, int reason) {
    printLog("语音合成已结束");
  }

  @Override
  public void onAudio(SynthStream s, ByteBuffer audio, final Runnable free) {
    player.audioSink().asyncWrite(audio, new IAudioCB() {
      @Override
      public void run(int retval) {
        printLog("播放音频完成写入: " + retval);
        free.run();
      }
    });
  }

  @Override
  public void onError(SynthStream s, int code) {
    printLog("语音合成发生错误: code = " + code);
  }
}
