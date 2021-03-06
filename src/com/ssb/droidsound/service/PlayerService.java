package com.ssb.droidsound.service;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
//import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.ssb.droidsound.PlayerActivity;
import com.ssb.droidsound.Playlist;
import com.ssb.droidsound.R;
import com.ssb.droidsound.SongFile;
import com.ssb.droidsound.plugins.DroidSoundPlugin;
import com.ssb.droidsound.service.Player.SongInfo;
import com.ssb.droidsound.utils.Log;

public class PlayerService extends Service implements PlayerInterface {
	private static final String TAG = PlayerService.class.getSimpleName();
	
	// Information
	
	public static final int SONG_TITLE = 1;
	public static final int SONG_AUTHOR = 2;
	public static final int SONG_COPYRIGHT = 3;
	public static final int SONG_CPULOAD = 4;	
	public static final int SONG_FORMAT = 5;
	
	public static final int SONG_POS = 6;
	public static final int SONG_FILENAME = 7;
	public static final int SONG_LENGTH = 8;
	public static final int SONG_SUBSONG = 9;
	public static final int SONG_TOTALSONGS = 10;
	
	public static final int SONG_STATE = 11;
	
	public static final int SONG_DETAILS = 12;
	public static final int SONG_REPEAT = 13;
	
	public static final int SONG_SUBTUNE_TITLE = 14;
	public static final int SONG_SUBTUNE_AUTHOR = 15;
	public static final int SONG_PLAYLIST = 16;
	public static final int SONG_FLAGS = 17;
	public static final int SONG_SOURCE = 18;
	public static final int SONG_BUFFERING = 19;
	
	public static final int SONG_ERROR = 20;
	
	public static final int SONG_SIZEOF = 21;
	
	public static final int ERR_SONG_COULD_NOT_PLAY = 1;

	public static final int OPTION_SPEECH = 0;
	public static final int OPTION_SILENCE_DETECT = 1;
	//public static final int OPTION_RESPECT_LENGTH = 2;
	public static final int OPTION_PLAYBACK_ORDER = 3;
	public static final int OPTION_REPEATMODE = 4;
	public static final int OPTION_BUFFERSIZE = 5;
	public static final int OPTION_DEFAULT_LENGTH = 6;
	public static final int OPTION_CYCLE_SUBTUNES = 7;



	public static final int RM_CONTINUE = 0;
	public static final int RM_KEEP_PLAYING = 1;
	public static final int RM_REPEAT = 2;	
	public static final int RM_CONTINUE_SUBSONGS = 3;
	public static final int RM_REPEAT_SUBSONG = 4;
	//private short[] shuffleArray;
	
	private Object info[];
    	
	private static PlayerInterface playerInterface;
	private Player player;
	private Thread playerThread;
	private List<IPlayerServiceCallback> callbacks; 	

	private SongInfo currentSongInfo = new SongInfo();
	
	//private boolean silenceDetect;
	//private boolean respectLength = true;
	private boolean shuffleSongs;
	
	private PhoneStateListener phoneStateListener;
	private BroadcastReceiver mediaReceiver;
	
	private static boolean hasRemoteControl;
	private static boolean hasAudioFocus;
	static {
		try {
			RemoteControlWrapper.checkAvailable();
			hasRemoteControl = true;
		} catch (Throwable t) {
			hasRemoteControl = false;
		}
		try {
			AudioFocusWrapper.checkAvailable();
			hasAudioFocus = true;
		} catch (Throwable t) {
			hasAudioFocus = false;
		}
	}
	
	private RemoteControlWrapper remoteControl;
	private AudioFocusWrapper afWrapper;

	private int defaultRepeatMode = RM_CONTINUE;

	protected String saySomething;

	
	PlayQueue playQueue;
	
	private TextToSpeech textToSpeech;
	private int ttsStatus = -1;
	
	private boolean cycleSubTunes = false;

	
	public static PlayerInterface getPlayerInterface() {
		return playerInterface;
	}
	
	private void performCallback(int...what) {
		Iterator<IPlayerServiceCallback> it = callbacks.iterator();
		while(it.hasNext()) {
			IPlayerServiceCallback cb = it.next();
			for(int i : what) {
				try {
					if(info[i] instanceof String) {
						cb.stringChanged(i, (String)info[i]);
					} else if(info[i] == null) {
						cb.stringChanged(i, null);
					} else {
						cb.intChanged(i, (Integer)info[i]);
					}
				} catch (RemoteException e) {
					Log.d(TAG, "Removing callback because peer is gone");
					it.remove();
				}
			}
		}
	}

	final static String stripChars = "[]!<>?#${}"; 
	final static String blankChars = ".-^,";
	
	final static Map<String, String> composerTranslation = new HashMap<String, String>();

