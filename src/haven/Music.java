package haven;

import java.io.*;
import javax.sound.midi.*;

public class Music {
    private static Player player;
    private static boolean debug = false;
    
    private static void debug(String str) {
	if(debug)
	    System.out.println(str);
    }

    private static class Player extends Thread {
	private Resource res;
	private Thread waitfor;
	private Sequencer seq;
	private Synthesizer synth;
	private boolean done;
	private boolean loop = false;
	
	private Player(Resource res, Thread waitfor) {
	    super(Utils.tg(), "Music player");
	    setDaemon(true);
	    this.res = res;
	    this.waitfor = waitfor;
	}
	
	public void run() {
	    try {
		if(waitfor != null)
		    waitfor.join();
		res.loadwaitint();
		try {
		    seq = MidiSystem.getSequencer(false);
		    synth = MidiSystem.getSynthesizer();
		    seq.open();
		    seq.setSequence(res.layer(Resource.Music.class).seq);
		    synth.open();
		    seq.getTransmitter().setReceiver(synth.getReceiver());
		} catch(MidiUnavailableException e) {
		    return;
		} catch(InvalidMidiDataException e) {
		    return;
		} catch(IllegalArgumentException e) {
		    /* The soft synthesizer appears to be throwing
		     * non-checked exceptions through from the sampled
		     * audio system. Ignore them and only them. */
		    if(e.getMessage().startsWith("No line matching"))
			return;
		    throw(e);
		}
		seq.addMetaEventListener(new MetaEventListener() {
			public void meta(MetaMessage msg) {
			    debug("Meta " + msg.getType());
			    if(msg.getType() == 47) {
				synchronized(Player.this) {
				    done = true;
				    Player.this.notifyAll();
				}
			    }
			}
		    });
		do {
		    debug("Start loop");
		    done = false;
		    seq.start();
		    synchronized(this) {
			while(!done)
			    this.wait();
		    }
		    seq.setTickPosition(0);
		} while(loop);
	    } catch(InterruptedException e) {
	    } finally {
		debug("Exit player");
		if(seq != null)
		    seq.close();
		if(synth != null)
		    synth.close();
		synchronized(Music.class) {
		    if(player == this)
			player = null;
		}
	    }
	}
    }
    
    public static void play(Resource res, boolean loop) {
	synchronized(Music.class) {
	    if(player != null)
		player.interrupt();
	    if(res != null) {
		player = new Player(res, player);
		player.loop = loop;
		player.start();
	    }
	}
    }
    
    public static void main(String[] args) throws Exception {
	Resource.addurl(new java.net.URL("https://www.havenandhearth.com/res/"));
	debug = true;
	play(Resource.load(args[0]), (args.length > 1)?args[1].equals("y"):false);
	player.join();
    }
}
