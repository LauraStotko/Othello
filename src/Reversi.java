import szte.mi.Move;
import szte.mi.Player;
import szte.mi.SuperPlayer;

import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class Reversi implements Runnable {
	final static byte PLAYER_ONE = 1;
	final static byte PLAYER_TWO = -1;
	final static byte EMPTY = 0;

	// time in milliseconds
	static final long TIME_LIMIT = 1000 * 120;
	static final long TURN_TIME_LIMIT = 5 * 1000;
	static final int DEFAULT_BOARD_SIZE = 8;
	static final boolean DEFAULT_AI_FIRST = false;

	static private byte[][] board = new byte[0][];
	static private ReversiAI playerOne = null;
	static private ReversiAI playerTwo = null;
	private byte currPlayer;
	private ArrayList<ReversiSpectator> spectators;
	Random random = new Random();
	int order = 0;
	private volatile boolean quitGame = false;
	long tOpponent;
	long t ;

	public static void main(String[] args) {
		new Reversi(board.length,playerOne,playerTwo).startGame();
	}

	public Reversi(int boardSize, Player p1, Player p2) {
		board = new byte[boardSize][boardSize];
		playerOne = (ReversiAI) p1;
		playerTwo = (ReversiAI) p2;
		currPlayer = PLAYER_ONE;
		spectators = new ArrayList<ReversiSpectator>();
		quitGame = false;
	}


	// returns if the move was valid
	private boolean applyMove(int moveR, int moveC, byte player) {
		int boardSize = board.length;
		if (moveR < 0 || moveC < 0 || moveR >= boardSize || moveC >= boardSize
				|| board[moveR][moveC] != EMPTY)
			return false;
		board[moveR][moveC] = player;
		boolean shifted = false;
		for (int rShift = -1; rShift <= 1; rShift++) {
			for (int cShift = -1; cShift <= 1; cShift++) {
				if (rShift != 0 || cShift != 0) {
					int amount = 1;
					while (moveR + rShift * amount >= 0
							&& moveR + rShift * amount < boardSize
							&& moveC + cShift * amount < boardSize
							&& moveC + cShift * amount >= 0
							&& board[moveR + rShift * amount][moveC + cShift
									* amount] == -player) {
						amount++;
					}
					if (amount > 1
							&& moveR + rShift * amount >= 0
							&& moveR + rShift * amount < boardSize
							&& moveC + cShift * amount < boardSize
							&& moveC + cShift * amount >= 0
							&& board[moveR + rShift * amount][moveC + cShift
									* amount] == player) {
						shifted = true;
						for (amount--; amount > 0; amount--)
							board[moveR + rShift * amount][moveC + cShift
									* amount] = player;

					}
				}
			}
		}
		if (!shifted)
			board[moveR][moveC] = EMPTY;
		return shifted;
	}

	private boolean canMove(int player) {
		for (int i = 0; i < board.length; i++)
			for (int j = 0; j < board.length; j++)
				if (board[i][j] == EMPTY && isValid(i, j, player))
					return true;
		return false;
	}

	// returns if this is a valid move
	private boolean isValid(int moveR, int moveC, int player) {
		int boardSize = board.length;
		if (moveR < 0 || moveC < 0 || moveR >= boardSize || moveC >= boardSize
				|| board[moveR][moveC] != EMPTY)
			return false;
		for (int rShift = -1; rShift <= 1; rShift++) {
			for (int cShift = -1; cShift <= 1; cShift++) {
				if (rShift != 0 || cShift != 0) {
					int amount = 1;
					while (moveR + rShift * amount >= 0
							&& moveR + rShift * amount < boardSize
							&& moveC + cShift * amount < boardSize
							&& moveC + cShift * amount >= 0
							&& board[moveR + rShift * amount][moveC + cShift
									* amount] == -player)
						amount++;
					if (amount > 1
							&& moveR + rShift * amount >= 0
							&& moveR + rShift * amount < boardSize
							&& moveC + cShift * amount < boardSize
							&& moveC + cShift * amount >= 0
							&& board[moveR + rShift * amount][moveC + cShift
									* amount] == player)
						return true;
				}
			}
		}
		return false;
	}

	private void printBoard(int lastMoveR, int lastMoveC, byte currPlayer) {
		int boardSize = board.length;
		int score1 = 0;
		int score2 = 0;
		if (boardSize > 8) {
			System.out.print("   ");
			for (int i = 0; i < boardSize; i++) {
				System.out.print((char) (i + 'a'));
			}
			System.out.println();
			System.out.print("   ");
			for (int i = 0; i < boardSize; i++) {
				System.out.print("-");
			}
			System.out.println();
			for (int i = 0; i < boardSize; i++) {
				System.out.format("%2d", i + 1);
				System.out.print("|");
				for (int j = 0; j < boardSize; j++) {
					if (board[i][j] == Reversi.PLAYER_ONE) {
						score1++;
						System.out.print("o");
					} else if (board[i][j] == Reversi.PLAYER_TWO) {
						score2++;
						System.out.print("*");
					} else
						System.out.print(" ");
				}
				System.out.println();
			}
		} else {
			System.out.print("  ");
			for (int i = 0; i < boardSize; i++)
				System.out.print("  " + (char) (i + 'a') + " ");
			System.out.print("\n  +");
			for (int i = 0; i < boardSize; i++)
				System.out.print("---+");
			System.out.println();
			for (int i = 0; i < boardSize; i++) {
				System.out.print((i + 1) + " |");
				for (int j = 0; j < boardSize; j++) {
					if ((board[i][j] == Reversi.PLAYER_ONE)) {
						score1++;
						if (lastMoveR == i && lastMoveC == j)
							System.out.print("-D-|");
						else
							System.out.print(" D |");
					} else if (board[i][j] == Reversi.PLAYER_TWO) {
						score2++;
						if (lastMoveR == i && lastMoveC == j)
							System.out.print("-L-|");
						else
							System.out.print(" L |");
					} else if (isValid(i, j, currPlayer))
						System.out.print(" . |");
					else
						System.out.print("   |");
				}
				System.out.print("\n  +");
				for (int j = 0; j < boardSize; j++)
					System.out.print("---+");
				System.out.println();
			}
		}
		System.out.println("Score: Light " + score2 + " - Dark " + score1);
	}

	public static String moveToString(int moveR, int moveC) {
		return (char) (moveC + 'a') + ("" + (moveR + 1));
	}


	@Override
	public void run() {
		System.out.println("1 for human Vs AI \n 2 for human Vs Human 3 is invalid");
		Scanner scan = new Scanner(System.in);
		int choice = scan.nextInt();
		if (choice == 1) {
			humanVsAI();
		} else if (choice == 2) {
			humanVShuman();
		} else {
			System.exit(0);
		}
	}
	// plays the game, players cannot be substituted mid-game

	public void startGame() {
		Player playerOne = new ReversiAI(DEFAULT_BOARD_SIZE, 1, TIME_LIMIT, TURN_TIME_LIMIT, true,1,2);
		Player playerTwo = new ReversiAI(DEFAULT_BOARD_SIZE, 1, TIME_LIMIT, TURN_TIME_LIMIT, false,1,2);
		Reversi game = new Reversi(DEFAULT_BOARD_SIZE, playerOne, playerTwo);
		new Thread(game).start();
	}
	public void humanVsAI() {
		SuperPlayer[] players = new SuperPlayer[]{playerTwo, null, new ReversiConsolePlayer(board.length,1,2)}; // 0 is 2p and 2 is 1p
		currPlayer = PLAYER_ONE;
		int boardSize = board.length;

		// clear the board
		for (int i = 0; i < boardSize; i++)
			for (int j = 0; j < boardSize; j++)
				board[i][j] = EMPTY;

		board[(boardSize / 2) - 1][boardSize / 2 - 1] = PLAYER_TWO;
		board[(boardSize / 2) - 1][boardSize / 2] = PLAYER_ONE;
		board[(boardSize / 2)][boardSize / 2 - 1] = PLAYER_ONE;
		board[boardSize / 2][boardSize / 2] = PLAYER_TWO;

		for (ReversiSpectator spec : spectators)
			spec.gameUpdated(-1, -1);

		printBoard(-1, -1, currPlayer);
		do {
			System.out.println((currPlayer == PLAYER_ONE ? "Dark" : "Light") + " player's turn.");

			do {
				if (players[currPlayer + 1] instanceof ReversiConsolePlayer) {
					// Human player's move using ReversiConsolePlayer
					players[currPlayer + 1].nextMove(new Move(1,2),TIME_LIMIT,TURN_TIME_LIMIT);
				} else {
					// AI player's move
					players[currPlayer + 1].nextMove(new Move(1,2),TIME_LIMIT,TURN_TIME_LIMIT);
					if (quitGame)
						return;
				}

				if (applyMove(  (players[currPlayer + 1].getMoveRow()), players[currPlayer + 1].getMoveCol(), currPlayer))
					break;
				else
					System.out.println("Invalid move! Try again.");

			} while (true);

			byte nextPlayer;
			// figure out the next player
			if (canMove(-currPlayer))
				nextPlayer = (byte) -currPlayer;
			else if (canMove(currPlayer)) {
				nextPlayer = currPlayer;
				if (-currPlayer == PLAYER_ONE)
					System.out.println("No valid moves for dark player. Skipping turn!");
				else
					System.out.println("No valid moves for light player. Skipping turn!");

			} else {
				nextPlayer = EMPTY;
			}

			int moveR = players[currPlayer + 1].getMoveRow(), moveC = players[currPlayer + 1].getMoveCol();
			printBoard(moveR, moveC, nextPlayer);
			System.out.println("szte.mi.Move played: " + Reversi.moveToString(moveR, moveC));
			// allow the enemy to update
			players[-currPlayer + 1].enemyPlayMove(moveR, moveC);
			players[-currPlayer + 1].init(order,  TIME_LIMIT,random);
			currPlayer = nextPlayer;
			System.out.flush();
			for (ReversiSpectator spec : spectators)
				spec.gameUpdated(moveR, moveC);

		} while (currPlayer != EMPTY && !quitGame);

		System.out.println("Game Over!");
	}




	public void humanVShuman() {
		ReversiConsolePlayer player1 = new ReversiConsolePlayer(board.length, 1, 2);
		ReversiConsolePlayer player2 = new ReversiConsolePlayer(board.length, 1, 2);

		currPlayer = PLAYER_ONE;
		int boardSize = board.length;

		// clear the board
		for (int i = 0; i < boardSize; i++)
			for (int j = 0; j < boardSize; j++)
				board[i][j] = EMPTY;

		board[(boardSize / 2) - 1][boardSize / 2 - 1] = PLAYER_TWO;
		board[(boardSize / 2) - 1][boardSize / 2] = PLAYER_ONE;
		board[(boardSize / 2)][boardSize / 2 - 1] = PLAYER_ONE;
		board[boardSize / 2][boardSize / 2] = PLAYER_TWO;

		for (ReversiSpectator spec : spectators)
			spec.gameUpdated(-1, -1);

		printBoard(-1, -1, currPlayer);

		do {
			System.out.println((currPlayer == PLAYER_ONE ? "Dark" : "Light") + " player's turn.");

			// Console input for human player
			System.out.println("Enter your move (e.g., 'A3'): ");
			Scanner scanner = new Scanner(System.in);
			String userInput = scanner.nextLine().toUpperCase();

			if (userInput.length() == 2 && Character.isLetter(userInput.charAt(0))
					&& Character.isDigit(userInput.charAt(1))) {
				int row = userInput.charAt(1) - '1';
				int col = userInput.charAt(0) - 'A';

				// Check if the move is valid
				if (applyMove(row, col, currPlayer)) {
					byte nextPlayer;
					// figure out the next player
					if (canMove(-currPlayer))
						nextPlayer = (byte) -currPlayer;
					else if (canMove(currPlayer)) {
						nextPlayer = currPlayer;
						if (-currPlayer == PLAYER_ONE)
							System.out.println("No valid moves for dark player. Skipping turn!");
						else
							System.out.println("No valid moves for light player. Skipping turn!");
					} else {
						nextPlayer = EMPTY;
					}

					int moveR = row, moveC = col;
					printBoard(moveR, moveC, nextPlayer);
					System.out.println("szte.mi.Move played: " + Reversi.moveToString(moveR, moveC));

					// allow the enemy to update
					if (currPlayer == Reversi.PLAYER_ONE) {
						player2.enemyPlayMove(moveR, moveC);
						player2.init(order, TIME_LIMIT, random);
					} else {
						player1.enemyPlayMove(moveR, moveC);
						player1.init(order, TIME_LIMIT, random);
					}

					currPlayer = nextPlayer;

					for (ReversiSpectator spec : spectators)
						spec.gameUpdated(moveR, moveC);

					continue;
				}
			}
			System.out.println("Invalid input. Please enter a valid move (e.g., 'A3'). Try again.");

		} while (currPlayer != EMPTY);

		System.out.println("Game Over!");
	}

}
