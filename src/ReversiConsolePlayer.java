
import szte.mi.Move;
import szte.mi.SuperPlayer;

import java.io.IOException;
import java.util.Random;

public class ReversiConsolePlayer extends Move implements SuperPlayer {
	private static int order;
	private static long timeRemaining;
	private static Random random;
	byte[] buffer = new byte[256];
	int boardSize;
	int x; // Added to store the row
	int y;

	public ReversiConsolePlayer(int boardSize,int x,int y) {
         super(x,y);

		this.boardSize = boardSize;
	}


	public void init(int order1, long t, Random rnd) {
		order = order1;
		timeRemaining = t;
		random = rnd;

		System.out.println("Reversi Console szte.mi.Player " + (order + 1) + " initialized.");
		System.out.println("Remaining time: " + timeRemaining + " ms");
	}

	public Move nextMove(Move prevMove, long tOpponent, long t) {
		int length = 0;
		boolean correctInput;
		do {
			correctInput = true;
			try {
				System.out.print("> ");
				System.gc();
				length = System.in.read(buffer);
				while (length > 0
						&& Character.isWhitespace((char) buffer[length - 1]))
					length--;
				if (length >= 2 && Character.isAlphabetic(buffer[0])) {
					y = buffer[0] - 'a';
					x = Integer.parseInt(new String(buffer, 1, length - 1)) - 1;

				} else {
					System.out.println("Invalid format \""
							+ new String(buffer, 0, length)
							+ "\". Example entry: a1");
				}
			} catch (IOException e) {
				System.out.println("ERR: Reading from system in.");
				System.exit(-1);
			} catch (NumberFormatException e) {
				System.out.println("Invalid format \""
						+ new String(buffer, 0, length)
						+ "\". Example entry: a1");
				correctInput = false;
			}
		} while (!correctInput);
		return new Move(1,2);
	}

	public int getMoveRow() {
		return x;
	}


	public int getMoveCol() {
		return y;
	}



	public void enemyPlayMove(int moveR, int moveC) {
	}

}
