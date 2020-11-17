package com.example.asr_tts_demo;

import com.sinovoice.sdk.android.AudioPlayer;
import com.sinovoice.sdk.android.HciAudioManager;
import com.sinovoice.sdk.android.IAudioPlayerHandler;
import com.sinovoice.sdk.audio.HciAudioMetrics;
import com.sinovoice.sdk.audio.HciAudioSink;
import com.sinovoice.sdk.audio.IAudioCB;
import java.nio.ByteBuffer;

final class BackgroundSilence {
  private final AudioPlayer player;
  private final HciAudioSink sink;
  private final ILogger log;

  BackgroundSilence(HciAudioManager audioManager, ILogger log) {
    player = new AudioPlayer(audioManager, "pcm_s16le_16k", 1000);
    sink = player.audioSink();
    this.log = log;
  }

  void start() {
    final ByteBuffer silence = ByteBuffer.allocateDirect(6400);
    HciAudioMetrics m = sink.defaultMetrics().clone();
    sink.startWrite(m);
    sink.asyncWrite(silence, new IAudioCB() {
      @Override
      public void run(int retval) {
        silence.position(0);
        sink.asyncWrite(silence, this);
      }
    });

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
    }
  }
}
