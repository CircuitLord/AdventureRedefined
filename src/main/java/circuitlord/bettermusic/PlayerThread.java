package circuitlord.bettermusic;


import javazoom.jl.decoder.JavaLayerException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.InputStream;

public class PlayerThread extends Thread {

	public static final float MIN_GAIN = -50F;
	public static final float MAX_GAIN = 0F;

	public static float[] fadeGains;
	
	static {
		fadeGains = new float[BetterMusic.FADE_DURATION];
		float totaldiff = MIN_GAIN - MAX_GAIN;
		float diff = totaldiff / fadeGains.length;
		for(int i = 0; i < fadeGains.length; i++)
			fadeGains[i] = MAX_GAIN + diff * i;

		// Invert because we have fade ticks counting up now
		//for (int i = fadeGains.length - 1; i >= 0; i--) {
		//	fadeGains[i] = MAX_GAIN + diff * (fadeGains.length - 1 - i);
		//}
	}
	
	public volatile static float gainPercentage = 1.0f;
	public volatile static float realGain = 0;

	public volatile static String currentSong = null;
	public volatile static String currentSongChoices = null;
	
	AdvancedPlayer player;

	private volatile boolean queued = false;

	private volatile boolean kill = false;
	private volatile boolean playing = false;


	boolean notQueuedOrPlaying() {
		return !(queued || isPlaying());
	}

	boolean isPlaying() {
		return playing && !player.getComplete();
	}
	
	public PlayerThread() {
		setDaemon(true);
		setName("BetterMusic Player Thread");
		start();
	}

	@Override
	public void run() {
		try {
			while(!kill) {
				if(queued && currentSong != null) {

					if(player != null)
						resetPlayer();
					InputStream stream = SongLoader.getStream();
					if(stream == null)
						continue;

					player = new AdvancedPlayer(stream);
					queued = false;
				}


				if(player != null && player.getAudioDevice() != null && realGain > MIN_GAIN) {

					processRealGain();
					BetterMusic.LOGGER.info("Playing " + currentSong);
					playing = true;
					player.play();

				}

			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}



	public void resetPlayer() {
		playing = false;
		if(player != null)
			player.close();

		currentSong = null;
		player = null;
	}

	public void play(String song) {
		resetPlayer();

		currentSong = song;
        queued = true;
	}
	
/*	public float getGain() {
		if(player == null)
			return gain;
		
		AudioDevice device = player.getAudioDevice();
		if(device != null && device instanceof JavaSoundAudioDevice)
			return ((JavaSoundAudioDevice) device).getGain();
		return gain;
	}*/
	
/*	public void addGain(float gain) {
		setGain(getGain() + gain);
	}*/
	
	public void setGainPercentage(float newGain) {
		gainPercentage = Math.min(1.0f, Math.max(0.0f, newGain));
	}
	
	public void processRealGain() {

		GameOptions options = MinecraftClient.getInstance().options;

		float minecraftGain = options.getSoundVolume(SoundCategory.MUSIC) * options.getSoundVolume(SoundCategory.MASTER);
		float newRealGain = MIN_GAIN + (MAX_GAIN - MIN_GAIN) * minecraftGain * gainPercentage;
		
		realGain = newRealGain;
		if(player != null) {
			AudioDevice device = player.getAudioDevice();
			if(device != null && device instanceof JavaSoundAudioDevice) {
				try {
					((JavaSoundAudioDevice) device).setGain(newRealGain);
				} catch(IllegalArgumentException e) {
					BetterMusic.LOGGER.error(e.toString());
				}
			}
		}
		
		//if(musicGain == 0)
		//	play(null);
	}
	
/*	public float getRelativeVolume() {
		return getRelativeVolume(getGain());
	}*/
	
/*	public float getRelativeVolume(float gain) {
		float width = MAX_GAIN - MIN_GAIN;
		float rel = Math.abs(gain - MIN_GAIN);
		return rel / Math.abs(width);
	}*/

/*	public int getFramesPlayed() {
		return player == null ? 0 : player.getFrames();
	}*/
	
	public void forceKill() {
		try {
			resetPlayer();
			interrupt();

			finalize();
			kill = true;
		} catch(Throwable e) {
			e.printStackTrace();
		}
	}
}
