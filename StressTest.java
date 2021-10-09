/*
 * Version 2.2, Date 09 June 2021
 * 
 * This StressTest program requires Java 8. If your Java version is out of date, 
 * please update to Java 8.
 * 
 * This StressTest program should be copied into and then invoked from the same 
 * directory as your maze game program. 
 * 
 * If your maze game program uses RMI, then RMI registry should be started first
 * in the background, before running StressTest. The StressTest program itself does 
 * not use RMI. 
 * 
 * You should start your Tracker program before running StressTest. 
 * *** The maze size must be 15*15, and the number of treasures must be 10. ***
 * 
 * StressTest should be invoked in the following way with 3 arguments:
 * 		java StressTest [IP-address] [port-number] [your Game program] 
 * Here [IP-address] and [port-number] are the IP address and the port over which your 
 * Tracker program listens.
 * 
 * As an example, suppose that your Tracker is running on local host and the port is 6789.
 * Suppose that your Game program is "Game.exe". Then you should invoke StressTest as:
 * 		java StressTest 127.0.0.1 6789 Game.exe
 * Internally, StressTest will invoke the following command in separate processes:
 * 		Game.exe 127.0.0.1 6789 [player-id]
 * Here the [player-id] is the name of the player, which will be generated randomly 
 * internally by StressTest.
 * 
 * If your Game program is a Java program, then you will have a Game.class. 
 * You should invoke StressTest as:
 * 		java StressTest 127.0.0.1 6789 "java Game"
 * Here the quotes are important since they make sure that "java Game" is 
 * treated as a single argument. Internally, StressTest will invoke the following 
 * command in separate processes:
 * 		java Game 127.0.0.1 6789 [player-id]
 * 
 * StressTest will create a new directory "CS5223_StressTest12123" 
 * and write the standard output/error of your Game program into 
 * files in that directory. These files can be freely deleted after StressTest ends.
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class StressTest {
	static String ip = null;
	static String port = null;
	static String gameProg = null;
	static int idtick = 0;
	static boolean shutdownInProgress = false; //set to true once only during shutdown.

	public static void main(String[] args) {
		System.out.println("You should fully debug your Game program "
			+ "using your own testing code, before trying StressTest. "
			+ "StressTest does not serve to help you to debug. "
			+ "Have you fully debugged your Game program? [1] Yes [2] No");
		try {
			String ans = (new Scanner(System.in)).nextLine().toLowerCase();
			String[] yes = {"1", "y", "yes"};
			if ( !java.util.Arrays.asList(yes).contains(ans) ) {
				System.out.println("Please fully debug your code first.");
				System.exit(0);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		if (args.length != 3) {
			System.out.println("Wrong number of parameters...exiting");
			System.exit(0);
		}
		ip = args[0];
		port = args[1];
		gameProg = args[2];
		File theDir = new File("CS5223_StressTest12123");
		if (!theDir.exists()) {
			try {
				theDir.mkdir();
			} catch (SecurityException e) {
				e.printStackTrace();
				System.out.println("Unexpected error. Please contact TA");
				System.exit(0);
			}
		}

		Vector<StressTestPlayer> allPlayers = new Vector<StressTestPlayer>();

		// ShutdownHook kills all remaining player. 
		// Blocked if the main thread is adding/removing/talking to any player.
		// No need to give 3 second grace period between killing, because we don't check the results any more.
		Runtime.getRuntime().addShutdownHook(
			new Thread("Shutdown Hook"){
				public void run() {  //It only run during shutdown
					synchronized(allPlayers) {
						shutdownInProgress = true; //Once set to true, never becomes false.
						System.out.println("Shutdown in progress...");
						for (int i = 0; i < allPlayers.size(); i++) {
							StressTestPlayer player = allPlayers.elementAt(i);
							System.out.println("Killing player " + player.playerid);
							player.killed = true;
							player.extprocess.destroyForcibly();
						}
					}
				} // run
			} // new Thread
		);
                
		Random random = new Random(20210609);
		Scanner scan = new Scanner(System.in);
		int step = 0;
		while (step < 200) {
			step++;
			System.out.println("Current step is " + step);
			if ((step == 5) || (step == 30) || (step == 40) || (step == 60)
					|| (step == 200)) {
				System.out.println("######### Checkpoint at step " + step
						+ " #########");
				doSlowMoves(allPlayers, random, scan);
			}

			if (step <= 4) {
				createPlayer(allPlayers);
				continue;
			}

			if (step <= 30) {
				doFastMoves(allPlayers, random);
				continue;
			}

			// step must be above 30 now...
			int choice = random.nextInt(3);
			if (choice == 0) {
				doFastMoves(allPlayers, random);
			} else if ((choice == 1) && (allPlayers.size() <= 4)) {
				createPlayer(allPlayers);
				doFastMoves(allPlayers, random);
			} else if ((choice == 2) && (allPlayers.size() >= 3)) {
				killPlayer(allPlayers, random);
			}
		} // while

		System.out.println("Stress test ends after all steps.");
		System.out.println("TA should record how many checkpoints were passed correctly.");
		System.exit(0);
	} // main

	private static String createPlayerid() {
		idtick++;
		char first = (char) (97 + idtick / 26);
		char second = (char) (97 + idtick % 26);
		String id = Character.toString(first) + Character.toString(second);
		return id;
	}

	private static void createPlayer(Vector<StressTestPlayer> l) {
		String id = createPlayerid();
		String command = gameProg + " " + ip + " " + port + " " + id;
		StressTestPlayer player = new StressTestPlayer(command, id);
		boolean success = false;
		synchronized(l) { 
			if (!shutdownInProgress) {
				System.out.println("Creating player using command: " + command);
				System.out.println("Wait up to 5 seconds when creating a new player.");
				success = player.myStart();
				if (success) l.add(player);
			}
		}
		if (!success) System.exit(0);
	}

	private static void killPlayer(Vector<StressTestPlayer> l, Random r) {
		StressTestPlayer player = l.elementAt(r.nextInt(l.size()));
		player.killed = true;
		boolean success = false;
		synchronized(l) { 
			if (!shutdownInProgress) {
				System.out.println("Killing player " + player.playerid);
				System.out.println("Wait up to 5 seconds when killing a player.");
				player.extprocess.destroyForcibly();
				l.remove(player);
				
				try {
					long t = 5;
					// If the process still has not terminated after 5 seconds, 
					// it means that we did not successfully kill it.
					if (!player.extprocess.waitFor(t, TimeUnit.SECONDS)) {
						System.out.println("Unexpected error. Please contact TA. "
							+ "Unable to kill in 5secs.");
						success = false;
					}
					else {
						success = true;
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.out.println("Unexpected error. Please contact TA. "
						+ "Killing player is interrupted.");
					success = false;
				}
			}
		}
		if (!success) System.exit(0);
		
		// The assignment promises 2 seconds gap between successive crashes. 
		// Here we give 3 seconds just to be nice.
		try {
			System.out.println("Wait exactly 3 seconds before next move...");
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.out.println("Unexpected error. Please contact TA. "
				+ "Interrupted while sleeping for 3s.");
			System.exit(0);
		}
	}

	private static void doFastMoves(Vector<StressTestPlayer> l, Random r) {
		for (int i = 0; i < 5; i++) {
			StressTestPlayer player = l.elementAt(r.nextInt(l.size()));
			int direction = r.nextInt(5);
			synchronized(l) { 
				if (!shutdownInProgress) {
					System.out.println("Fast move " + player.playerid + " " + direction);
					player.useraction.println("" + direction); 
				}
			}
		}
	}

	private static void doSlowMoves(Vector<StressTestPlayer> l, Random r,
			Scanner s) {
		for (int i = 0; i < 3; i++) {
			StressTestPlayer player = l.elementAt(r.nextInt(l.size()));
			int direction = r.nextInt(5);
			System.out.println("Plan to move " + player.playerid + " " + direction);
			System.out.println("TA should press return to initiate the move...");
			s.nextLine();

			synchronized(l) { 
				if (!shutdownInProgress) player.useraction.println("" + direction); 
			}

			System.out.println("TA should now verify the move is done correctly");
			System.out.println("TA should press return after verification");
			s.nextLine();
			System.out.println("Continuing the test...");
		}
	}
}

class StressTestPlayer extends Thread {
	String command = null;
	String playerid = null;
	Process extprocess = null;
	PrintStream useraction = null;
	Boolean killed = false;
	public StressTestPlayer(String c, String id) {
		command = c;
		playerid = id;
	}

	public void run() {
		try {
			ProcessBuilder pb = new ProcessBuilder(command.split(" "));
			pb.redirectErrorStream(true);
			String filename = "CS5223_StressTest12123"
					+ System.getProperty("file.separator") + playerid + "_12123";
			pb.redirectOutput(new File(filename));
			extprocess = pb.start();
			useraction = new PrintStream(extprocess.getOutputStream(), true);
			extprocess.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Unable to start Game program. "
				+ "Please double check the command used to launch the Game program. "
				+ "If the command is correct, please contact TA.");
			System.exit(0);
		}

        /*
         * This is the key part that detects whether student's Game program breaks.
         * This part is reachd when i) the Game process is killed by StressTest, or
         * ii) the Game process exits by itself.
         * If StressTest killed this process, then we do nothing.
         * Otherwise it means that the Game program exits without being killed by 
         * StressTest. This is abnormal since the Game program should never exit 
         * (note that StressTest never inputs "9" to the program). Hence we view the 
         * Game program as "crashed" and hence "incorrect", and we will terminate 
         * the StressTest. No further checkpoints will be considered, and grades 
         * will be given based on how much checkpoints the Game program correctly 
         * passes before such "incorrect" behavior. 
        */
		if (!killed) {
			System.out.print("Student's Game program with playid " + playerid);
			System.out.print(" exits unexpectedly. Stress test ends. ");
			System.out.print("Student may check the output files in the ");
			System.out.print("\"CS5223_StressTest12123\" folder for the outputs from");
			System.out.println(" student's Game program.");
			System.exit(0);
		}

	}

	public boolean myStart() {
		this.start();

        // We wait until the student's Game program has been fully started and
        // until after we get the standard input stream of the Game program.
        // If it takes more than 5 seconds, something is probably wrong. 
		int counter = 0;
		while (this.extprocess == null || this.useraction == null) {
			counter++;
			if (counter > 50) {
				System.out.println("Unexpected error. Please contact TA. "
					+ "Unable to start Game program in 5secs.");
				return false;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.out.println("Unexpected error. Please contact TA. "
					+ "Starting Game program is interrupted.");
				return false;
			}
		} // while
		return true;
	} // myStart

}