	static {
		
		Map<String, String> ct = composerTranslation;
		
		ct.put("GOTO80", "Go to 80");
		ct.put("RAVEGURU", "Rave guru");
		ct.put("A MAN", "A.Man");
		ct.put("LMAN", "L.Man");
		ct.put("LFT", "LFT");
		ct.put("GREGFEEL", "Greg feel");
		ct.put("CUBEHEAD", "Cube head");
		ct.put("LFT", "LFT");
		ct.put("XINY6581", "Xiny 6 5 8 1");
		ct.put("V0YAGER", "Voyager");
		ct.put("SOUNDEMON", "Sound demon");
		ct.put("SILVERFOX", "Silver fox");
		ct.put("PUTERMAN", "Puter man");
		ct.put("SANDMAN", "Sand man");
		ct.put("LITTLE SID", "Little Sid");
		ct.put("LORD SID", "Lord Sid");
		ct.put("NOISE OF SID", "Noise of Sid");
		ct.put("SMALLTOWN BOY", "Small town boy");
		ct.put("PVCF", "P V C F");
		ct.put("AGEMIXER", "Age mixer");
		ct.put("RADIANTX", "Radiant X");
		ct.put("TWOFLOWER", "Two flower");
		ct.put("NECROPOLO", "Necro Polo");
		ct.put("MINDFLOW", "Mind flow");
		ct.put("MAKTONE", "Mact1");
		ct.put("NE7", "N E 7");
		ct.put("DRAX", "Drax");
		ct.put("CYCLEBURNER", "Cycle burner");
		ct.put("GLENN RUNE GALLEFOSS", "Glen Runay Gallefoss");
	}
	

	private String fixSpeech(String s, boolean composer) {
		
		if(s == null) return s;
		
		StringBuilder sb = new StringBuilder();
		
		if(s.equals("<?>")) {
			return "Unnamed";
		}
		
		for(int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			if(stripChars.indexOf(c) == -1) {
				if(blankChars.indexOf(c) != -1) {
					c = ' ';
				}
				
				switch(c) {
				case '&':
					sb.append(" and ");
					break;
				case '/':
					if(composer) {
						sb.append(" of ");
					} else {
						sb.append(' ');
					}
					break;
				default:
					sb.append(c);
					break;
				}
			}
		}
		
		if(composer) {
			String a = sb.toString().toUpperCase();
			String x = composerTranslation.get(a);
			Log.d(TAG, "Checked %s became %s", a, x == null ? "NULL" : x);
			if(x != null) {
				return x;
			}
		}
		
		return sb.toString();
	}
	
	private void speakTitle() {
		String text = "Unnamed song.";
			
		if(info[SONG_TITLE] == null) return;
		
		String songComposer = (String) info[SONG_AUTHOR];
		
		String s = (String) info[SONG_SUBTUNE_AUTHOR];
		if(s != null && s.length()> 0) {
			songComposer = s;
		}
		
		
				
		
		if(songComposer != null && songComposer.endsWith(")")) {
			int lpara = songComposer.lastIndexOf("(");
			int rpara = songComposer.lastIndexOf(")");
			if(lpara > 0) {
				songComposer = songComposer.substring(lpara+1, rpara);
			}
		}
		
		String songTitle = (String) info[SONG_TITLE];		
		s = (String) info[SONG_SUBTUNE_TITLE];
		if(s != null && s.length()> 0) {
			songTitle = s;
		}
		
		songTitle = fixSpeech(songTitle, false);
		
		
		songComposer = fixSpeech(songComposer, true);
		/*
		String subtuneTitle = (String) info[SONG_SUBTUNE_TITLE];
		
		if(subtuneTitle != null) {
			subtuneTitle = fixSpeech(subtuneTitle, false);
			songTitle += (" " + subtuneTitle);
		} */

		if(songComposer != null & songComposer.length() > 1) {        					        					
			text = songTitle + ". By " + songComposer + ".";        					
		} else {
			text = songTitle + ".";
		}
		Log.d(TAG, "Saying '%s'", text);
		if(ttsStatus >=0 && textToSpeech != null) {
			textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
		} else {
			saySomething = text;
		}	

	}
	
	private static class MyHandler extends Handler {
		
		private WeakReference<PlayerService> psRef;

		public MyHandler(PlayerService ps) {
			psRef = new WeakReference<PlayerService>(ps);
		}
		
