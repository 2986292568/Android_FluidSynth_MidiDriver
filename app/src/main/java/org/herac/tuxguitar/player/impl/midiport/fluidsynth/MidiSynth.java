package org.herac.tuxguitar.player.impl.midiport.fluidsynth;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.SparseArray;

/**
 * 使用sf2音色库播放midi
 * @author Cai
 */
public class MidiSynth {

	private static final String JNI_LIBRARY_NAME = new String("fluidsynth-android");

	static{
		System.loadLibrary(JNI_LIBRARY_NAME);
	}

	private Thread m_streamThread[];
	private final int THREAD_MAX = 5;

	private int index;
	private long[] synthNums;
	public boolean m_bIsStreaming = true;
	private SparseArray<Integer> noteManager;
	private String filePath;

	public MidiSynth(String filePath){
		this.filePath = filePath;
	}

	public void startSynth() {
		index = 0;
		synthNums = new long[THREAD_MAX];
		noteManager = new SparseArray<Integer>();

		m_streamThread = new Thread[THREAD_MAX];

		// not running yet
		if (m_bIsStreaming)
		{
			m_bIsStreaming = false;

			for(int i = 0; i < THREAD_MAX; i++){
				m_streamThread[i] = new MidiSynthThread(i, this);
				m_streamThread[i].start();
			}

			// need a small timeout to enable synth to start
			try {
				Thread.sleep(1000);

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void destroy()
	{
		if (!m_bIsStreaming){
			m_bIsStreaming = true;

			try
			{
				for(int i = 0; i < THREAD_MAX; i++){
					m_streamThread[i].join();
				}
			}
			catch(final Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}

	public boolean isInit(){
		for (int i=0;i<synthNums.length;i++){
			if (synthNums[i]!=0){
				return true;
			}
		}
		return false;
	}

	public class MidiSynthThread extends Thread {

		private int synthNum;
		private MidiSynth midiSynth;
		private AudioTrack m_Track;

		public MidiSynthThread(int synthNum,MidiSynth midiSynth){
			this.synthNum = synthNum;
			this.midiSynth = midiSynth;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			super.run();
			int minBufferSize = AudioTrack.getMinBufferSize(44100/2, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
			m_Track = new AudioTrack(AudioManager.STREAM_MUSIC, 44100/2, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);
			m_Track.play();

			int bufferSize = 2 ;;
			short[] streamBuffer = new short[bufferSize];
			synthNums[synthNum] = midiSynth.malloc();

//			String filePath = "/storage/sdcard0" + "/FluidR3_GM.sf2";
//			String filePath = "/storage/sdcard0" + "/GeneralUser_GS.sf2";
//			String filePath = "/storage/sdcard0" + "/Florestan_Basic_GM_GS.sf2";

			midiSynth.open(synthNums[synthNum]);
			midiSynth.loadFont(synthNums[synthNum], filePath);

			while (!m_bIsStreaming)
			{

				// Have fluidSynth fill our buffer...
				midiSynth.FillBuffer(synthNums[synthNum], streamBuffer, bufferSize);

				// ... then feed that data to the AudioTrack
				m_Track.write(streamBuffer, 0, bufferSize);

			}
			m_Track.flush();
			m_Track.stop();
			m_Track.release();

			midiSynth.unloadFont(synthNums[synthNum]);
			midiSynth.close(synthNums[synthNum]);
		}

	}

	private int getSynthNumNoteOn(){
		int synthNum = 0;
		index++;
		if(index >= THREAD_MAX){
			index = 0;
		}
		synthNum = index;
		return synthNum;
	}

	public void sendNoteOn(int channel, int key, int velocity)  {

		if (isInit()){
			int synthNum = getSynthNumNoteOn();
			noteManager.append(key, synthNum);
			this.noteOn(synthNums[synthNum],channel, key, velocity);

		}

	}

	public void sendNoteOff(int channel, int key, int velocity) {
		if (isInit()){
			if (noteManager.get(key) != null){
				int synthNum = (Integer) noteManager.get(key);
				this.noteOff(synthNums[synthNum],channel, key, velocity);
			}
		}

	}

	public void sendDrumOn(int channel, int key, int velocity){
		if (isInit()){
			int synthNum = getSynthNumNoteOn();
			try {
				this.noteOn(synthNums[synthNum],channel, key, velocity);
				Thread.sleep(50);
				this.noteOff(synthNums[synthNum],channel, key, velocity);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	public void sendControlChange(int channel, int controller, int value) {
		if (isInit()){
			for(int i = 0; i < THREAD_MAX; i++){
				controlChange(synthNums[i], channel, controller,value);
			}
		}
	}

	public void sendProgramChange(int channel, int value) {
		if (isInit()){
			for(int i = 0; i < THREAD_MAX; i++){
				programChange(synthNums[i], channel,value);
			}
		}

	}

	public void sendPitchBend(int channel, int value) {
		if (isInit()){
			for(int i = 0; i < THREAD_MAX; i++){
				pitchBend(synthNums[i], channel,value);
			}
		}
	}

	private native long malloc();

	private native void free(long instance);

	private native void open(long instance);

	private native void close(long instance);

	private native void loadFont(long instance, String path);

	private native void unloadFont(long instance);

	private native void systemReset(long instance);

	private synchronized native void noteOn(long instance,int channel,int note,int velocity);

	private synchronized native void noteOff(long instance,int channel,int note,int velocity);

	private native void controlChange(long instance,int channel,int control,int value);

	private native void programChange(long instance,int channel,int program);

	private native void pitchBend(long instance,int channel,int value);

	public native void FillBuffer(long instance, short[] buffer, int buffLen);

}
