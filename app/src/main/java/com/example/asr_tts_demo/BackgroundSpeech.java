package com.example.asr_tts_demo;

import com.sinovoice.sdk.HciSdk;
import com.sinovoice.sdk.android.AudioPlayer;
import com.sinovoice.sdk.android.HciAudioManager;
import com.sinovoice.sdk.android.IAudioPlayerHandler;
import com.sinovoice.sdk.audio.HciAudioMetrics;
import com.sinovoice.sdk.audio.HciAudioSink;
import com.sinovoice.sdk.audio.IAudioCB;
import com.sinovoice.sdk.tts.CloudTtsConfig;
import com.sinovoice.sdk.tts.ISynthHandler;
import com.sinovoice.sdk.tts.SynthConfig;
import com.sinovoice.sdk.tts.SynthStream;
import com.sinovoice.sdk.tts.Warning;
import java.nio.ByteBuffer;

// 持续播放背景语音，以测试回声消除是否有效
final class BackgroundSpeech {
  private final SynthStream stream;
  private final ILogger log;
  private final AudioPlayer player;
  private final SynthConfig config;
  private final HciAudioSink sink;

  BackgroundSpeech(HciSdk sdk, HciAudioManager audioManager, ILogger log) {
    stream = new SynthStream(sdk, new CloudTtsConfig());
    player = new AudioPlayer(audioManager, "pcm_s16le_16k", 1000);
    this.log = log;
    config = new SynthConfig();
    config.setProperty("cn_roumeijuan_common");
    config.setFormat("pcm");
    config.setSampleRate(16000); // 采样率需要与 pcm_s16le_16k 保持一致
    config.setSlice(500);
    sink = player.audioSink();
  }

  void start(final String text) {
    HciAudioMetrics m = sink.defaultMetrics().clone();
    sink.startWrite(m);
    // 启动播放器
    boolean playing = player.start(0, new IAudioPlayerHandler() {
      @Override
      public void onStart(AudioPlayer player) {
        log.printLog("背景音频播放器已启动");
      }

      @Override
      public void onStartFail(AudioPlayer player, String message) {
        log.printLog("背景音频播放器启动失败: " + message);
      }

      @Override
      public void onStop(AudioPlayer player) {
        log.printLog("背景音频播放器已停止");
      }

      @Override
      public void onAudio(AudioPlayer player, ByteBuffer audio, long timestamp) {}

      @Override
      public void onBufferEmpty(AudioPlayer player) {
        log.printLog("背景音频播放器缓冲区空");
      }

      @Override
      public void onError(AudioPlayer player, String message) {
        log.printLog("背景音频播放器发生错误: " + message);
      }

      @Override
      public void onSinkEnded(AudioPlayer player, boolean cancel) {
        log.printLog("背景音频播放器音频槽结束写入: cancel = " + cancel);
      }
    });
    if (!playing) {
      sink.endWrite(true);
      return;
    }
    // 合成语音
    stream.start(text, config, new ISynthHandler() {
      @Override
      public void onStart(SynthStream s, int code, Warning[] warnings) {
        if (code != 0) {
          log.printLog("背景语音合成启动失败: code = " + code);
        } else {
          // log.printLog("背景语音合成启动成功");
        }
      }

      @Override
      public void onError(SynthStream s, int code) {
        log.printLog("背景语音合成发生错误: code = " + code);
      }

      @Override
      public void onAudio(SynthStream s, ByteBuffer audio, final Runnable free) {
        // 将合成的音频写入播放器的 audioSink
        sink.asyncWrite(audio, new IAudioCB() {
          @Override
          public void run(int retval) {
            // log.printLog("背景音频完成写入: " + retval);
            free.run();
          }
        });
      }

      @Override
      public void onEnd(SynthStream s, int reason) {
        // 继续合成
        // log.printLog("背景语音合成启动结束: reason = " + reason);
        stream.start(text, config, this, true);
      }
    }, true);
  }
}