		@Override
		public void handleMessage(Message msg) {
			String [] sa;
			PlayerService ps = psRef.get();
            switch (msg.what) {
            	case Player.MSG_WAVDUMPED:
            		ps.info[SONG_FILENAME] = msg.obj;
            		ps.performCallback(SONG_FILENAME);
            		ps.info[SONG_FILENAME] = null;
            		break;
            	case Player.MSG_DETAILS:
            		sa = (String [])msg.obj;
            		ps.info[SONG_DETAILS] = "DETAILS";
            		ps.currentSongInfo.details = sa;
            		ps.performCallback(SONG_DETAILS);
            		Log.d(TAG, "%%%%%%%% Sending %d details", sa.length);
            		break;
            	case Player.MSG_INFO:
        			sa = (String [])msg.obj;
        			ps.info[SONG_TITLE] = sa[0];
        			ps.info[SONG_AUTHOR] = sa[1];
        			
					if(hasRemoteControl)
						ps.remoteControl.setMetaData((String) ps.info[SONG_AUTHOR], (String) ps.info[SONG_TITLE]);
        			
					ps.speakTitle();
					ps.performCallback(SONG_TITLE, SONG_AUTHOR);
        			break;
            	case Player.MSG_SUBTUNE:
            		Log.d(TAG, "SUBTUNE %d, Length %d", msg.arg1, msg.arg2);
            		ps.info[SONG_SUBSONG] = msg.arg1;
            		ps.info[SONG_LENGTH] = msg.arg2;
            		
            		if(msg.arg2 == -1)
            			ps.info[SONG_LENGTH] = ps.defaultLength;
            		
            		ps.info[SONG_STATE] = 1;
            		if(msg.obj != null) {
            			sa = (String [])msg.obj;
            			ps.info[SONG_SUBTUNE_TITLE] = sa[0];
            			ps.info[SONG_SUBTUNE_AUTHOR] = sa[1];
            			

            			
            			if(msg.arg1 < 0) {

        					if(hasRemoteControl)
        						ps.remoteControl.setMetaData((String) ps.info[SONG_SUBTUNE_AUTHOR], (String) ps.info[SONG_SUBTUNE_TITLE]);

        					ps.speakTitle();
            			}
            			
            			ps.performCallback(SONG_SUBSONG, SONG_LENGTH, SONG_SUBTUNE_TITLE, SONG_SUBTUNE_AUTHOR, SONG_STATE);
                		break;                		
            		}
            		ps.performCallback(SONG_SUBSONG, SONG_LENGTH, SONG_STATE);
            		break;
            	case Player.MSG_FAILED:
            		ps.info[SONG_STATE] = 0;
            		ps.info[SONG_ERROR] = ERR_SONG_COULD_NOT_PLAY;
            		ps.performCallback(SONG_FILENAME, SONG_TITLE, SONG_ERROR);
            		ps.info[SONG_ERROR] = 0;
            		break;
                case Player.MSG_NEWSONG:
                	
                	
                	String lastFileName = ps.currentSongInfo.fileName;
                	
                	ps.player.getSongInfo(ps.currentSongInfo);
                	                	
                	ps.info[SONG_TITLE] = ps.currentSongInfo.title;
					ps.info[SONG_AUTHOR] = ps.currentSongInfo.author;
					ps.info[SONG_COPYRIGHT] = ps.currentSongInfo.copyright;
					ps.info[SONG_FORMAT] = ps.currentSongInfo.type;
					ps.info[SONG_LENGTH] = ps.currentSongInfo.length;
					ps.info[SONG_TOTALSONGS] = ps.currentSongInfo.subTunes;
					ps.info[SONG_SUBSONG] = ps.currentSongInfo.startTune;
					ps.info[SONG_REPEAT] = ps.defaultRepeatMode;
					ps.info[SONG_SUBTUNE_TITLE] = ps.currentSongInfo.subtuneTitle;
					ps.info[SONG_SUBTUNE_AUTHOR] = ps.currentSongInfo.subtuneAuthor;
					ps.info[SONG_FLAGS] = ps.currentSongInfo.canSeek ? 1 : 0;
					ps.info[SONG_STATE] = 1;
					ps.info[SONG_POS] = 0;
					ps.info[SONG_SOURCE] = "";
					ps.info[SONG_BUFFERING] = -1;
					
            		if(ps.currentSongInfo.length == -1)
            			ps.info[SONG_LENGTH] = ps.defaultLength;

					
					if(ps.currentSongInfo.source != null && ps.currentSongInfo.source.length() > 0)					
						ps.info[SONG_SOURCE] = ps.currentSongInfo.source;
					else if(ps.playListName != null) {
						ps.info[SONG_SOURCE] = ps.playListName;
					}
					
					Log.d(TAG, "SOURCE IS " + ps.currentSongInfo.source);

					if(lastFileName == null || !lastFileName.equals(ps.currentSongInfo.fileName)) {
						if(ps.ttsStatus >= 0) {
							ps.speakTitle();
						}
					}
					
					if(hasRemoteControl)
						ps.remoteControl.setMetaData((String) ps.info[SONG_AUTHOR], (String) ps.info[SONG_TITLE]);        			

					ps.performCallback(SONG_FILENAME, SONG_TITLE, SONG_POS, SONG_SUBTUNE_TITLE, SONG_SUBTUNE_AUTHOR, SONG_AUTHOR, SONG_COPYRIGHT, SONG_LENGTH, SONG_FLAGS, SONG_SUBSONG, SONG_TOTALSONGS, SONG_SOURCE, SONG_REPEAT, SONG_STATE);
                	break;
                case Player.MSG_DONE:
                	Log.d(TAG, "Music done");
                	if((Integer)ps.info[SONG_REPEAT] == RM_CONTINUE) {
                		ps.playNextSong();
                	}// else {
                	//	ps.info[SONG_STATE] = 0;
                	//	performCallback(SONG_STATE);
                	//}
                    break;
                case Player.MSG_PROGRESS:
                	int l = (Integer)ps.info[SONG_LENGTH];
                	if(l < 0) {
                		l = ps.defaultLength;
                	}
                	//Log.d(TAG, "%d vs %d", msg.arg1, l);
                	if(l > 0 && (msg.arg1 >= l) && ((Integer)ps.info[SONG_REPEAT] == RM_CONTINUE)) {
                		ps.playNextSong();
                	} else {                	
                    	int pos = (Integer)msg.arg1;
                    	int buffering = (Integer)msg.arg2;
                    	if(pos >= 0 && buffering >= 0) {
	                    	ps.info[SONG_BUFFERING] = buffering;
	                    	ps.info[SONG_POS] = pos;
	                    	ps.performCallback(SONG_BUFFERING, SONG_POS);
                    	} else if(pos >= 0) {
	                    	ps.info[SONG_POS] = pos;
	                    	ps.performCallback(SONG_POS);
                    	} else {
	                    	ps.info[SONG_BUFFERING] = buffering;
	                    	ps.performCallback(SONG_BUFFERING);
                    	}
                	}
    				break;
                case Player.MSG_STATE:
                	ps.info[SONG_STATE] = (Integer)msg.arg1;
                	
                	if(msg.arg1 == 0) {
                		ps.info[SONG_POS] = ps.info[SONG_SUBSONG] = ps.info[SONG_TOTALSONGS] = ps.info[SONG_LENGTH] = 0;
                		ps.currentSongInfo.fileName = null;
                		ps.performCallback(SONG_POS, SONG_SUBSONG, SONG_TOTALSONGS, SONG_LENGTH, SONG_STATE);
                	} else {                	
                		ps.performCallback(SONG_STATE);
                	}
                	break;
                /*case Player.MSG_SILENT:
                	if(silenceDetect) {
	                	if((Integer)ps.info[SONG_REPEAT] == RM_CONTINUE) {
	                		playNextSong();
	                	} else {
	                		Log.d(TAG, "User has interferred, not switching");
	                	}
                	}
                	break;*/
                default:
                    super.handleMessage(msg);
            }
        }
	};
		
	
    private Handler mHandler = null; /*new Handler() {

		@Override
        public void handleMessage(Message msg) {
        	//Log.d(TAG, "Got msg %d with arg %s", msg.what, (String)msg.obj);
			String [] sa;
            switch (msg.what) {
            	case Player.MSG_WAVDUMPED:
            		info[SONG_FILENAME] = msg.obj;
            		performCallback(SONG_FILENAME);
            		info[SONG_FILENAME] = null;
            		break;
            	case Player.MSG_DETAILS:
            		sa = (String [])msg.obj;
            		info[SONG_DETAILS] = "DETAILS";
            		currentSongInfo.details = sa;
            		performCallback(SONG_DETAILS);
            		Log.d(TAG, "%%%%%%%% Sending %d details", sa.length);
            		break;
            	case Player.MSG_INFO:
        			sa = (String [])msg.obj;
        			info[SONG_TITLE] = sa[0];
        			info[SONG_AUTHOR] = sa[1];
        			
					if(hasRemoteControl)
						remoteControl.setMetaData((String) info[SONG_AUTHOR], (String) info[SONG_TITLE]);
        			
        			speakTitle();
        			performCallback(SONG_TITLE, SONG_AUTHOR);
        			break;
            	case Player.MSG_SUBTUNE:
            		Log.d(TAG, "SUBTUNE %d, Length %d", msg.arg1, msg.arg2);
            		info[SONG_SUBSONG] = msg.arg1;
            		info[SONG_LENGTH] = msg.arg2;
            		
            		if(msg.arg2 == -1)
            			info[SONG_LENGTH] = defaultLength;
            		
            		info[SONG_STATE] = 1;
            		if(msg.obj != null) {
            			sa = (String [])msg.obj;
            			info[SONG_SUBTUNE_TITLE] = sa[0];
            			info[SONG_SUBTUNE_AUTHOR] = sa[1];
            			

            			
            			if(msg.arg1 < 0) {

        					if(hasRemoteControl)
        						remoteControl.setMetaData((String) info[SONG_SUBTUNE_AUTHOR], (String) info[SONG_SUBTUNE_TITLE]);

            				speakTitle();
            			}
            			
                		performCallback(SONG_SUBSONG, SONG_LENGTH, SONG_SUBTUNE_TITLE, SONG_SUBTUNE_AUTHOR, SONG_STATE);
                		break;                		
            		}
            		performCallback(SONG_SUBSONG, SONG_LENGTH, SONG_STATE);
            		break;
                case Player.MSG_NEWSONG:
                	
                	
                	String lastFileName = currentSongInfo.fileName;
                	
                	player.getSongInfo(currentSongInfo);
                	                	
                	info[SONG_TITLE] = currentSongInfo.title;
					info[SONG_AUTHOR] = currentSongInfo.author;
					info[SONG_COPYRIGHT] = currentSongInfo.copyright;
					info[SONG_FORMAT] = currentSongInfo.type;
					info[SONG_LENGTH] = currentSongInfo.length;
					info[SONG_TOTALSONGS] = currentSongInfo.subTunes;
					info[SONG_SUBSONG] = currentSongInfo.startTune;
					info[SONG_REPEAT] = defaultRepeatMode;
					info[SONG_SUBTUNE_TITLE] = currentSongInfo.subtuneTitle;
					info[SONG_SUBTUNE_AUTHOR] = currentSongInfo.subtuneAuthor;
					info[SONG_FLAGS] = currentSongInfo.canSeek ? 1 : 0;
					info[SONG_STATE] = 1;
					info[SONG_SOURCE] = "";
					info[SONG_BUFFERING] = -1;
					
            		if(currentSongInfo.length == -1)
            			info[SONG_LENGTH] = defaultLength;

					
					if(currentSongInfo.source != null && currentSongInfo.source.length() > 0)					
						info[SONG_SOURCE] = currentSongInfo.source;
					else if(playListName != null) {
						info[SONG_SOURCE] = playListName;
					}
					
					Log.d(TAG, "SOURCE IS " + currentSongInfo.source);

					if(lastFileName == null || !lastFileName.equals(currentSongInfo.fileName)) {
						if(ttsStatus >= 0) {
							speakTitle();
						}
					}
					
					if(hasRemoteControl)
						remoteControl.setMetaData((String) info[SONG_AUTHOR], (String) info[SONG_TITLE]);        			

					performCallback(SONG_FILENAME, SONG_TITLE, SONG_SUBTUNE_TITLE, SONG_SUBTUNE_AUTHOR, SONG_AUTHOR, SONG_COPYRIGHT, SONG_LENGTH, SONG_FLAGS, SONG_SUBSONG, SONG_TOTALSONGS, SONG_SOURCE, SONG_REPEAT, SONG_STATE);
                	break;
                case Player.MSG_DONE:
                	Log.d(TAG, "Music done");
                	if((Integer)info[SONG_REPEAT] == RM_CONTINUE) {
                		playNextSong();
                	}// else {
                	//	info[SONG_STATE] = 0;
                	//	performCallback(SONG_STATE);
                	//}
                    break;
                case Player.MSG_PROGRESS:
                	int l = (Integer)info[SONG_LENGTH];
                	if(l < 0) {
                		l = defaultLength;
                	}
                	//Log.d(TAG, "%d vs %d", msg.arg1, l);
                	if(l > 0 && (msg.arg1 >= l) && ((Integer)info[SONG_REPEAT] == RM_CONTINUE)) {
                		playNextSong();
                	} else {                	
                    	int pos = (Integer)msg.arg1;
                    	int buffering = (Integer)msg.arg2;
                    	if(pos >= 0 && buffering >= 0) {
	                    	info[SONG_BUFFERING] = buffering;
	                    	info[SONG_POS] = pos;
	                		performCallback(SONG_BUFFERING, SONG_POS);
                    	} else if(pos >= 0) {
	                    	info[SONG_POS] = pos;
	                		performCallback(SONG_POS);
                    	} else {
	                    	info[SONG_BUFFERING] = buffering;
	                		performCallback(SONG_BUFFERING);
                    	}
                	}
    				break;
                case Player.MSG_STATE:
                	info[SONG_STATE] = (Integer)msg.arg1;
                	
                	if(msg.arg1 == 0) {
                		info[SONG_POS] = info[SONG_SUBSONG] = info[SONG_TOTALSONGS] = info[SONG_LENGTH] = 0;
                		currentSongInfo.fileName = null;
                		performCallback(SONG_POS, SONG_SUBSONG, SONG_TOTALSONGS, SONG_LENGTH, SONG_STATE);
                	} else {                	
                		performCallback(SONG_STATE);
                	}
                	break;
                default:
                    super.handleMessage(msg);
            }
        }

    }; */

	
    void createThread() {
    	
    	if(playerThread != null) {
			if(!playerThread.isAlive()) {
				playerThread = null;
			}
    	}
    	
    	if(playerThread == null) {
			Log.d(TAG, "Creating thread");
			player.setBufSize(bufSize);
		    playerThread = new Thread(player);
		    playerThread.setPriority(Thread.MAX_PRIORITY);
		    playerThread.start();
    	}
    }
    
    
    void updatePlaylist() {
    /*	if(currentPlaylist != null) {
    		int hash = currentPlaylist.hashCode();
    		if(hash != oldPlaylistHash) {
    			Log.d(TAG, "Current playlist has changed!");    			
    			List<File> files = currentPlaylist.getFiles();
    			
    			String current =  musicList[shuffleArray[musicListPos]];
    			
    			for(int i=0; i<files.size(); i++) {
    				if(files.get(i).getPath().equals(current)) {
    					break;
    				}
    			}
    			
    			
    			musicList = new String [files.size()];
    			int i = 0;
    			for(File f : files) {
    				musicList[i++] = f.getPath();				
    			}
    			
    			
    		}
    		oldPlaylistHash = hash;
    	} */
    }

    public boolean playNextSong() {
    	
    	if(cycleSubTunes) {
	    	int next = (Integer)info[SONG_SUBSONG] + 1;
	    	if(next < (Integer)info[SONG_TOTALSONGS]) {
	        	player.setSubSong(next);
	    		info[SONG_SUBSONG] = (Integer)next;			
	    		return true;
	    	}
    	}
    	return playNextFile();
    }
    
    public boolean playNextFile() {
    	
    	if(playQueue == null) {
    		return false;
    	}
    	SongFile song = playQueue.next();
    	
		if(song != null) {    			
       		song = playQueue.current();       		
       		info[SONG_FILENAME] = song.getPath();
       		createThread();

       		beforePlay(song.getName());
       		player.playMod(song);
       		return true;
    	}
		return false;
    }

    public boolean playPrevSong() {
    	
    	
    	if(cycleSubTunes) {
	    	int next = (Integer)info[SONG_SUBSONG] - 1;
	    	if(next >= 0) {
	        	player.setSubSong(next);
	    		info[SONG_SUBSONG] = (Integer)next;			
	    		return true;
	    	}
    	}
    	return playPrevFile();
    }

   public boolean playPrevFile() {
	   
    	if(playQueue == null) {
    		return false;
    	}
    	SongFile song = playQueue.prev();    	
		if(song != null) {    			
       		song = playQueue.current();
       		info[SONG_FILENAME] = song.getPath();
       		createThread();
       		beforePlay(song.getName());
       		player.playMod(song);
       		return true;
    	}
		return false;
    }
  
	private Notification notification;

	private PendingIntent contentIntent;

	protected int bufSize = 0x40000;

	protected int defaultLength = 60*15*1000;

	protected String playListName;

	protected int callState = TelephonyManager.CALL_STATE_IDLE;

    
	@TargetApi(8)
	@SuppressWarnings("deprecation") // Notifications needs to be backward compatible
	@Override
	public void onCreate() {
		super.onCreate();
		
		DroidSoundPlugin.setContext(getApplicationContext());
		
		mHandler = new MyHandler(this);
		
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		player = new Player(audioManager, mHandler, getApplicationContext());
		callbacks = new ArrayList<IPlayerServiceCallback>();
		info = new Object [SONG_SIZEOF];
		for(int i=0; i<SONG_SIZEOF; i++)
			info[i] = null;
		
		
		phoneStateListener = new PhoneStateListener() {
			
			//boolean didPause = false;
			
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				
				callState  = state;
				switch(state) {
				case TelephonyManager.CALL_STATE_RINGING:
				case TelephonyManager.CALL_STATE_OFFHOOK:
					if(player != null && player.isPlaying()) {
						player.paused(true);
						//didPause = true;
					}
					break;
				/*case TelephonyManager.CALL_STATE_IDLE:
					if(didPause && player != null && !player.isPlaying()) {
						player.paused(false);
					}
					didPause = false;
					break; */
				}
				
				Log.d(TAG, "CALL STATE %d %s", state, incomingNumber);
				//super.onCallStateChanged(state, incomingNumber);
			}
		};
		
		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        mediaReceiver = new BroadcastReceiver() {        	
			private long unpluggedTime = -1;
			private int lastState = -1;

			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG, "##### GOT INTENT %s", intent.getAction());
				
				if(intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
					int state = intent.getIntExtra("state", -1);
					
					if(lastState  != -1 && lastState != state) {					
						Log.d(TAG, "HEADSET PLUG " + state);
						if(state == 0) {						
							if(player.isPlaying()) {
								unpluggedTime = System.currentTimeMillis();
								player.paused(true);						
							}
						} else if(state == 1) {
							if(unpluggedTime > 0 && (System.currentTimeMillis() - unpluggedTime) < 5000) {
								if(!player.isPlaying()) {
									player.paused(false);						
								}							
							}
							unpluggedTime = -1;
						}
					}
					lastState = state;
				}
			}
		};
				
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		registerReceiver(mediaReceiver, filter);

		ComponentName myEventReceiver = new ComponentName(getPackageName(), RemoteControlReceiver.class.getName());
		getPackageManager().setComponentEnabledSetting(myEventReceiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP); 		

		AudioManager myAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		try {
			myAudioManager.registerMediaButtonEventReceiver(myEventReceiver);
		} catch (Throwable t) {
		}

		if(hasRemoteControl)
			remoteControl = new RemoteControlWrapper(this, myEventReceiver); 
		if(hasAudioFocus)
			afWrapper = new AudioFocusWrapper(this, player);
		
		notification = new Notification(R.drawable.note36, "Droidsound", System.currentTimeMillis());		
		Intent notificationIntent = new Intent(this, PlayerActivity.class);
		contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);
	    notification.setLatestEventInfo(this, "Droidsound", "Playing", contentIntent);
	    
	    playerInterface = this;
	    
	}
	
	@SuppressWarnings("deprecation")
	void beforePlay(String name) {
		notification.setLatestEventInfo(this, "Droidsound", name, contentIntent);
		startForeground(R.string.notification, notification);
		//AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		//am.requestAudioFocus(this, AudioManager.STREAM_MUSIC,  AudioManager.AUDIOFOCUS_GAIN);
		if(hasAudioFocus)
			afWrapper.requestFocus();
		
		if(hasRemoteControl)
			remoteControl.setState(RemoteControlWrapper.PLAYING);
	}
	
	void whenStopped() {
		if(hasRemoteControl)
			remoteControl.setState(RemoteControlWrapper.STOPPED);
		stopForeground(true);
		//AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		//am.abandonAudioFocus(this);
		if(hasAudioFocus)
			afWrapper.abandonFocus();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		Log.d(TAG, "Service started: %x %d", flags, startId);
		if(intent != null) {
			Log.d(TAG, "Intent: %s", intent.toString());
			String action = intent.getAction();
			if(action != null)
				Log.d(TAG, "Intent %s / %s", action, intent.getDataString());
	        if(intent.getAction() != null && intent.getAction().contentEquals(Intent.ACTION_VIEW)) {
				Uri uri = intent.getData();
				if(uri == null) {
					Bundle b = intent.getExtras();		
					SongFile song = null;
					String ff =  b.getString("musicFile");
					if(ff != null) {
						song = new SongFile(ff);
					}
					int index = b.getInt("musicPos");
					String [] names = (String []) b.getSerializable("musicList");
					
					playQueue = new PlayQueue(names, index, shuffleSongs);
					
					if(song == null) {
						song = playQueue.current();
					}
	
					createThread();
					if(song == null) {
						String modname = (String) info[SONG_FILENAME];
						if(names != null && modname != null) {
							Log.d(TAG, "Got playlist without song");
						}
					} else {
						Log.d(TAG, "Want to play list with " + song.getPath());
						info[SONG_FILENAME] = song.getPath();
						playListName = "";
						beforePlay(song.getName());
						player.playMod(song);
					}
				} else {
					Log.d(TAG, "Want to play " + intent.getDataString());
					createThread();
					playListName = "";
					info[SONG_FILENAME] = uri.getLastPathSegment();
					SongFile song = new SongFile(intent.getDataString());
					beforePlay(song.getName());
					player.playMod(song);
				}
			}
		}
        return Service.START_STICKY_COMPATIBILITY;
	}
	
	@Override
	public void onDestroy() {
		
		super.onDestroy();
		
		//wakeLock.release();
		
		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(phoneStateListener, 0);
		
		unregisterReceiver(mediaReceiver);
		
		player.stop();
		whenStopped();
		player = null;
		playerThread = null;
	}
	
	private final IPlayerService.Stub mBinder = new IPlayerService.Stub() {

		@Override
		public boolean playMod(String name) throws RemoteException {
			Log.d(TAG, "Playmod called " + name);
			
			createThread();
			info[SONG_FILENAME] = name;
			SongFile song = new SongFile(name);
			beforePlay(song.getName());			
			player.playMod(song);
			return true;
		}

		@Override
		public void registerCallback(IPlayerServiceCallback cb, int flags) throws RemoteException {
			
			if(cb != null) {
				for(int i=1; i<SONG_SIZEOF; i++) {
					try {
						if(info[i] != null) {
							if(info[i] instanceof String) {
								cb.stringChanged(i, (String)info[i]);
							} else {
								cb.intChanged(i, (Integer)info[i]);
							}
						}
					} catch (RemoteException e) {
						Log.d(TAG, "Ignoring callback because peer is gone");
						return;
					}				
				}
			}
			Log.d(TAG, "Adding %s", cb.toString());
			callbacks.add(cb);			
		}

		@Override
		public void unRegisterCallback(IPlayerServiceCallback cb)
				throws RemoteException {
			
			Log.d(TAG, "Removing %s", cb.toString());
			callbacks.remove(cb);
		}

		
		@Override
		public boolean playPause(boolean play) throws RemoteException {
			if((!player.isActive() || player.isSwitching()) && play && playQueue != null) {
				SongFile s = playQueue.current();
				if(s != null) {
	           		info[SONG_FILENAME] = s.getPath(); 		           		
	           		createThread();
	    			beforePlay(s.getName());			
	           		player.playMod(s);
	           		return true;
	    		}
			}
			player.paused(!play);
			return true;
			//}
			//return false;
		}

		@Override
		public void stop() throws RemoteException {
			player.stop();
			whenStopped();
	    	//userInterferred = false;
			info[SONG_REPEAT] = defaultRepeatMode;
			performCallback(SONG_REPEAT);
		}


		@Override
		public boolean seekTo(int msec) throws RemoteException {
			player.seekTo(msec);
			info[SONG_POS] = msec;
			performCallback(SONG_POS);
			
			/*if((Integer)info[SONG_REPEAT] == RM_CONTINUE) {
				info[SONG_REPEAT] = RM_KEEP_PLAYING;
				performCallback(SONG_REPEAT);
			} */
			return true;
		}

		@Override
		public boolean setSubSong(int song) throws RemoteException {

	    	if(playQueue == null) {
	    		return false;
	    	}
			
			// TODO : Check if next song is the same file and the same sub song
			/* boolean ok = false;
			Log.d(TAG, "Current song is " + currentSongInfo.fileName);
			SongFile nextSong = playQueue.getNextSong();			
			if(nextSong != null) {
				Log.d(TAG, "Next song is " + nextSong.getName());
				if(song == nextSong.startSong && currentSongInfo.fileName.equals(nextSong.filename)) {
					playQueue.next();
					ok = true;
				}
			}
			if(!ok) {
				nextSong = playQueue.getPrevSong();				
				if(nextSong != null) {
					Log.d(TAG, "Prev song is " + nextSong.filename);
					if(song == nextSong.startSong && currentSongInfo.fileName.equals(nextSong.filename)) {
						playQueue.prev();
						ok = true;						
					}
				}
			} */
			
			
 			player.setSubSong(song);
			info[SONG_SUBSONG] = (Integer)song;			
			//if((Integer)info[SONG_REPEAT] == RM_CONTINUE) {
			//	info[SONG_REPEAT] = RM_KEEP_PLAYING;
			//	performCallback(SONG_REPEAT);
			//}
			
			//if(ok) {
			//	info[SONG_FILENAME] = nextSong.filename + ";" + song;
			//	performCallback(SONG_FILENAME);
			//}
			
			return true;
			
		}
		
		private int BUFSIZE(double sec) {			
			return (int)(sec*44100*2) & 0xffffffe0;			
		}
		
		@Override
		public void setOption(int opt, String arg) throws RemoteException {

			boolean on = arg.equals("on");
			
			try {
				switch(opt) {
				case OPTION_CYCLE_SUBTUNES:
					cycleSubTunes  = on;
					break;
				case OPTION_DEFAULT_LENGTH:
					defaultLength  = Integer.parseInt(arg) * 1000;
					Log.d(TAG, "Default length set to " + defaultLength);
					break;
				case OPTION_REPEATMODE:
					info[SONG_REPEAT] = Integer.parseInt(arg);
					performCallback(SONG_REPEAT);
					break;
				//case OPTION_SILENCE_DETECT:
				//	Log.d(TAG, "Silence detection " + arg);
				//	silenceDetect = on;
				//	break;
				case OPTION_PLAYBACK_ORDER:
					if(arg.charAt(0) == 'R') {
						shuffleSongs = true;
					} else if(arg.charAt(0) == 'S') {
						shuffleSongs = false;
					}
					if(playQueue != null) {
						playQueue.setShuffle(shuffleSongs);
					}
					break;
				//case OPTION_RESPECT_LENGTH:
				//	Log.d(TAG, "Respect length " + arg);
				//	respectLength = on;
				//	break;
				case OPTION_SPEECH:
					activateSpeech(on);
					break;
				case OPTION_BUFFERSIZE:
			 		bufSize = BUFSIZE(2.0);
			 		if(arg.equals("Short")) {
			 			bufSize = BUFSIZE(0.5);
			 		} else
			 		if(arg.equals("Medium")) {
			 			bufSize = BUFSIZE(1.0);
			 		} else
			 		if(arg.equals("Very Long")) {
			 			bufSize = BUFSIZE(4.0);
			 		}
			 		if(player != null)
			 			player.setBufSize(bufSize);
					break;
				}
			} catch (NumberFormatException e) {
			}
		}
		
/*
		private void shuffle() {
			String t;
			int sz = musicList.size();
			for (int i=musicListPos+1; i<sz; i++) {					
			    int randomPosition = rgen.nextInt(sz-1) + 1;
			    t = musicList.get(i);
			    musicList.set(i, musicList.get(randomPosition));
			    musicList.set(randomPosition, t);
			}
		}
		
		private void unshuffle() {
			
			musicList.clear();
			for(int i=0; i<musicNames.length; i++) {
				musicList.add(musicNames[i]);
			}
		}
*/
		@Override
		public boolean playPlaylist(String name, int startIndex) throws RemoteException {
			
			File pf = new File(name);
			Playlist pl = Playlist.getPlaylist(pf);
			playQueue = new PlayQueue(pl, startIndex, shuffleSongs);

			SongFile mod = playQueue.current();       		

			createThread();
			info[SONG_FILENAME] = mod.getPath();
			
			int dot = name.lastIndexOf('.');
			if(dot > 0) {
				name = name.substring(0, dot);
			}
			playListName = name;
						

			beforePlay(mod.getName());
			player.playMod(mod);
	    	info[SONG_REPEAT] = defaultRepeatMode;
			//performCallback(SONG_SOURCE, SONG_REPEAT);

	    	return true;
	    	
			/*
			File pf = new File(name);
			currentPlaylist = Playlist.getPlaylist(pf);
			Log.d(TAG, "File %s is playlist %s", name, currentPlaylist.toString());
			List<File> files = currentPlaylist.getFiles();
			int i = 0;			
			for(File f : files) {
				musicNames[i] = f.getPath();
			}
			
			return playList(null, startIndex); */
		}

		@Override
		public boolean playList(String[] names, int startIndex) throws RemoteException {
			
			
			playQueue = new PlayQueue(names, startIndex, shuffleSongs);
			/*
			if(names != null) {
				musicNames = names;
			}
			musicList = new ArrayList<String>();
			
			for(int i=0; i<names.length; i++) {
				musicList.add(names[i]);
			}			
			musicListPos = startIndex;
			String name = musicList.get(startIndex);
			if(shuffleSongs) {
				shuffle();								
			} */
			SongFile song = playQueue.current();       		
			Log.d(TAG, "PlayList called " + song.getPath());
			createThread();
			info[SONG_FILENAME] = song.getPath();
			playListName = "";
			beforePlay(song.getName());
			player.playMod(song);
	    	info[SONG_REPEAT] = defaultRepeatMode;
			performCallback(SONG_REPEAT);


			return false;
		}

		@Override
		public void playNext() throws RemoteException {
			Log.d(TAG, "### PLAY NEXT");
	    	info[SONG_REPEAT] = defaultRepeatMode;
			performCallback(SONG_REPEAT);
			playNextFile();
		}

		@Override
		public void playPrev() throws RemoteException {
	    	info[SONG_REPEAT] = defaultRepeatMode;
			performCallback(SONG_REPEAT);
			playPrevFile();
		}

		@Override
		public String[] getSongInfo() throws RemoteException {

			return currentSongInfo.details;
		}

		@Override
		public byte[] getSongMD5() throws RemoteException {

			return currentSongInfo.md5;
		}

		@Override
		public boolean dumpWav(String modName, String destFile, int length, int flags) throws RemoteException {
			// TODO Auto-generated method stub
			
			createThread();
			info[SONG_FILENAME] = modName;
			SongFile song = new SongFile(modName);
			//beforePlay(song.getName());			
			player.dumpWav(song, destFile, length, flags);
			
			return false;
		}

		};

	
    @Override
    public IBinder onBind(Intent intent) {
    	Log.d(TAG, "BOUND");
    	return mBinder;
    }
    
    protected void activateSpeech(boolean on) {
    	if(on) {
			if(textToSpeech == null) {
				Log.d(TAG, "Turning on speech");
				textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
					@Override
					public void onInit(int status) {					
						ttsStatus = status;
						if(saySomething != null && textToSpeech != null) {
							textToSpeech.speak(saySomething, TextToSpeech.QUEUE_FLUSH, null);
							saySomething = null;
						}
							
					}
				});
				textToSpeech.setLanguage(Locale.US);
			}
		} else {
			if(textToSpeech != null) {
				textToSpeech.shutdown();
				textToSpeech = null;
				ttsStatus = -1;
				Log.d(TAG, "Turning off speech");
			}
		}
    }
   
	@Override
    public boolean onUnbind(Intent intent) {
    	Log.d(TAG, "UNBOUND");
    	return super.onUnbind(intent);
    }

	@Override
	public boolean isActive() {
		return player.isActive();
	}

	@Override
	public void stop() {
		player.stop();
		whenStopped();
		
	}

	@Override
	public boolean isPlaying() {
		return player.isPlaying();
	}

	@Override
	public void paused(boolean b) {
		
		if(hasRemoteControl)
			remoteControl.setState(b ? RemoteControlWrapper.PAUSED : RemoteControlWrapper.PLAYING);		
		player.paused(b);		
	}

	@Override
	public void speechOnOff() {
		if(textToSpeech == null) {
			speakTitle();
			activateSpeech(true);									
		} else {
			textToSpeech.speak("Speech off.", TextToSpeech.QUEUE_FLUSH, null);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			activateSpeech(false);
		} 
	}

	@Override
	public int getCallState() {
		return callState;
	}
}
